package com.minimalist.launcher

import android.content.Context

enum class StrictUiState {
    SETUP, RUNNING, COMPLETE
}

object StrictModeManager {
  var uiState = StrictUiState.SETUP
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
    TopBarBlockerService.start(context)
    serviceRef?.startBlocking()
  }

  fun stop(context: Context) {
    active = false
    endTimeMs = 0L
    context.getSharedPreferences("strict",
      Context.MODE_PRIVATE).edit().clear().apply()
    TopBarBlockerService.stop(context)
    serviceRef?.stopBlocking()
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
      TopBarBlockerService.start(context)
      serviceRef?.startBlocking()
    } else if (savedActive) {
      context.getSharedPreferences("strict",
        Context.MODE_PRIVATE).edit().clear().apply()
    }
  }

  fun getRemainingMs() = 
    maxOf(0L, endTimeMs - System.currentTimeMillis())

  fun getBlockedPackages(): List<String> {
    return listOf(
        "com.android.settings",
        "com.android.vending",
        "com.google.android.youtube",
        "com.whatsapp",
        "com.facebook.katana",
        "com.instagram.android",
        "com.twitter.android",
        "com.snapchat.android",
        "com.reddit.frontpage",
        "com.google.android.apps.docs",
        "com.google.android.gm",
        "com.netflix.mediaclient",
        "com.disney.disneyplus",
        "com.spotify.music"
    )
  }
}
