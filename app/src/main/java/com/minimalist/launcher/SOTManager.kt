package com.minimalist.launcher

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
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
     * Attribution matches Digital Wellbeing's convention: only the *top* activity's package
     * earns time, the screen must be interactive, and the launcher / systemui / packages with no
     * launcher entry are excluded. Total screen time is the sum of this map, so the headline
     * number and the list can never disagree.
     */
    fun getTodayAppUsage(context: Context): Map<String, Long> {
        val start = startOfToday()
        val end = System.currentTimeMillis()
        val manager = context.getSystemService(UsageStatsManager::class.java)
            ?: return emptyMap()

        // Look one day back so an app that was already in the foreground at midnight is seeded.
        val cursor = manager.queryEvents(start - DAY_MS, end)
        val events = mutableListOf<TimedEvent>()
        val event = UsageEvents.Event()
        while (cursor.hasNextEvent()) {
            cursor.getNextEvent(event)
            if (event.eventType in TRACKED_TYPES) {
                events += TimedEvent(event.timeStamp, event.eventType, event.packageName.orEmpty())
            }
        }

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
        var top: String? = null
        var interactive = true
        var cursor = start

        fun credit(until: Long) {
            val from = maxOf(cursor, start)
            val to = minOf(until, end)
            if (to <= from || !interactive) return
            val pkg = top ?: return
            if (!counted(pkg)) return
            totals[pkg] = (totals[pkg] ?: 0L) + (to - from)
        }

        events.asSequence()
            .filter { it.timestamp <= end }
            .sortedBy(TimedEvent::timestamp)
            .forEach { item ->
                if (item.timestamp > start) {
                    credit(item.timestamp)
                    cursor = minOf(item.timestamp, end)
                }
                when (item.type) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        top = item.packageName
                        interactive = true
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED -> {
                        // Only the app that is actually on top can end the interval; a late PAUSED
                        // from the previous app must not cancel the app that just resumed.
                        if (item.packageName == top) top = null
                    }
                    UsageEvents.Event.SCREEN_INTERACTIVE -> interactive = true
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE -> interactive = false
                    UsageEvents.Event.DEVICE_SHUTDOWN,
                    UsageEvents.Event.DEVICE_STARTUP -> {
                        interactive = false
                        top = null
                    }
                }
            }

        credit(end)
        return totals
    }

    /**
     * ponytail: "has a launcher entry" is a cheap proxy for "app the user can actually open",
     * which is how Digital Wellbeing decides what shows up in its chart. Ceiling: an app that
     * hides its launcher icon (rare) drops out of the list. Upgrade path would be reading
     * ApplicationInfo.category / FLAG_SYSTEM per package if that ever matters.
     */
    private fun launchablePackages(context: Context): Set<String> = runCatching {
        val pm = context.packageManager
        pm.getInstalledApplications(0)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .map { it.packageName }
            .toSet()
    }.getOrElse {
        Log.w(TAG, "could not list launchable packages: ${it.message}")
        emptySet()
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

    fun labelFor(pm: PackageManager, packageName: String): String? = runCatching {
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrNull()

    internal data class TimedEvent(
        val timestamp: Long,
        val type: Int,
        val packageName: String
    )

    private const val DAY_MS = 24 * 60 * 60 * 1000L

    private val TRACKED_TYPES = setOf(
        UsageEvents.Event.ACTIVITY_RESUMED,
        UsageEvents.Event.ACTIVITY_PAUSED,
        UsageEvents.Event.ACTIVITY_STOPPED,
        UsageEvents.Event.SCREEN_INTERACTIVE,
        UsageEvents.Event.SCREEN_NON_INTERACTIVE,
        UsageEvents.Event.DEVICE_SHUTDOWN,
        UsageEvents.Event.DEVICE_STARTUP
    )

    private val EXCLUDED_PACKAGES = setOf("android", "com.android.systemui")
}
