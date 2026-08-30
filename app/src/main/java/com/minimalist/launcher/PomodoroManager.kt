package com.minimalist.launcher

import android.content.Context
import android.telecom.TelecomManager

object PomodoroManager {
    var isActive = false
    var isWorkPhase = true
    var sessionCount = 1
    var workDurationSeconds = 25 * 60
    
    var remainingWorkSeconds = 0
    var workChunkSeconds = 25 * 60
    
    // ── USER-SELECTED APPS (separate from system-always-allowed) ──
    val userSelectedApps = mutableListOf<String>()
    val allowedPackages = mutableSetOf<String>()
    
    val emergencyContacts = MutableList<Pair<String, String>?>(2) { null }

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
        contacts: List<Pair<String, String>?>,
        context: Context
    ) {
        workDurationSeconds = durationMinutes * 60
        remainingWorkSeconds = workDurationSeconds
        workChunkSeconds = 25 * 60
        
        isActive = true
        isWorkPhase = true
        sessionCount = 1
        
        emergencyContacts.indices.forEach { emergencyContacts[it] = contacts.getOrNull(it) }

        val safeApps = allowedApps.distinct().filter { packageName ->
            runCatching {
                val app = context.packageManager.getApplicationInfo(packageName, 0)
                !GameDetector.isLikelyGame(context.packageManager, app)
            }.getOrDefault(false)
        }.take(3)

        userSelectedApps.clear()
        userSelectedApps.addAll(safeApps)

        // Build allowed package list for accessibility service checks
        allowedPackages.clear()
        allowedPackages.addAll(ALWAYS_ALLOWED)
        allowedPackages.addAll(safeApps)
        context.getSystemService(TelecomManager::class.java)
            .defaultDialerPackage
            ?.let(allowedPackages::add)

        TopBarBlockerService.start(context)
        isWorkPhase = true
        
        context.getSharedPreferences("miss_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("pomodoro_shade_block", true)
            .apply()
        
        StrictModeService.instance?.startBlocking()
    }

    fun stop(context: Context) {
        isActive = false
        isWorkPhase = true
        sessionCount = 0
        emergencyContacts.fill(null)
        userSelectedApps.clear()
        allowedPackages.clear()
        
        remainingWorkSeconds = 0
        
        context.getSharedPreferences("miss_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("pomodoro_shade_block", false)
            .apply()
        
        TopBarBlockerService.stop(context)
        StrictModeService.instance?.stopBlocking()
    }

    fun isWorkSessionActive() = isActive && isWorkPhase
    fun isBreakActive() = isActive && !isWorkPhase
}
