package com.minimalist.launcher

import android.content.Context

object StrictModeManager {
  var serviceRef: StrictModeService? = null
  private var active = false
  private var endTimeMs = 0L

  fun start(context: Context, durationMs: Long) {
    active = true
    endTimeMs = System.currentTimeMillis() + durationMs
    context.getSharedPreferences("strict", 
      Context.MODE_PRIVATE).edit()
      .putBoolean("active", true)
      .putLong("end", endTimeMs)
      .apply()
    serviceRef?.startBlocking()
  }

  fun stop(context: Context) {
    active = false
    endTimeMs = 0L
    context.getSharedPreferences("strict",
      Context.MODE_PRIVATE).edit().clear().apply()
    // Stop the shade blocker loop
    serviceRef?.stopBlocking()
    // Service stays connected (needed for future 
    // sessions) but does nothing until next start
  }

  fun isActive(): Boolean {
    if (!active) return false
    if (System.currentTimeMillis() > endTimeMs) {
      active = false
      return false
    }
    return true
  }

  fun restoreFromPrefs(context: Context) {
    val prefs = context.getSharedPreferences(
      "strict", Context.MODE_PRIVATE)
    val savedActive = prefs.getBoolean("active", false)
    val savedEnd = prefs.getLong("end", 0L)
    if (savedActive && 
        System.currentTimeMillis() < savedEnd) {
      active = true
      endTimeMs = savedEnd
      serviceRef?.startBlocking()
    } else if (savedActive) {
      context.getSharedPreferences("strict",
        Context.MODE_PRIVATE).edit().clear().apply()
    }
  }

  fun getRemainingMs() = 
    maxOf(0L, endTimeMs - System.currentTimeMillis())
}
