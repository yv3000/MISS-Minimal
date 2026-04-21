package com.minimalist.launcher

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.Calendar

object SOTManager {

    fun getScreenOnTimeToday(context: Context): Long {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val events = usageStatsManager.queryEvents(startTime, endTime)
        val allEvents = mutableListOf<SimpleEvent>()
        
        val launcherPkg = context.packageName
        val ignorePackages = setOf("android", "com.android.systemui", launcherPkg)

        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val type = event.eventType
            // We only care about Resumed (1), Paused (2), Stopped (23), Screen On (15), Screen Off (16)
            if (type == 1 || type == 2 || type == 23 || type == 15 || type == 16) {
                allEvents.add(SimpleEvent(event.packageName ?: "", type, event.timeStamp))
            }
        }
        
        allEvents.sortBy { it.timestamp }
        
        var totalSot = 0L
        var isAnyAppActive = false
        var isScreenOn = false // Start as false to be safe, first event will correct it
        var segmentStartTime = 0L
        val activeApps = mutableSetOf<String>()
        
        for (e in allEvents) {
            when (e.type) {
                1 -> if (!ignorePackages.contains(e.packageName)) activeApps.add(e.packageName)
                2, 23 -> activeApps.remove(e.packageName)
                15 -> isScreenOn = true
                16 -> {
                    isScreenOn = false
                    activeApps.clear()
                }
            }
            
            val shouldBeCounting = isScreenOn && activeApps.isNotEmpty()
            
            if (shouldBeCounting && !isAnyAppActive) {
                segmentStartTime = e.timestamp
                isAnyAppActive = true
            } else if (!shouldBeCounting && isAnyAppActive) {
                totalSot += (e.timestamp - segmentStartTime)
                isAnyAppActive = false
            }
        }
        
        if (isAnyAppActive) {
            totalSot += (endTime - segmentStartTime)
        }
        
        // Final sanity check: cannot exceed time since midnight
        val maxPossible = endTime - startTime
        return totalSot.coerceIn(0L, maxPossible)
    }

    private data class SimpleEvent(val packageName: String, val type: Int, val timestamp: Long)
}
