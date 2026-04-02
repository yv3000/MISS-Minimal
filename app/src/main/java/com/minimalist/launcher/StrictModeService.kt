package com.minimalist.launcher

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityEvent

class StrictModeService : AccessibilityService() {

  companion object {
    var isStrictModeActive = false
    var strictEndTimeMs = 0L
    
    fun startStrict(durationMs: Long) {
      isStrictModeActive = true
      strictEndTimeMs = System.currentTimeMillis() + durationMs
      // State is saved directly in FocusActivity per the flow
    }
    
    fun endStrict() {
      isStrictModeActive = false
      strictEndTimeMs = 0L
    }
    
    fun isActive(): Boolean {
      if (!isStrictModeActive) return false
      if (System.currentTimeMillis() > strictEndTimeMs) {
        endStrict()
        return false
      }
      return true
    }
    
    fun getRemainingMs(): Long {
      return maxOf(0, strictEndTimeMs - System.currentTimeMillis())
    }
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent) {
    if (!isActive()) return
    
    if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
      val packageName = event.packageName?.toString() ?: return
      
      // Allow only our launcher package
      val allowedPackages = setOf(
        "com.minimalist.launcher",
        "com.android.systemui"  // allow system UI only
      )
      
      if (packageName !in allowedPackages) {
        // Any other app opened — send back to launcher immediately
        performGlobalAction(GLOBAL_ACTION_HOME)
      }
    }
  }

  override fun onInterrupt() {}
  
  override fun onServiceConnected() {
    val info = AccessibilityServiceInfo()
    info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
    info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
    info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
    info.notificationTimeout = 100
    serviceInfo = info
  }
}
