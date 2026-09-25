package com.minimalist.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

class StrictModeService : AccessibilityService() {

    companion object {
        var instance: StrictModeService? = null
        private val SYSTEM_UI_PACKAGES = setOf(
            "com.android.systemui",
            "com.miui.systemui",
            "com.samsung.android.systemui",
            "com.coloros.systemui",
            "com.oplus.systemui",
            "com.vivo.systemui"
        )

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
            }
            if (shouldBlock || PomodoroManager.isActive) handler.postDelayed(this, 750)
        }
    }

    private fun shouldBlock() =
        StrictModeManager.isActive() || PomodoroManager.isWorkSessionActive()

    private fun dismissNotificationShade() {
        if (Build.VERSION.SDK_INT >= 31) {
            performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        } else {
            // Android 10-11 expose no public notification-shade dismiss action.
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
        @Suppress("DEPRECATION")
        sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
    }

    private fun syncOverlay(shouldBlock: Boolean) {
        if (overlayBlocking == shouldBlock) return
        overlayBlocking = shouldBlock
        if (shouldBlock) TopBarBlockerService.start(this) else TopBarBlockerService.stop(this)
    }

    private fun isSystemUi(packageName: String) = SYSTEM_UI_PACKAGES.any {
        packageName == it || packageName.startsWith("$it.")
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

        if (isSystemUi(eventPkg) && (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                    event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
                    event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED)) {
            dismissNotificationShade()
        }

        if (PomodoroManager.isWorkSessionActive()) {
            
            val allowed = PomodoroManager.allowedPackages
            
            // If the event itself is from a non-allowed source, kill it
            if (!allowed.contains(eventPkg) && !isSystemUi(eventPkg)) {
                performGlobalAction(GLOBAL_ACTION_HOME)
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
            if (!isSystemUi(eventPkg) && eventPkg != packageName) {
                performGlobalAction(GLOBAL_ACTION_HOME)
                performGlobalAction(GLOBAL_ACTION_BACK)
                
                handler.postDelayed({
                    try {
                        val intent = Intent(this, FocusActivity::class.java).apply {
                            addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                            )
                            putExtra("tab", "strict")
                        }
                        startActivity(intent)
                    } catch (e: Exception) {
                        performGlobalAction(GLOBAL_ACTION_HOME)
                    }
                }, 50)
            }
        }
    }

    override fun onInterrupt() {}

}
