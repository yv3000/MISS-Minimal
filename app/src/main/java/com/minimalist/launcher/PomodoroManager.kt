package com.minimalist.launcher

import android.content.Context

object PomodoroManager {
    var isActive = false
    var isWorkPhase = true
    var allowedPackages = mutableSetOf<String>()
    var sessionCount = 0
    var totalFocusMinutes = 0
    var totalWorkSeconds = 0
    var remainingWorkSeconds = 0
    var workChunkSeconds = 25 * 60

    var emergencyContactName: String? = null
    var emergencyContactNumber: String? = null
    var selectedAppPackages = mutableListOf<String>()

    fun start(
        context: Context,
        durationMinutes: Int,
        allowedApps: List<String>,
        contactName: String?,
        contactNumber: String?
    ) {
        isActive = true
        isWorkPhase = true
        sessionCount = 1
        totalWorkSeconds = durationMinutes * 60
        remainingWorkSeconds = totalWorkSeconds
        
        emergencyContactName = contactName
        emergencyContactNumber = contactNumber
        selectedAppPackages.clear()
        selectedAppPackages.addAll(allowedApps)

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
        emergencyContactName = null
        emergencyContactNumber = null
        selectedAppPackages.clear()
        allowedPackages.clear()
        
        TopBarBlockerService.stop(context)
        StrictModeService.instance?.stopBlocking()
    }

    fun isWorkSessionActive() = isActive && isWorkPhase
    fun isBreakActive() = isActive && !isWorkPhase
}
