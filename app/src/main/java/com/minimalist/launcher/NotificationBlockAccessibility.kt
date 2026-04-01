package com.minimalist.launcher

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class NotificationBlockAccessibility : AccessibilityService() {

    companion object {
        var isEnabled = false
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isEnabled = true
        val info = AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        info.notificationTimeout = 50
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!MissLauncherApp.blockNotifPanel) return

        val pkg = event.packageName?.toString() ?: return

        // Detect system UI / notification panel opening
        val systemUiPackages = listOf(
            "com.android.systemui",
            "com.iqoo.systemui",
            "com.vivo.systemui",
            "com.realme.systemui",
            "com.miui.systemui",
            "com.samsung.android.systemui"
        )

        if (pkg in systemUiPackages && event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // System panel opened — dismiss it immediately
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    override fun onInterrupt() {
        isEnabled = false
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isEnabled = false
        return super.onUnbind(intent)
    }
}
