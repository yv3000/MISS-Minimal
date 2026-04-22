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

    private var topOverlay: View? = null
    private var leftOverlay: View? = null
    private var rightOverlay: View? = null
    private lateinit var windowManager: WindowManager

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
        if (topOverlay != null) return

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val type = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }

        // 1. TOP OVERLAY (Blocks Notifs)
        val statusBarHeight = getStatusBarHeight()
        val topParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            statusBarHeight * 2, // Double height for buffer
            0, 0, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSPARENT
        )
        topParams.gravity = Gravity.TOP
        topOverlay = createBlockerView()
        windowManager.addView(topOverlay, topParams)

        // 2. LEFT OVERLAY (Blocks Sidebars)
        val edgeWidth = dpToPx(20)
        val leftParams = WindowManager.LayoutParams(
            edgeWidth,
            WindowManager.LayoutParams.MATCH_PARENT,
            0, 0, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSPARENT
        )
        leftParams.gravity = Gravity.START
        leftOverlay = createBlockerView()
        windowManager.addView(leftOverlay, leftParams)

        // 3. RIGHT OVERLAY (Blocks Sidebars)
        val rightParams = WindowManager.LayoutParams(
            edgeWidth,
            WindowManager.LayoutParams.MATCH_PARENT,
            0, 0, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSPARENT
        )
        rightParams.gravity = Gravity.END
        rightOverlay = createBlockerView()
        windowManager.addView(rightOverlay, rightParams)
    }

    private fun createBlockerView(): View {
        return View(this).apply {
            setOnTouchListener { _, event ->
                true // Consume EVERYTHING
            }
        }
    }

    private fun removeOverlays() {
        val overlays = listOf(topOverlay, leftOverlay, rightOverlay)
        overlays.forEach { view ->
            view?.let {
                try {
                    windowManager.removeView(it)
                } catch (e: Exception) {}
            }
        }
        topOverlay = null
        leftOverlay = null
        rightOverlay = null
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
