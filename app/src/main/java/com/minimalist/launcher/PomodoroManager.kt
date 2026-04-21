package com.minimalist.launcher

import android.content.Context

object PomodoroManager {
    var isActive = false
    var isWorkPhase = true
    var allowedPackages = mutableSetOf<String>()
    var sessionCount = 0
    var totalFocusMinutes = 0
    var workDurationSeconds = 25 * 60

    fun start(
        context: Context,
        durationMinutes: Int,
        allowedApps: List<String>,
        emergencyContact: String?
    ) {
        isActive = true
        isWorkPhase = true
        sessionCount = 1
        workDurationSeconds = durationMinutes * 60
        allowedPackages.clear()
        allowedPackages.addAll(allowedApps)
        
        // Always add phone apps to be safe
        allowedPackages.add("com.android.dialer")
        allowedPackages.add("com.google.android.dialer")
        allowedPackages.add("com.samsung.android.dialer")
        allowedPackages.add("com.vivo.dialer")
        allowedPackages.add("com.iqoo.phone")
        
        // Our launcher always allowed
        allowedPackages.add("com.minimalist.launcher")
        allowedPackages.add("android")
        allowedPackages.add("com.android.systemui")

        TopBarBlockerService.start(context)
        StrictModeService.instance?.startBlocking()
    }

    fun stop(context: Context) {
        isActive = false
        isWorkPhase = true
        sessionCount = 0
        allowedPackages.clear()
        
        TopBarBlockerService.stop(context)
        StrictModeService.instance?.stopBlocking()
    }

    fun isWorkSessionActive() = isActive && isWorkPhase
    fun isBreakActive() = isActive && !isWorkPhase
}
