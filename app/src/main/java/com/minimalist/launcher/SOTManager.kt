package com.minimalist.launcher

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
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
        val bootstrapStart = start - 24 * 60 * 60 * 1000L
        val usageEvents = manager.queryEvents(bootstrapStart, end)
        val events = mutableListOf<TimedEvent>()
        val event = UsageEvents.Event()
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                event.eventType == UsageEvents.Event.ACTIVITY_PAUSED ||
                event.eventType == UsageEvents.Event.ACTIVITY_STOPPED ||
                event.eventType == UsageEvents.Event.SCREEN_INTERACTIVE ||
                event.eventType == UsageEvents.Event.SCREEN_NON_INTERACTIVE ||
                event.eventType == UsageEvents.Event.DEVICE_SHUTDOWN ||
                event.eventType == UsageEvents.Event.DEVICE_STARTUP
            ) {
                events += TimedEvent(
                    event.timeStamp,
                    event.eventType,
                    event.packageName.orEmpty(),
                    event.className.orEmpty()
                )
            }
        }
        return calculateAppScreenTime(start, end, events, context.packageName)
    }

    internal fun calculateAppScreenTime(
        start: Long,
        end: Long,
        events: List<TimedEvent>,
        launcherPackage: String
    ): Long {
        val resumed = mutableMapOf<String, Int>()
        var interactive = true
        var previous = start
        var total = 0L

        events.asSequence()
            .filter { it.timestamp <= end }
            .sortedBy(TimedEvent::timestamp)
            .forEach { item ->
                if (item.timestamp >= start && interactive && resumed.any { (key, count) ->
                        count > 0 && isCountedPackage(key.substringBefore('/'), launcherPackage)
                    }) {
                    total += (item.timestamp - previous).coerceAtLeast(0L)
                }
                val key = "${item.packageName}/${item.className}"
                when (item.type) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> resumed[key] = resumed.getOrDefault(key, 0) + 1
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED -> {
                        val count = resumed.getOrDefault(key, 0) - 1
                        if (count > 0) resumed[key] = count else resumed.remove(key)
                    }
                    UsageEvents.Event.SCREEN_INTERACTIVE -> interactive = true
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE -> interactive = false
                    UsageEvents.Event.DEVICE_SHUTDOWN,
                    UsageEvents.Event.DEVICE_STARTUP -> {
                        interactive = false
                        resumed.clear()
                    }
                }
                if (item.timestamp >= start) previous = item.timestamp else previous = start
            }

        if (interactive && resumed.any { (key, count) ->
                count > 0 && isCountedPackage(key.substringBefore('/'), launcherPackage)
            }) {
            total += (end - previous).coerceAtLeast(0L)
        }
        return total.coerceIn(0L, end - start)
    }

    private fun isCountedPackage(packageName: String, launcherPackage: String) =
        packageName.isNotBlank() && packageName != launcherPackage && packageName !in EXCLUDED_PACKAGES

    internal data class TimedEvent(
        val timestamp: Long,
        val type: Int,
        val packageName: String,
        val className: String
    )

    private val EXCLUDED_PACKAGES = setOf("android", "com.android.systemui")
}
