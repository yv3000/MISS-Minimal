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
        val midnight = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        // Bootstrap from 2 hours before midnight to catch sessions already in progress
        val bootstrapStart = midnight - (2 * 60 * 60 * 1000)
        val events = usageStatsManager.queryEvents(bootstrapStart, endTime)
        val allEvents = mutableListOf<SimpleEvent>()
        
        val ignorePackages = setOf("android", "com.android.systemui")

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
        var segmentStartTime = midnight
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
                // Segment started. If it started before midnight, clamp to midnight.
                segmentStartTime = Math.max(e.timestamp, midnight)
                isAnyAppActive = true
            } else if (!shouldBeCounting && isAnyAppActive) {
                // Segment ended. If it ended before midnight, it contributes 0.
                val segmentEndTime = e.timestamp
                if (segmentEndTime > midnight) {
                    totalSot += (segmentEndTime - Math.max(segmentStartTime, midnight))
                }
                isAnyAppActive = false
            }
        }
        
        if (isAnyAppActive) {
            totalSot += (endTime - Math.max(segmentStartTime, midnight))
        }
        
        val maxPossible = endTime - midnight
        return totalSot.coerceIn(0L, maxPossible)
    }

    private data class SimpleEvent(val packageName: String, val type: Int, val timestamp: Long)
}
