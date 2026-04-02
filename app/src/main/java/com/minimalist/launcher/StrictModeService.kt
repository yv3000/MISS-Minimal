package com.minimalist.launcher

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

class StrictModeService : AccessibilityService() {

  private val handler = Handler(Looper.getMainLooper())
  
  companion object {
    var instance: StrictModeService? = null
  }

  // Runs every 100ms — continuously dismiss 
  // notification shade if it opens
  private val shadeBlocker = object : Runnable {
    override fun run() {
      // Only dismiss if strict mode is active
      if (StrictModeManager.isActive()) {
        if (Build.VERSION.SDK_INT >= 31) {
          performGlobalAction(
            GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        }
        handler.postDelayed(this, 200)
      }
      // If not active — runnable stops automatically
      // by not re-posting itself
    }
  }

  override fun onServiceConnected() {
    instance = this
    val info = AccessibilityServiceInfo().apply {
      eventTypes = (
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
        AccessibilityEvent.TYPE_WINDOWS_CHANGED or
        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
      )
      feedbackType = 
        AccessibilityServiceInfo.FEEDBACK_GENERIC
      flags = (
        AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
        AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
        AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
      )
      notificationTimeout = 50
    }
    serviceInfo = info
    StrictModeManager.serviceRef = this
  }

  override fun onAccessibilityEvent(
      event: AccessibilityEvent) {
    
    // CRITICAL: Do absolutely nothing if 
    // strict mode is not active
    if (!StrictModeManager.isActive()) return
    
    val pkg = event.packageName?.toString() ?: return

    // BLOCK 1: Notification shade / Quick Settings
    // These are systemui windows — dismiss them
    if (pkg == "com.android.systemui") {
      val className = event.className?.toString() ?: ""
      val title = event.text?.joinToString() ?: ""
      
      val isNotifShade = 
        className.contains("NotificationShade") ||
        className.contains("StatusBar") ||
        className.contains("QuickSettings") ||
        className.contains("NotificationPanel") ||
        title.contains("Notifications") ||
        title.contains("Quick settings")
      
      if (isNotifShade) {
        if (Build.VERSION.SDK_INT >= 31) {
          performGlobalAction(
            GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        } else {
          performGlobalAction(GLOBAL_ACTION_BACK)
          performGlobalAction(GLOBAL_ACTION_HOME)
        }
        return
      }
    }

    // BLOCK 2: Floating windows / Sidebar
    val floatingPackages = listOf(
      "com.vivo.easyaccess",
      "com.iqoo.easyaccess",
      "com.bbk.easyaccess", 
      "com.vivo.floatingball",
      "com.iqoo.secure",
      "com.miui.personalassistant",
      "com.samsung.android.app.edgecontent",
      "com.huawei.works",
      "com.oppo.assistantscreen",
      "com.coloros.assistantscreen"
    )
    
    if (floatingPackages.any { pkg.contains(it) || 
        it.contains(pkg) }) {
      performGlobalAction(GLOBAL_ACTION_BACK)
      bringStrictTimerToFront()
      return
    }

    // BLOCK 3: Any non-allowed app
    val allowedPackages = setOf(
      packageName,
      "com.android.systemui",
      "android"
    )

    if (event.eventType == 
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
        pkg !in allowedPackages) {
      performGlobalAction(GLOBAL_ACTION_BACK)
      handler.postDelayed({
        bringStrictTimerToFront()
      }, 100)
    }
  }

  fun bringStrictTimerToFront() {
    val intent = Intent(this, FocusActivity::class.java)
    intent.flags = (
      Intent.FLAG_ACTIVITY_NEW_TASK or
      Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
      Intent.FLAG_ACTIVITY_SINGLE_TOP
    )
    intent.putExtra("show_strict_timer", true)
    startActivity(intent)
  }

  fun startBlocking() {
    handler.removeCallbacks(shadeBlocker)
    handler.post(shadeBlocker)
  }

  fun stopBlocking() {
    handler.removeCallbacks(shadeBlocker)
  }

  override fun onInterrupt() {}

  override fun onDestroy() {
    instance = null
    StrictModeManager.serviceRef = null
    handler.removeCallbacksAndMessages(null)
    super.onDestroy()
  }
}
