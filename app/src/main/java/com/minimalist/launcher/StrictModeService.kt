package com.minimalist.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo

class StrictModeService : AccessibilityService() {

    companion object {
        var instance: StrictModeService? = null
        fun isEnabled(context: Context): Boolean {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabledServices.contains(context.packageName)
        }
    }

    private val handler = Handler(Looper.getMainLooper())

    // ── SHADE BLOCKER ──
    // Runs every 50ms when blocking is active.
    // This is the PRIMARY defense against the notification shade.
    // It runs continuously in a tight loop regardless of accessibility events.
    private val shadeBlocker = object : Runnable {
        override fun run() {
            val shouldBlock = StrictModeManager.isActive() || PomodoroManager.isActive
            if (shouldBlock) {
                // ALWAYS dismiss the shade — this is the core mechanism
                if (Build.VERSION.SDK_INT >= 31) {
                    performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
                } else {
                    // Pre-Android 12: collapse status bar via reflection
                    try {
                        val sbService = getSystemService("statusbar")
                        val sbClass = Class.forName("android.app.StatusBarManager")
                        val collapse = sbClass.getMethod("collapsePanels")
                        collapse.invoke(sbService)
                    } catch (e: Exception) {
                        performGlobalAction(GLOBAL_ACTION_BACK)
                    }
                }
                
                // ALSO check for floating windows here proactively
                if (PomodoroManager.isActive) {
                    checkAndKillFloatingWindows()
                }

                handler.postDelayed(this, 50)
            }
        }
    }

    private fun checkAndKillFloatingWindows() {
        try {
            val allWindows = windows
            allWindows?.forEach { window ->
                val winType = window.type
                // TYPE_APPLICATION windows that are NOT the launcher and NOT full screen
                // usually indicate a floating/pop-up window (like OEM sidebar apps)
                if (winType == AccessibilityWindowInfo.TYPE_APPLICATION || 
                    winType == AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER) {
                    
                    val winPkg = try { window.root?.packageName?.toString() } catch (e: Exception) { null }
                    val allowed = PomodoroManager.allowedPackages
                    
                    if (winPkg != null && winPkg != packageName && !allowed.contains(winPkg)) {
                        // This is an unauthorized floating window — kill it
                        performGlobalAction(GLOBAL_ACTION_BACK)
                    }
                }
            }
        } catch (e: Exception) {}
    }

    fun startBlocking() {
        handler.removeCallbacks(shadeBlocker)
        handler.post(shadeBlocker)
    }

    fun stopBlocking() {
        handler.removeCallbacks(shadeBlocker)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // ── AUTO-START BLOCKING ──
        val shouldAutoBlock = getSharedPreferences("miss_prefs", MODE_PRIVATE)
            .getBoolean("pomodoro_shade_block", false)
        
        if (shouldAutoBlock || PomodoroManager.isActive || StrictModeManager.isActive()) {
            startBlocking()
        }
    }

    override fun onDestroy() {
        instance = null
        handler.removeCallbacks(shadeBlocker)
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val eventPkg = event.packageName?.toString() ?: return

        // ── POMODORO BLOCKING ──
        if (PomodoroManager.isActive) {
            
            // ALWAYS dismiss notification shade on EVERY event
            if (Build.VERSION.SDK_INT >= 31) {
                performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
            }
            
            val allowed = PomodoroManager.allowedPackages
            
            // If the event itself is from a non-allowed source, kill it
            if (!allowed.contains(eventPkg) && eventPkg != "com.android.systemui") {
                performGlobalAction(GLOBAL_ACTION_BACK)
                
                // Bring FocusActivity back to front
                handler.postDelayed({
                    try {
                        val intent = Intent(this, FocusActivity::class.java).apply {
                            addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                            )
                            putExtra("tab", "pomodoro")
                        }
                        startActivity(intent)
                    } catch (e: Exception) {
                        performGlobalAction(GLOBAL_ACTION_HOME)
                    }
                }, 50)
            }
            return
        }

        // ── STRICT MODE BLOCKING ──
        if (StrictModeManager.isActive()) {
            if (Build.VERSION.SDK_INT >= 31) {
                performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
            }
            
            val blockedPkgs = StrictModeManager.getBlockedPackages()
            if (blockedPkgs.isNotEmpty() && blockedPkgs.contains(eventPkg)) {
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }
    }

    override fun onInterrupt() {}
}
