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
        "com.android.phone",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.google.android.incallui",
        "com.android.incallui",
        "com.android.server.telecom",
        "com.android.telecom",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.coloros.securitypermission",
        "com.samsung.android.dialer",
        "com.samsung.android.incallui",
        "com.vivo.dialer",
        "com.iqoo.phone",
        "com.oneplus.dialer",
        "com.oplus.dialer",
        "com.heytap.dialer",
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
        workChunkSeconds = durationMinutes * 60
        
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

        context.getSharedPreferences("pomodoro_prefs", Context.MODE_PRIVATE).edit()
            .putBoolean("is_active", true)
            .putInt("duration_minutes", durationMinutes)
            .putStringSet("selected_apps", safeApps.toSet())
            .putString("contact_0_name", emergencyContacts[0]?.first)
            .putString("contact_0_num", emergencyContacts[0]?.second)
            .putString("contact_1_name", emergencyContacts[1]?.first)
            .putString("contact_1_num", emergencyContacts[1]?.second)
            .apply()
        
        // Block notification shade via Shizuku
        PrivilegedToggle.setStatusBarExpansion(true)
        StrictModeService.instance?.startBlocking()
    }

    fun restoreFromPrefs(context: Context) {
        val prefs = context.getSharedPreferences("pomodoro_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("is_active", false)) return
        val dur = prefs.getInt("duration_minutes", 25)
        val apps = prefs.getStringSet("selected_apps", emptySet())?.toList() ?: emptyList()
        val c0 = prefs.getString("contact_0_num", null)?.let { (prefs.getString("contact_0_name", it) ?: it) to it }
        val c1 = prefs.getString("contact_1_num", null)?.let { (prefs.getString("contact_1_name", it) ?: it) to it }
        start(dur, apps, listOf(c0, c1), context)
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

        context.getSharedPreferences("pomodoro_prefs", Context.MODE_PRIVATE).edit()
            .putBoolean("is_active", false)
            .apply()
        
        // Unblock notification shade via Shizuku
        PrivilegedToggle.setStatusBarExpansion(false)
        TopBarBlockerService.stop(context)
        StrictModeService.instance?.stopBlocking()
    }

    fun isWorkSessionActive() = isActive && isWorkPhase
    fun isBreakActive() = isActive && !isWorkPhase
}
