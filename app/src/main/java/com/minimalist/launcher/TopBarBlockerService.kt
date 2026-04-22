package com.minimalist.launcher

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

class TopBarBlockerService : Service() {

    private var topOverlayView: View? = null
    private var rightEdgeView: View? = null
    private var leftEdgeView: View? = null
    private lateinit var windowManager: WindowManager
    
    private var touchStartY = 0f
    private var panelOpenedThisGesture = false

    companion object {
        fun start(context: Context) {
            if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(context)) return
            
            val intent = Intent(context, TopBarBlockerService::class.java)
            intent.action = "START"
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, TopBarBlockerService::class.java)
            intent.action = "STOP"
            context.startService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START" -> addOverlays()
            "STOP" -> {
                removeOverlays()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun addOverlays() {
        if (topOverlayView != null) return
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        val type = if (Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            
        val statusBarH = getStatusBarHeight()
        
        // ── TOP OVERLAY (blocks notification shade pull-down) ──
        addTopOverlay(type, statusBarH)
        
        // ── RIGHT EDGE OVERLAY (blocks OEM sidebar swipe) ──
        addRightEdgeOverlay(type)
        
        // ── LEFT EDGE OVERLAY (blocks OEM sidebar swipe) ──
        addLeftEdgeOverlay(type)
    }

    private fun addTopOverlay(type: Int, statusBarH: Int) {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            statusBarH * 3,
            0, 0, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSPARENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        
        topOverlayView = View(this).apply {
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        touchStartY = event.rawY
                        panelOpenedThisGesture = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dy = event.rawY - touchStartY
                        if (dy > 60f && !panelOpenedThisGesture) {
                            panelOpenedThisGesture = true
                            // Open our custom panel instead
                            val intent = Intent("com.minimalist.launcher.OPEN_NOTIF_PANEL")
                            intent.`package` = packageName
                            sendBroadcast(intent)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> {
                        panelOpenedThisGesture = false
                        true
                    }
                    else -> true
                }
            }
        }
        try { windowManager.addView(topOverlayView, params) } catch (e: Exception) {}
    }

    private fun addRightEdgeOverlay(type: Int) {
        // Cover right 32dp of screen to block sidebar swipe-in gesture
        val edgeWidth = dpToPx(32)
        val params = WindowManager.LayoutParams(
            edgeWidth,
            WindowManager.LayoutParams.MATCH_PARENT,
            0, 0, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSPARENT
        )
        params.gravity = Gravity.TOP or Gravity.END  // right edge
        rightEdgeView = View(this).apply {
            setOnTouchListener { _, _ -> true }  // consume all touches
        }
        try { windowManager.addView(rightEdgeView, params) } catch (e: Exception) {}
    }

    private fun addLeftEdgeOverlay(type: Int) {
        val edgeWidth = dpToPx(32)
        val params = WindowManager.LayoutParams(
            edgeWidth,
            WindowManager.LayoutParams.MATCH_PARENT,
            0, 0, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSPARENT
        )
        params.gravity = Gravity.TOP or Gravity.START  // left edge
        leftEdgeView = View(this).apply {
            setOnTouchListener { _, _ -> true }  // consume all touches
        }
        try { windowManager.addView(leftEdgeView, params) } catch (e: Exception) {}
    }

    private fun removeOverlays() {
        topOverlayView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) {}
            topOverlayView = null
        }
        rightEdgeView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) {}
            rightEdgeView = null
        }
        leftEdgeView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) {}
            leftEdgeView = null
        }
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) {
            resources.getDimensionPixelSize(resourceId)
        } else {
            dpToPx(24)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        removeOverlays()
        super.onDestroy()
    }
}
