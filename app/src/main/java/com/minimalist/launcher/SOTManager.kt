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
import java.util.Calendar

object SOTManager {

    fun getScreenOnTimeToday(context: Context): Long {
        val usageMap = getTodayAppUsageFromEvents(context)
        return usageMap.values.sum()
    }

    fun getTodayAppUsageFromEvents(context: Context): Map<String, Long> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val midnight = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val bootstrapStart = midnight - (2 * 60 * 60 * 1000)
        val events = usageStatsManager.queryEvents(bootstrapStart, endTime)
        
        val appUsage = mutableMapOf<String, Long>()
        var isScreenOn = false
        var lastScreenOnTime = midnight
        var currentForegroundApp: String? = null

        val event = UsageEvents.Event()
        val allEvents = mutableListOf<SimpleEvent>()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val type = event.eventType
            if (type == 1 || type == 2 || type == 15 || type == 16) {
                allEvents.add(SimpleEvent(event.packageName ?: "", type, event.timeStamp))
            }
        }
        allEvents.sortBy { it.timestamp }

        for (e in allEvents) {
            when (e.type) {
                15 -> {
                    isScreenOn = true
                    lastScreenOnTime = Math.max(e.timestamp, midnight)
                }
                16 -> {
                    if (isScreenOn && currentForegroundApp != null) {
                        val clampedStart = Math.max(lastScreenOnTime, midnight)
                        val duration = e.timestamp - clampedStart
                        if (duration > 0) {
                            appUsage[currentForegroundApp!!] = (appUsage[currentForegroundApp!!] ?: 0L) + duration
                        }
                    }
                    isScreenOn = false
                }
                1 -> {
                    if (isScreenOn && currentForegroundApp != null) {
                        val clampedStart = Math.max(lastScreenOnTime, midnight)
                        val duration = e.timestamp - clampedStart
                        if (duration > 0) {
                            appUsage[currentForegroundApp!!] = (appUsage[currentForegroundApp!!] ?: 0L) + duration
                        }
                    }
                    currentForegroundApp = e.packageName
                    lastScreenOnTime = Math.max(e.timestamp, midnight)
                }
                2 -> {
                    if (isScreenOn && currentForegroundApp == e.packageName) {
                        val clampedStart = Math.max(lastScreenOnTime, midnight)
                        val duration = e.timestamp - clampedStart
                        if (duration > 0) {
                            appUsage[currentForegroundApp!!] = (appUsage[currentForegroundApp!!] ?: 0L) + duration
                        }
                        currentForegroundApp = null
                    }
                }
            }
        }

        if (isScreenOn && currentForegroundApp != null) {
            val clampedStart = Math.max(lastScreenOnTime, midnight)
            val duration = endTime - clampedStart
            if (duration > 0) {
                appUsage[currentForegroundApp!!] = (appUsage[currentForegroundApp!!] ?: 0L) + duration
            }
        }

        return appUsage
    }

    private data class SimpleEvent(val packageName: String, val type: Int, val timestamp: Long)
}
