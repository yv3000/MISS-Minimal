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
        addEdgeOverlay(type, Gravity.END)
        
        // ── LEFT EDGE OVERLAY (blocks OEM sidebar swipe) ──
        addEdgeOverlay(type, Gravity.START)
    }

    private fun addTopOverlay(type: Int, statusBarH: Int) {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            statusBarH * 2, // Double height to catch the start of the swipe
            0, 0, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSPARENT
        )
        params.gravity = Gravity.TOP
        
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
                        if (dy > 80f && !panelOpenedThisGesture) {
                            panelOpenedThisGesture = true
                            // Open our custom panel broadcast
                            sendBroadcast(Intent("com.minimalist.launcher.OPEN_NOTIF_PANEL").apply {
                                `package` = packageName
                            })
                        }
                        true
                    }
                    else -> true
                }
            }
        }
        try { windowManager.addView(topOverlayView, params) } catch (e: Exception) {}
    }

    private fun addEdgeOverlay(type: Int, gravity: Int) {
        // Cover 40dp of screen edges to block sidebar gestures
        val edgeWidth = dpToPx(40)
        val params = WindowManager.LayoutParams(
            edgeWidth,
            WindowManager.LayoutParams.MATCH_PARENT,
            0, 0, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSPARENT
        )
        params.gravity = gravity
        val edgeView = View(this).apply {
            setOnTouchListener { _, _ -> true } // Consume all touches
        }
        if (gravity == Gravity.END) rightEdgeView = edgeView else leftEdgeView = edgeView
        try { windowManager.addView(edgeView, params) } catch (e: Exception) {}
    }

    private fun removeOverlays() {
        listOf(topOverlayView, rightEdgeView, leftEdgeView).forEach { view ->
            view?.let {
                try { windowManager.removeView(it) } catch (e: Exception) {}
            }
        }
        topOverlayView = null
        rightEdgeView = null
        leftEdgeView = null
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
