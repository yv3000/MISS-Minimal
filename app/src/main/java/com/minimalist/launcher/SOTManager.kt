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
        
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val type = event.eventType
            if (type == 1 || type == 2 || type == 23 || type == 15 || type == 16) {
                allEvents.add(SimpleEvent(event.packageName ?: "", type, event.timeStamp))
            }
        }
        
        allEvents.sortBy { it.timestamp }
        
        var totalSot = 0L
        var isAnyAppActive = false
        var isScreenOn = true 
        var segmentStartTime = 0L
        val activeApps = mutableSetOf<String>()
        
        // Filter out launcher if desired, but for raw SOT we usually keep it.
        // However, if launcher is "always active" in background it might mess up.
        // Let's filter out known system packages that stay resumed.
        val ignorePackages = setOf("android", "com.android.systemui")

        for (e in allEvents) {
            if (ignorePackages.contains(e.packageName)) continue

            when (e.type) {
                1 -> activeApps.add(e.packageName)
                2, 23 -> activeApps.remove(e.packageName)
                15 -> isScreenOn = true
                16 -> {
                    isScreenOn = false
                    activeApps.clear() // On screen off, consider all apps paused for SOT
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
        
        // Sanity check: SOT cannot exceed time since midnight
        val maxPossible = endTime - startTime
        return if (totalSot > maxPossible) maxPossible else totalSot
    }

    private data class SimpleEvent(val packageName: String, val type: Int, val timestamp: Long)
}
