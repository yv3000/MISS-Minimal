package com.minimalist.launcher

import android.app.KeyguardManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.PowerManager
import java.util.Calendar

object SOTManager {
    fun getScreenOnTimeToday(context: Context): Long {
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val end = System.currentTimeMillis()
        val manager = context.getSystemService(UsageStatsManager::class.java)
        val usageEvents = manager.queryEvents(start, end)
        val events = mutableListOf<TimedEvent>()
        val event = UsageEvents.Event()
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType in TRACKED_EVENTS) events += TimedEvent(event.timeStamp, event.eventType)
        }

        if (events.isEmpty()) return dailyStatsFallback(manager, start, end)
        val power = context.getSystemService(PowerManager::class.java)
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        return calculateUnlockedTime(start, end, events, power.isInteractive, keyguard.isKeyguardLocked)
    }

    internal fun calculateUnlockedTime(
        start: Long,
        end: Long,
        events: List<TimedEvent>,
        currentlyInteractive: Boolean,
        currentlyLocked: Boolean
    ): Long {
        val sorted = events.filter { it.timestamp in start..end }.sortedBy(TimedEvent::timestamp)
        val firstScreen = sorted.firstOrNull { it.type == UsageEvents.Event.SCREEN_INTERACTIVE || it.type == UsageEvents.Event.SCREEN_NON_INTERACTIVE }
        val firstKeyguard = sorted.firstOrNull { it.type == UsageEvents.Event.KEYGUARD_SHOWN || it.type == UsageEvents.Event.KEYGUARD_HIDDEN }
        var interactive: Boolean? = firstScreen?.type?.let { it == UsageEvents.Event.SCREEN_NON_INTERACTIVE }
            ?: currentlyInteractive
        var locked: Boolean? = firstKeyguard?.type?.let { it == UsageEvents.Event.KEYGUARD_HIDDEN }
            ?: currentlyLocked
        var previous = start
        var total = 0L

        for (item in sorted) {
            if (interactive == true && locked == false) total += (item.timestamp - previous).coerceAtLeast(0L)
            when (item.type) {
                UsageEvents.Event.SCREEN_INTERACTIVE -> interactive = true
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> interactive = false
                UsageEvents.Event.KEYGUARD_SHOWN -> locked = true
                UsageEvents.Event.KEYGUARD_HIDDEN -> locked = false
                UsageEvents.Event.DEVICE_SHUTDOWN, UsageEvents.Event.DEVICE_STARTUP -> {
                    interactive = null
                    locked = null
                }
            }
            previous = item.timestamp
        }
        if (interactive == true && locked == false) total += (end - previous).coerceAtLeast(0L)
        return total.coerceIn(0L, end - start)
    }

    private fun dailyStatsFallback(manager: UsageStatsManager, start: Long, end: Long): Long {
        val largestAppTotal = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
            .maxOfOrNull { it.totalTimeInForeground } ?: 0L
        return largestAppTotal.coerceIn(0L, end - start)
    }

    internal data class TimedEvent(val timestamp: Long, val type: Int)

    private val TRACKED_EVENTS = setOf(
        UsageEvents.Event.SCREEN_INTERACTIVE,
        UsageEvents.Event.SCREEN_NON_INTERACTIVE,
        UsageEvents.Event.KEYGUARD_SHOWN,
        UsageEvents.Event.KEYGUARD_HIDDEN,
        UsageEvents.Event.DEVICE_SHUTDOWN,
        UsageEvents.Event.DEVICE_STARTUP
    )
}
