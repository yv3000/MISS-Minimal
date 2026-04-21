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
        var totalSot = 0L
        val lastEventTime = mutableMapOf<String, Long>()

        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val packageName = event.packageName
            val timestamp = event.timeStamp

            // Filter out system UI issues or launcher itself if needed, 
            // but standard SOT includes all apps basically except launcher maybe?
            // Actually, SOT usually means screen on time across all apps.
            // When ACTIVITY_RESUMED, app came to foreground.
            // When ACTIVITY_PAUSED or ACTIVITY_STOPPED, app went to background.
            
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    lastEventTime[packageName] = timestamp
                }
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val start = lastEventTime.remove(packageName)
                    if (start != null) {
                        totalSot += (timestamp - start)
                    }
                }
            }
        }
        
        // Add currently foregrounded apps
        val now = System.currentTimeMillis()
        for ((_, start) in lastEventTime) {
            totalSot += (now - start)
        }

        return totalSot
    }
}
