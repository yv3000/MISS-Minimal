/*
 * i expect nothing from you...
 *
 * the dead man
 * yv3000
 * the god
 */
package com.minimalist.launcher

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import java.util.Calendar
import kotlin.math.max
import kotlin.math.min

object SOTManager {

    fun getScreenOnTimeToday(context: Context): Long =
        getTodayAppUsageFromEvents(context).values.sum()

    fun getTodayAppUsageFromEvents(context: Context): Map<String, Long> {
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val midnightCalendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val midnight = midnightCalendar.timeInMillis
        val endTime = System.currentTimeMillis()
        val bootstrapStart = (midnightCalendar.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, -1)
        }.timeInMillis

        val events = manager.queryEvents(bootstrapStart, endTime)
        val allEvents = mutableListOf<SimpleEvent>()
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.SCREEN_INTERACTIVE ||
                event.eventType == UsageEvents.Event.SCREEN_NON_INTERACTIVE ||
                event.eventType == UsageEvents.Event.KEYGUARD_SHOWN ||
                event.eventType == UsageEvents.Event.KEYGUARD_HIDDEN ||
                event.eventType == resumedEvent || event.eventType == pausedEvent
            ) {
                allEvents += SimpleEvent(event.packageName.orEmpty(), event.eventType, event.timeStamp)
            }
        }
        allEvents.sortBy { it.timestamp }

        val appUsage = mutableMapOf<String, Long>()
        var interactive: Boolean? = null
        var keyguardShown: Boolean? = null
        var foregroundApp: String? = null
        var foregroundKnown = false
        var countingSince: Long? = null

        fun closeSegment(at: Long) {
            val app = foregroundApp
            val start = countingSince
            if (app != null && start != null) {
                val duration = min(at, endTime) - max(start, midnight)
                if (duration > 0) appUsage[app] = appUsage.getOrDefault(app, 0L) + duration
            }
            countingSince = null
        }

        fun resumeCounting(at: Long) {
            if (interactive == true && keyguardShown == false && foregroundApp != null) {
                countingSince = max(at, midnight)
            }
        }

        var stateKnownAtMidnight: Boolean? = null
        for (item in allEvents) {
            if (item.timestamp >= midnight && stateKnownAtMidnight == null) {
                stateKnownAtMidnight = interactive != null && keyguardShown != null && foregroundKnown
            }
            closeSegment(item.timestamp)
            when (item.type) {
                UsageEvents.Event.SCREEN_INTERACTIVE -> interactive = true
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> interactive = false
                UsageEvents.Event.KEYGUARD_SHOWN -> keyguardShown = true
                UsageEvents.Event.KEYGUARD_HIDDEN -> keyguardShown = false
                resumedEvent -> {
                    foregroundApp = item.packageName.takeIf(String::isNotEmpty)
                    foregroundKnown = true
                }
                pausedEvent -> if (foregroundApp == item.packageName) {
                    foregroundApp = null
                    foregroundKnown = true
                }
            }
            resumeCounting(item.timestamp)
        }
        if (stateKnownAtMidnight == null) {
            stateKnownAtMidnight = interactive != null && keyguardShown != null && foregroundKnown
        }
        closeSegment(endTime)

        return if (stateKnownAtMidnight == true) appUsage else dailyStatsFallback(manager, midnight, endTime)
    }

    private fun dailyStatsFallback(
        manager: UsageStatsManager,
        midnight: Long,
        endTime: Long
    ): Map<String, Long> {
        val totals = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, midnight, endTime)
            .groupBy { it.packageName }
            .mapValues { (_, stats) -> stats.sumOf { it.totalTimeInForeground }.coerceAtLeast(0L) }
            .filterValues { it > 0 }
        val total = totals.values.sum()
        val limit = (endTime - midnight).coerceAtLeast(0L)
        if (total <= limit) return totals
        return totals.mapValues { (_, duration) -> (duration.toDouble() * limit / total).toLong() }
    }

    @Suppress("DEPRECATION")
    private val resumedEvent: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            UsageEvents.Event.ACTIVITY_RESUMED
        } else {
            UsageEvents.Event.MOVE_TO_FOREGROUND
        }

    @Suppress("DEPRECATION")
    private val pausedEvent: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            UsageEvents.Event.ACTIVITY_PAUSED
        } else {
            UsageEvents.Event.MOVE_TO_BACKGROUND
        }

    private data class SimpleEvent(val packageName: String, val type: Int, val timestamp: Long)
}
