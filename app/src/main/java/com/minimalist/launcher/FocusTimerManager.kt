package com.minimalist.launcher

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages persistent state for Stopwatch and Timer in FocusActivity.
 * Uses real timestamps (System.currentTimeMillis()) so time keeps ticking
 * even when the activity is destroyed, backed out of, or the screen is off.
 */
object FocusTimerManager {

    private const val PREFS_NAME = "focus_timer_state"

    // --- STOPWATCH STATE ---
    var swRunning = false
        private set
    var swPaused = false
        private set
    private var swStartTimeMs = 0L
    private var swAccumulatedMs = 0L
    val swLaps = mutableListOf<String>()

    // --- TIMER STATE ---
    var timerRunning = false
        private set
    var timerPaused = false
        private set
    private var timerEndTimeMs = 0L
    private var timerRemainingSecondsOnPause = 0
    var timerTotalSeconds = 0
        private set

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Restore Stopwatch
        swRunning = prefs.getBoolean("sw_running", false)
        swPaused = prefs.getBoolean("sw_paused", false)
        swStartTimeMs = prefs.getLong("sw_start_time", 0L)
        swAccumulatedMs = prefs.getLong("sw_accumulated", 0L)
        val lapsString = prefs.getString("sw_laps", "") ?: ""
        swLaps.clear()
        if (lapsString.isNotEmpty()) {
            swLaps.addAll(lapsString.split(","))
        }

        // Restore Timer
        timerRunning = prefs.getBoolean("timer_running", false)
        timerPaused = prefs.getBoolean("timer_paused", false)
        timerEndTimeMs = prefs.getLong("timer_end_time", 0L)
        timerRemainingSecondsOnPause = prefs.getInt("timer_remaining_on_pause", 0)
        timerTotalSeconds = prefs.getInt("timer_total_seconds", 0)
    }

    private fun save(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("sw_running", swRunning)
            .putBoolean("sw_paused", swPaused)
            .putLong("sw_start_time", swStartTimeMs)
            .putLong("sw_accumulated", swAccumulatedMs)
            .putString("sw_laps", swLaps.joinToString(","))
            .putBoolean("timer_running", timerRunning)
            .putBoolean("timer_paused", timerPaused)
            .putLong("timer_end_time", timerEndTimeMs)
            .putInt("timer_remaining_on_pause", timerRemainingSecondsOnPause)
            .putInt("timer_total_seconds", timerTotalSeconds)
            .apply()
    }

    // ==================== STOPWATCH METHODS ====================

    fun startStopwatch(context: Context) {
        swRunning = true
        swPaused = false
        swStartTimeMs = System.currentTimeMillis()
        swAccumulatedMs = 0L
        swLaps.clear()
        save(context)
    }

    fun pauseStopwatch(context: Context) {
        if (!swRunning || swPaused) return
        swAccumulatedMs += (System.currentTimeMillis() - swStartTimeMs)
        swPaused = true
        save(context)
    }

    fun resumeStopwatch(context: Context) {
        if (!swRunning || !swPaused) return
        swStartTimeMs = System.currentTimeMillis()
        swPaused = false
        save(context)
    }

    fun addStopwatchLap(context: Context, lap: String) {
        swLaps.add(lap)
        save(context)
    }

    fun stopStopwatch(context: Context): Int {
        val elapsed = getStopwatchElapsedSeconds()
        swRunning = false
        swPaused = false
        swStartTimeMs = 0L
        swAccumulatedMs = 0L
        swLaps.clear()
        save(context)
        return elapsed
    }

    fun getStopwatchElapsedSeconds(): Int {
        if (!swRunning) return 0
        val totalMs = if (swPaused) {
            swAccumulatedMs
        } else {
            swAccumulatedMs + (System.currentTimeMillis() - swStartTimeMs)
        }
        return (totalMs / 1000L).coerceAtLeast(0L).toInt()
    }

    // ==================== TIMER METHODS ====================

    fun startTimer(context: Context, totalSeconds: Int) {
        timerTotalSeconds = totalSeconds
        timerRunning = true
        timerPaused = false
        timerEndTimeMs = System.currentTimeMillis() + (totalSeconds * 1000L)
        timerRemainingSecondsOnPause = totalSeconds
        save(context)
    }

    fun pauseTimer(context: Context) {
        if (!timerRunning || timerPaused) return
        timerRemainingSecondsOnPause = getTimerRemainingSeconds()
        timerPaused = true
        save(context)
    }

    fun resumeTimer(context: Context) {
        if (!timerRunning || !timerPaused) return
        timerEndTimeMs = System.currentTimeMillis() + (timerRemainingSecondsOnPause * 1000L)
        timerPaused = false
        save(context)
    }

    fun resetTimer(context: Context) {
        timerRunning = false
        timerPaused = false
        timerEndTimeMs = 0L
        timerRemainingSecondsOnPause = 0
        timerTotalSeconds = 0
        save(context)
    }

    fun getTimerRemainingSeconds(): Int {
        if (!timerRunning) return 0
        if (timerPaused) return timerRemainingSecondsOnPause
        val diffMs = timerEndTimeMs - System.currentTimeMillis()
        return (diffMs / 1000L).coerceAtLeast(0L).toInt()
    }
}
