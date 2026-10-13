package com.minimalist.launcher

import android.app.NotificationManager
import android.content.Context
import android.telecom.TelecomManager

object PomodoroManager {
    var isActive = false
    var isWorkPhase = true
    var currentSession = 1
    var totalSessions = 1
    var selectedDurationMinutes = 25
    val sessionCount: Int get() = currentSession
    
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
        selectedDurationMinutes = durationMinutes
        totalSessions = (durationMinutes / 25).coerceAtLeast(1)
        currentSession = 1
        isActive = true
        isWorkPhase = true
        
        emergencyContacts.indices.forEach { emergencyContacts[it] = contacts.getOrNull(it) }

        val safeApps = allowedApps.distinct().take(3)

        userSelectedApps.clear()
        userSelectedApps.addAll(safeApps)

        allowedPackages.clear()
        allowedPackages.addAll(ALWAYS_ALLOWED)
        allowedPackages.addAll(safeApps)
        context.getSystemService(TelecomManager::class.java)
            ?.defaultDialerPackage
            ?.let(allowedPackages::add)

        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        imm?.enabledInputMethodList?.forEach { allowedPackages.add(it.packageName) }

        applyRestrictions(context, enable = true)
        persistState(context)
    }

    fun startBreak(context: Context) {
        isWorkPhase = false
        applyRestrictions(context, enable = false)
        persistState(context)
    }

    fun resumeNextWorkSession(context: Context) {
        currentSession++
        isWorkPhase = true
        applyRestrictions(context, enable = true)
        persistState(context)
    }

    fun stop(context: Context) {
        isActive = false
        isWorkPhase = true
        currentSession = 1
        totalSessions = 1
        emergencyContacts.fill(null)
        userSelectedApps.clear()
        allowedPackages.clear()

        applyRestrictions(context, enable = false)
        context.getSharedPreferences("pomodoro_prefs", Context.MODE_PRIVATE).edit()
            .putBoolean("is_active", false)
            .apply()
    }

    private fun applyRestrictions(context: Context, enable: Boolean) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (nm != null && nm.isNotificationPolicyAccessGranted) {
            nm.setInterruptionFilter(
                if (enable) NotificationManager.INTERRUPTION_FILTER_NONE
                else NotificationManager.INTERRUPTION_FILTER_ALL
            )
        }
        if (enable) {
            TopBarBlockerService.start(context)
            StrictModeService.instance?.startBlocking()
        } else {
            TopBarBlockerService.stop(context)
            StrictModeService.instance?.stopBlocking()
        }
    }

    private fun persistState(context: Context) {
        context.getSharedPreferences("pomodoro_prefs", Context.MODE_PRIVATE).edit()
            .putBoolean("is_active", isActive)
            .putBoolean("is_work_phase", isWorkPhase)
            .putInt("current_session", currentSession)
            .putInt("total_sessions", totalSessions)
            .putInt("duration_minutes", selectedDurationMinutes)
            .putStringSet("selected_apps", userSelectedApps.toSet())
            .putString("contact_0_name", emergencyContacts[0]?.first)
            .putString("contact_0_num", emergencyContacts[0]?.second)
            .putString("contact_1_name", emergencyContacts[1]?.first)
            .putString("contact_1_num", emergencyContacts[1]?.second)
            .apply()
    }

    fun restoreFromPrefs(context: Context) {
        val prefs = context.getSharedPreferences("pomodoro_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("is_active", false)) return
        val dur = prefs.getInt("duration_minutes", 25)
        val curSess = prefs.getInt("current_session", 1)
        val totSess = prefs.getInt("total_sessions", 1)
        val isWork = prefs.getBoolean("is_work_phase", true)
        val apps = prefs.getStringSet("selected_apps", emptySet())?.toList() ?: emptyList()
        val c0 = prefs.getString("contact_0_num", null)?.let { (prefs.getString("contact_0_name", it) ?: it) to it }
        val c1 = prefs.getString("contact_1_num", null)?.let { (prefs.getString("contact_1_name", it) ?: it) to it }

        selectedDurationMinutes = dur
        totalSessions = totSess
        currentSession = curSess
        isActive = true
        isWorkPhase = isWork

        emergencyContacts[0] = c0
        emergencyContacts[1] = c1

        userSelectedApps.clear()
        userSelectedApps.addAll(apps)

        allowedPackages.clear()
        allowedPackages.addAll(ALWAYS_ALLOWED)
        allowedPackages.addAll(apps)
        context.getSystemService(TelecomManager::class.java)
            ?.defaultDialerPackage
            ?.let(allowedPackages::add)

        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        imm?.enabledInputMethodList?.forEach { allowedPackages.add(it.packageName) }

        applyRestrictions(context, enable = isWork)
    }

    fun isWorkSessionActive() = isActive && isWorkPhase
    fun isBreakActive() = isActive && !isWorkPhase
}
