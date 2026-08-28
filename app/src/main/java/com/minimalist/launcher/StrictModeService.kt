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
    private var overlayBlocking = false

    private val shadeBlocker = object : Runnable {
        override fun run() {
            val shouldBlock = shouldBlock()
            syncOverlay(shouldBlock)
            if (shouldBlock) {
                dismissNotificationShade()
                if (PomodoroManager.isWorkSessionActive()) {
                    checkAndKillFloatingWindows()
                }
            }
            if (shouldBlock || PomodoroManager.isActive) handler.postDelayed(this, 750)
        }
    }

    private fun shouldBlock() =
        StrictModeManager.isActive() || PomodoroManager.isWorkSessionActive()

    private fun dismissNotificationShade() {
        if (Build.VERSION.SDK_INT >= 31) {
            performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
            return
        }
        try {
            val statusBar = getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager")
                .getMethod("collapsePanels")
                .invoke(statusBar)
        } catch (_: Exception) {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    private fun syncOverlay(shouldBlock: Boolean) {
        if (overlayBlocking == shouldBlock) return
        overlayBlocking = shouldBlock
        if (shouldBlock) TopBarBlockerService.start(this) else TopBarBlockerService.stop(this)
    }

    private fun isSystemUi(packageName: String) =
        packageName.contains("systemui", ignoreCase = true)

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
        if (shouldBlock()) handler.post(shadeBlocker) else syncOverlay(false)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        StrictModeManager.serviceRef = this

        if (shouldBlock()) {
            startBlocking()
        }
    }

    override fun onDestroy() {
        instance = null
        if (StrictModeManager.serviceRef === this) StrictModeManager.serviceRef = null
        handler.removeCallbacks(shadeBlocker)
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val eventPkg = event.packageName?.toString() ?: return
        val shouldBlock = shouldBlock()
        syncOverlay(shouldBlock)
        if (!shouldBlock) return

        if (isSystemUi(eventPkg)) dismissNotificationShade()

        if (PomodoroManager.isWorkSessionActive()) {
            
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

        if (StrictModeManager.isActive()) {
            val blockedPkgs = StrictModeManager.getBlockedPackages()
            if (blockedPkgs.isNotEmpty() && blockedPkgs.contains(eventPkg)) {
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }
    }

    override fun onInterrupt() {}
}
