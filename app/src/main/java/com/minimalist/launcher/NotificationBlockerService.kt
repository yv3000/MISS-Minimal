package com.minimalist.launcher

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager

class NotificationBlockerService : Service() {
    
    private var overlayView: View? = null
    private lateinit var windowManager: WindowManager
    
    override fun onBind(intent: Intent?) = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            removeOverlay()
            stopSelf()
            return START_NOT_STICKY
        }
        
        addOverlay()
        return START_STICKY
    }
    
    fun addOverlay() {
        if (overlayView != null) return
        
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        val type = if (Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        
        val displayMetrics = resources.displayMetrics
        val statusBarHeight = getStatusBarHeight()
        // Cover top 3× status bar height — 
        // enough to block swipe gesture trigger zone
        val overlayHeight = statusBarHeight * 4

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayHeight,        // ← only top strip
            0, 0, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        
        overlayView = View(this)
        overlayView!!.setOnTouchListener { _, _ -> true }
        
        try {
            windowManager.addView(overlayView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun removeOverlay() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {}
            overlayView = null
        }
    }
    
    fun getStatusBarHeight(): Int {
        var result = 0
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = resources.getDimensionPixelSize(resourceId)
        }
        return result
    }
    
    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }
}
