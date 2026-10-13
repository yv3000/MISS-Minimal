package com.minimalist.launcher

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.UserManager
import android.util.Log
import java.util.Calendar

object SOTManager {

    private const val TAG = "SOTManager"

    fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /**
     * Per-package foreground time for today, in ms, descending by duration.
     *
     * Estimates time for the latest resumed activity while interactive and not keyguard-locked.
     * The existing launcher/system/launchable-package filter is applied to that time.
     */
    fun getTodayAppUsage(context: Context): Map<String, Long> {
        check(UsageAccess.isGranted(context)) { "Usage access is unavailable" }
        check(context.getSystemService(UserManager::class.java)?.isUserUnlocked == true) {
            "Usage history is unavailable before user unlock"
        }
        val start = startOfToday()
        val end = System.currentTimeMillis()
        val manager = context.getSystemService(UsageStatsManager::class.java)
            ?: error("Usage stats service is unavailable")

        // Look one day back so an app that was already in the foreground at midnight is seeded.
        val cursor = manager.queryEvents(start - DAY_MS, end)
            ?: error("Usage history is unavailable")
        // Android can also hide service failures behind an empty event cursor.
        check(cursor.hasNextEvent()) { "No usage history is available" }
        val events = mutableListOf<TimedEvent>()
        val event = UsageEvents.Event()
        while (cursor.hasNextEvent()) {
            check(cursor.getNextEvent(event)) { "Could not read usage event" }
            if (event.eventType in TRACKED_TYPES) {
                events += TimedEvent(
                    event.timeStamp, event.eventType, event.packageName.orEmpty(), event.className
                )
            }
        }
        check(UsageAccess.isGranted(context)) { "Usage access is unavailable" }

        val launchable = launchablePackages(context)
        val usage = computeAppUsage(start, end, events) { pkg ->
            isCountedPackage(pkg, context.packageName, launchable)
        }

        Log.d(TAG, "today total=${usage.values.sum() / 60_000}m from ${usage.size} apps")
        return usage.entries
            .sortedByDescending { it.value }
            .associate { it.key to it.value }
    }

    /** Total screen time today = sum of the per-app breakdown. */
    fun getScreenOnTimeToday(context: Context): Long = getTodayAppUsage(context).values.sum()

    /**
     * Pure state machine, kept internal so it can be unit tested on the JVM without a device.
     * [counted] decides whether a package contributes (launcher/system filtering lives outside).
     */
    internal fun computeAppUsage(
        start: Long,
        end: Long,
        events: List<TimedEvent>,
        counted: (String) -> Boolean
    ): Map<String, Long> {
        val totals = mutableMapOf<String, Long>()
        var top: TimedEvent? = null
        // API 26/27 have no screen events: unknown falls back to activity lifecycle events.
        var interactive: Boolean? = null
        var locked = false
        var cursor = start

        fun credit(until: Long) {
            val from = maxOf(cursor, start)
            val to = minOf(until, end)
            if (to <= from || interactive == false || locked) return
            val pkg = top?.packageName ?: return
            if (!counted(pkg)) return
            totals[pkg] = (totals[pkg] ?: 0L) + (to - from)
        }

        events.asSequence()
            .filter { it.timestamp <= end }
            .sortedBy(TimedEvent::timestamp)
            .forEach { item ->
                if (item.timestamp > start) {
                    // A startup without a shutdown leaves the previous interval's end unknown.
                    if (item.type != UsageEvents.Event.DEVICE_STARTUP) credit(item.timestamp)
                    cursor = minOf(item.timestamp, end)
                }
                when (item.type) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        top = item
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED -> {
                        // A late close from another activity in the same package is not ours.
                        if (item.packageName == top?.packageName && item.className == top?.className) {
                            top = null
                        }
                    }
                    UsageEvents.Event.SCREEN_INTERACTIVE -> interactive = true
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE -> interactive = false
                    UsageEvents.Event.KEYGUARD_SHOWN -> locked = true
                    UsageEvents.Event.KEYGUARD_HIDDEN -> locked = false
                    UsageEvents.Event.DEVICE_SHUTDOWN -> {
                        interactive = false
                        top = null
                    }
                    UsageEvents.Event.DEVICE_STARTUP -> {
                        interactive = null
                        locked = false
                        top = null
                    }
                }
            }

        credit(end)
        return totals
    }

    /**
     * ponytail: cached for [LAUNCHABLE_TTL_MS] because this is a PackageManager enumeration plus
     * one binder call per installed app — far too expensive to redo on every SOT refresh.
     * Ceiling: an app installed/removed in the last minute is misclassified for that minute.
     * Upgrade path: invalidate from a PACKAGE_ADDED/REMOVED receiver.
     */
    @Volatile private var launchableCache: Set<String> = emptySet()
    @Volatile private var launchableCachedAt = 0L
    private const val LAUNCHABLE_TTL_MS = 60_000L

    private fun launchablePackages(context: Context): Set<String> {
        val now = System.currentTimeMillis()
        val cached = launchableCache
        if (cached.isNotEmpty() && now - launchableCachedAt < LAUNCHABLE_TTL_MS) return cached
        val fresh = runCatching {
            val pm = context.packageManager
            pm.getInstalledApplications(0)
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                .map { it.packageName }
                .toSet()
        }.getOrElse {
            Log.w(TAG, "could not list launchable packages: ${it.message}")
            emptySet()
        }
        if (fresh.isNotEmpty()) {
            launchableCache = fresh
            launchableCachedAt = now
        }
        return fresh
    }

    internal fun isCountedPackage(
        packageName: String,
        launcherPackage: String,
        launchable: Set<String>
    ): Boolean {
        if (packageName.isBlank()) return false
        if (packageName == launcherPackage) return false
        if (packageName in EXCLUDED_PACKAGES) return false
        // Empty set = we failed to query the package manager; don't silently drop everything.
        return launchable.isEmpty() || packageName in launchable
    }

    fun format(ms: Long): String {
        val minutes = ms / 60_000
        return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
    }

    fun labelFor(pm: PackageManager, packageName: String): String = runCatching {
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: packageName

    internal data class TimedEvent(
        val timestamp: Long,
        val type: Int,
        val packageName: String,
        val className: String? = null
    )

    private const val DAY_MS = 24 * 60 * 60 * 1000L

    private val TRACKED_TYPES = setOf(
        UsageEvents.Event.ACTIVITY_RESUMED,
        UsageEvents.Event.ACTIVITY_PAUSED,
        UsageEvents.Event.ACTIVITY_STOPPED,
        UsageEvents.Event.SCREEN_INTERACTIVE,
        UsageEvents.Event.SCREEN_NON_INTERACTIVE,
        UsageEvents.Event.KEYGUARD_SHOWN,
        UsageEvents.Event.KEYGUARD_HIDDEN,
        UsageEvents.Event.DEVICE_SHUTDOWN,
        UsageEvents.Event.DEVICE_STARTUP
    )

    private val EXCLUDED_PACKAGES = setOf("android", "com.android.systemui")
}
