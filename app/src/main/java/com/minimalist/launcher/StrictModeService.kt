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
  private var phoneAppStartTimeMs = 0L
  
  private val phoneApps = setOf(
    "com.android.server.telecom", 
    "com.google.android.dialer", 
    "com.samsung.android.dialer", 
    "com.android.dialer",
    "com.android.phone"
  )
  
  companion object {
    var instance: StrictModeService? = null
  }

  private val shadeBlocker = object : Runnable {
    override fun run() {
      val strictActive = StrictModeManager.isActive()
      val pomodoroActive = PomodoroManager.isActive
      
      if (strictActive || pomodoroActive) {
        // DISMISS SHADE
        if (Build.VERSION.SDK_INT >= 31) {
          performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        } else {
          performGlobalAction(GLOBAL_ACTION_BACK)
        }

        // AGGRESSIVE WINDOW BLOCKING (Sidebars/Floating Windows)
        windows?.forEach { window ->
          val title = window.title?.toString() ?: ""
          if (title.contains("Sidebar", ignoreCase = true) ||
              title.contains("EasyTouch", ignoreCase = true) ||
              title.contains("Smart", ignoreCase = true) ||
              window.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_SYSTEM) {
            
            if (Build.VERSION.SDK_INT >= 31) {
              performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
            } else {
              performGlobalAction(GLOBAL_ACTION_BACK)
            }
          }
        }

        handler.postDelayed(this, 50)
      }
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

  override fun onAccessibilityEvent(event: AccessibilityEvent) {
    val strictActive = StrictModeManager.isActive()
    val pomodoroActive = PomodoroManager.isActive
    if (!strictActive && !pomodoroActive) return

    val windowsInfo = windows
    windowsInfo?.forEach { window ->
      val title = window.title?.toString() ?: ""
      // Block System Panels (Notifs, Quick Settings, Sidebars)
      if (title.contains("Notification", ignoreCase = true) ||
          title.contains("Quick Settings", ignoreCase = true) ||
          title.contains("Sidebar", ignoreCase = true) ||
          title.contains("EasyTouch", ignoreCase = true) ||
          window.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_SYSTEM) {
        if (Build.VERSION.SDK_INT >= 31) {
          performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        } else {
          performGlobalAction(GLOBAL_ACTION_BACK)
        }
      }
    }
    
    val pkg = event.packageName?.toString() ?: return

    // BLOCK 1: Notification shade & Recents
    if (pkg == "com.android.systemui") {
      val className = event.className?.toString() ?: ""
      if (className.contains("Notification") || className.contains("StatusBar") || 
          className.contains("QuickSettings") || className.contains("NotificationPanel") ||
          className.contains("Recents") || className.contains("RecentTasks")) {
        
        if (Build.VERSION.SDK_INT >= 31) {
          performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        } else {
          performGlobalAction(GLOBAL_ACTION_BACK)
        }
        
        if (pomodoroActive && PomodoroManager.isWorkSessionActive()) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
        return
      }
    }

    // BLOCK 1.5: Home Screen / Launchers
    if (pkg.contains("launcher") || pkg.contains("trebuchet") || pkg.contains("home")) {
        if (pkg != packageName) { // If it's not OUR launcher
            if ((pomodoroActive && PomodoroManager.isWorkSessionActive()) || strictActive) {
                bringStrictTimerToFront()
                return
            }
        }
    }

    // BLOCK 2: Floating windows
    val floatingPackages = listOf(
      "com.vivo.easyaccess", "com.iqoo.easyaccess", "com.bbk.easyaccess", 
      "com.vivo.floatingball", "com.iqoo.secure", "com.miui.personalassistant",
      "com.samsung.android.app.edgecontent", "com.huawei.works",
      "com.oppo.assistantscreen", "com.coloros.assistantscreen",
      "com.android.systemui.shared.recents"
    )
    if (floatingPackages.any { pkg.contains(it) }) {
      performGlobalAction(GLOBAL_ACTION_BACK)
      bringStrictTimerToFront()
      return
    }

    // Extra Pomodoro enforcement: Iterate through windows
    if (pomodoroActive && PomodoroManager.isWorkSessionActive()) {
        windows?.forEach { window ->
            // If window is NOT our app and NOT system UI/phone, close it
            val winPkg = try { window.root?.packageName?.toString() } catch(e: Exception) { null }
            if (winPkg != null && winPkg != packageName && winPkg != "com.android.systemui" && winPkg !in phoneApps) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }
    }

    // BLOCK 3: Allowed Phone Apps (5 min limit)
    if (pkg in phoneApps) {
      if (phoneAppStartTimeMs == 0L) {
        phoneAppStartTimeMs = System.currentTimeMillis()
      } else if (System.currentTimeMillis() - phoneAppStartTimeMs > 5 * 60 * 1000L) {
        performGlobalAction(GLOBAL_ACTION_HOME)
        handler.postDelayed({ bringStrictTimerToFront() }, 100)
      }
      return
    } else if (pkg != "com.android.systemui" && event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
      phoneAppStartTimeMs = 0L
    }

    // BLOCK 4: Any non-allowed app
    if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
      if (pomodoroActive && PomodoroManager.isWorkSessionActive()) {
        if (pkg !in PomodoroManager.allowedPackages) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            handler.postDelayed({ bringStrictTimerToFront() }, 50)
            return
        }
      } else if (strictActive) {
        val allowedPackages = setOf(
          packageName,
          "com.android.systemui",
          "android"
        )
        if (pkg !in allowedPackages) {
          performGlobalAction(GLOBAL_ACTION_BACK)
          performGlobalAction(GLOBAL_ACTION_HOME)
          handler.postDelayed({
            bringStrictTimerToFront()
          }, 100)
        }
      }
    }
  }

  fun bringStrictTimerToFront() {
    val intent = Intent(this, FocusActivity::class.java)
    intent.flags = (
      Intent.FLAG_ACTIVITY_NEW_TASK or
      Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
      Intent.FLAG_ACTIVITY_SINGLE_TOP
    )
    if (PomodoroManager.isActive) {
        intent.putExtra("tab", "pomodoro")
    } else {
        intent.putExtra("show_strict_timer", true)
    }
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
