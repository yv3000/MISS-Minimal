package com.minimalist.launcher

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent

class NotificationBlockAccessibility : AccessibilityService() {

  companion object {
    var isActive = false
  }

  override fun onServiceConnected() {
    isActive = true
    val info = serviceInfo ?: AccessibilityServiceInfo()
    info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
    info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
    info.notificationTimeout = 100
    serviceInfo = info
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    if (!shouldBlock()) return
    val pkg = event?.packageName?.toString() ?: return
    if (isSystemUI(pkg)) dismissNotificationShade()
  }

  fun shouldBlock(): Boolean {
    return StrictModeManager.isActive() || PomodoroManager.isWorkSessionActive()
  }

  fun isSystemUI(pkg: String): Boolean {
    return pkg.contains("systemui", ignoreCase = true)
  }

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

  override fun onInterrupt() { isActive = false }
  
  override fun onUnbind(i: Intent?): Boolean {
    isActive = false
    return super.onUnbind(i)
  }
}
