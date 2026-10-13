package com.minimalist.launcher

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent

class NotificationBlockAccessibility : AccessibilityService() {

  companion object {
    var isActive = false
    var instance: NotificationBlockAccessibility? = null
  }

  override fun onServiceConnected() {
    isActive = true
    instance = this
    val info = serviceInfo ?: AccessibilityServiceInfo()
    info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
    info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
    info.notificationTimeout = 100
    serviceInfo = info
  }

  private var lastPanelOpenTime = 0L

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    val pkg = event?.packageName?.toString() ?: return
    if (!isSystemUI(pkg)) return

    if (shouldBlock()) {
      dismissNotificationShade()
      return
    }

    if (MissLauncherApp.blockNotifPanel) {
      dismissNotificationShade()
      val now = android.os.SystemClock.uptimeMillis()
      if (now - lastPanelOpenTime > 800) {
        lastPanelOpenTime = now
        val intent = Intent(this, NotificationPanelActivity::class.java).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
      }
    }
  }

  fun shouldBlock(): Boolean {
    return StrictModeManager.isActive() || PomodoroManager.isWorkSessionActive()
  }

  fun isSystemUI(pkg: String): Boolean {
    val p = pkg.lowercase()
    return p.contains("systemui") ||
           p.contains("securityadd") ||
           p.contains("securitycenter") ||
           p.contains("cocktailbar") ||
           p.contains("smartsidebar") ||
           p.contains("sidebar") ||
           p.contains("smartpanel") ||
           p.contains("sidepanel") ||
           p.contains("floatingscreen") ||
           p.contains("dockbar")
  }

  fun dismissNotificationShade() {
    PrivilegedToggle.collapseStatusBar()
    try {
      val statusBar = getSystemService("statusbar")
      Class.forName("android.app.StatusBarManager")
        .getMethod("collapsePanels")
        .invoke(statusBar)
    } catch (_: Exception) {}
    if (Build.VERSION.SDK_INT >= 31) {
      performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
    }
    performGlobalAction(GLOBAL_ACTION_BACK)
    @Suppress("DEPRECATION")
    sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
  }

  override fun onInterrupt() {
    isActive = false
    instance = null
  }
  
  override fun onUnbind(i: Intent?): Boolean {
    isActive = false
    instance = null
    return super.onUnbind(i)
  }
}
