package com.minimalist.launcher

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
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
    if (isSystemUI(pkg)) {
      performGlobalAction(GLOBAL_ACTION_BACK)
    }
  }

  fun shouldBlock(): Boolean {
    return getSharedPreferences("miss_prefs", MODE_PRIVATE)
      .getBoolean("block_notif_panel", false)
  }

  fun isSystemUI(pkg: String): Boolean {
    val sysUIPkgs = listOf(
      "com.android.systemui",
      "com.iqoo.systemui",
      "com.vivo.systemui",
      "com.realme.systemui",
      "com.oppo.systemui"
    )
    return pkg in sysUIPkgs
  }

  override fun onInterrupt() { isActive = false }
  
  override fun onUnbind(i: Intent?): Boolean {
    isActive = false
    return super.onUnbind(i)
  }
}
