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
    // Runs every 80ms when blocking is active
    // Handles BOTH strict mode AND pomodoro
    private val shadeBlocker = object : Runnable {
        override fun run() {
            val shouldBlock = StrictModeManager.isActive() || PomodoroManager.isActive
            if (shouldBlock) {
                if (Build.VERSION.SDK_INT >= 31) {
                    performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
                } else {
                    // Pre-Android 12 fallback
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
                handler.postDelayed(this, 80)
            }
        }
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
    }

    override fun onDestroy() {
        instance = null
        handler.removeCallbacks(shadeBlocker)
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val eventPkg = event.packageName?.toString() ?: return

        // Skip irrelevant event types early
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return

        // ── POMODORO BLOCKING ──
        // This runs when Pomodoro session is active
        if (PomodoroManager.isActive) {
            val allowed = PomodoroManager.allowedPackages
            if (!allowed.contains(eventPkg)) {
                // Block this app — send back to our activity
                performGlobalAction(GLOBAL_ACTION_HOME)
                
                // Also bring PomodoroActivity to front after short delay
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
                        // fallback: just go home
                        performGlobalAction(GLOBAL_ACTION_HOME)
                    }
                }, 100)
            }

            // Also dismiss notification shade proactively
            val title = event.className?.toString() ?: ""
            if (title.contains("StatusBar", true) ||
                title.contains("NotificationShade", true) ||
                title.contains("QuickSettings", true) ||
                title.contains("VolumeDialog", true)) {
                if (Build.VERSION.SDK_INT >= 31) {
                    performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
                }
            }

            // Block ALL floating windows on ANY OS
            // Window type check instead of OEM-specific package names
            val windows = windows
            windows?.forEach { window ->
                if (window.type == AccessibilityWindowInfo.TYPE_SYSTEM ||
                    window.type == AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER) {
                    val winPkg = try { window.root?.packageName?.toString() } catch (e: Exception) { null } ?: return@forEach
                    if (!allowed.contains(winPkg)) {
                        performGlobalAction(GLOBAL_ACTION_BACK)
                    }
                }
            }

            // Also block any non-allowed app that opened a new window
            if (!allowed.contains(eventPkg)) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
            return
        }

        // ── STRICT MODE BLOCKING ──
        if (StrictModeManager.isActive()) {
            val blockedPkgs = StrictModeManager.getBlockedPackages()
            if (blockedPkgs.isNotEmpty() && blockedPkgs.contains(eventPkg)) {
                performGlobalAction(GLOBAL_ACTION_HOME)
            }

            // Also dismiss notification shade
            val title = event.className?.toString() ?: ""
            if (title.contains("StatusBar", true) ||
                title.contains("NotificationShade", true)) {
                if (Build.VERSION.SDK_INT >= 31) {
                    performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
                }
            }
        }
    }

    override fun onInterrupt() {}
}
