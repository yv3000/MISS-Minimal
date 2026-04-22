package com.minimalist.launcher

import android.content.Context

object PomodoroManager {
    var isActive = false
    var isWorkPhase = true
    var sessionCount = 1
    var workDurationSeconds = 25 * 60
    
    var remainingWorkSeconds = 0
    var workChunkSeconds = 25 * 60
    
    val allowedPackages = mutableSetOf<String>()
    
    var emergencyContactName: String? = null
    var emergencyContactNumber: String? = null

    private val ALWAYS_ALLOWED = setOf(
        "com.minimalist.launcher",
        "android",
        "com.android.systemui",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.vivo.dialer",
        "com.iqoo.phone",
        "com.oneplus.dialer",
        "com.miui.home",
        "com.coloros.phonemanager"
    )

    fun start(
        durationMinutes: Int,
        allowedApps: List<String>,
        emergencyContact: String?,
        context: Context
    ) {
        workDurationSeconds = durationMinutes * 60
        remainingWorkSeconds = workDurationSeconds
        workChunkSeconds = 25 * 60 // standard pomodoro chunk
        
        // Set state FIRST — service checks this
        isActive = true
        isWorkPhase = true
        sessionCount = 1
        
        emergencyContactNumber = emergencyContact

        // Build allowed package list
        allowedPackages.clear()
        allowedPackages.addAll(ALWAYS_ALLOWED)
        allowedPackages.addAll(allowedApps)

        // Start top bar overlay (blocks notification shade swipe)
        TopBarBlockerService.start(context)
        
        // Start accessibility blocking loop
        // This is CRITICAL — without this, shade blocker never runs
        StrictModeService.instance?.startBlocking()
    }

    fun stop(context: Context) {
        isActive = false
        isWorkPhase = true
        sessionCount = 0
        emergencyContactName = null
        emergencyContactNumber = null
        allowedPackages.clear()
        
        remainingWorkSeconds = 0
        
        TopBarBlockerService.stop(context)
        StrictModeService.instance?.stopBlocking()
    }

    fun isWorkSessionActive() = isActive && isWorkPhase
    fun isBreakActive() = isActive && !isWorkPhase
}
