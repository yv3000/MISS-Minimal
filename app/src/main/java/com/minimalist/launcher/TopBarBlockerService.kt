package com.minimalist.launcher

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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
    private val handler = Handler(Looper.getMainLooper())
    
    private var touchStartY = 0f
    private var panelOpenedThisGesture = false
    private val stateCheck = object : Runnable {
        override fun run() {
            if (shouldBlock()) addOverlays() else removeOverlays()
            if (shouldBlock() || PomodoroManager.isActive) {
                handler.postDelayed(this, 750)
            } else {
                stopSelf()
            }
        }
    }

    companion object {
        fun start(context: Context) {
            if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(context)) return
            
            val intent = Intent(context, TopBarBlockerService::class.java)
            intent.action = "START"
            context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TopBarBlockerService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }
        handler.removeCallbacks(stateCheck)
        handler.post(stateCheck)
        return START_STICKY
    }

    private fun shouldBlock() =
        StrictModeManager.isActive() || PomodoroManager.isWorkSessionActive()

    private fun addOverlays() {
        if (topOverlayView != null) return
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        val type = if (Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            
        val statusBarH = getStatusBarHeight()
        
        addTopOverlay(type, statusBarH)
        addEdgeOverlay(type, Gravity.END)
        addEdgeOverlay(type, Gravity.START)
    }

    private fun addTopOverlay(type: Int, statusBarH: Int) {
        val topH = statusBarH + dpToPx(24)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            topH,
            0, 0, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP
        
        topOverlayView = View(this).apply {
            setBackgroundColor(0x01000000)
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                    try {
                        @Suppress("DEPRECATION")
                        sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
                    } catch (_: Exception) {}
                    try {
                        val sb = getSystemService("statusbar")
                        val method = sb?.javaClass?.getMethod("collapsePanels")
                        method?.isAccessible = true
                        method?.invoke(sb)
                    } catch (_: Exception) {}
                    try {
                        StrictModeService.instance?.dismissNotificationShade()
                    } catch (_: Exception) {}
                }
                true
            }
        }
        try { windowManager.addView(topOverlayView, params) } catch (e: Exception) {}
    }

    private fun addEdgeOverlay(type: Int, gravity: Int) {
        val edgeWidth = dpToPx(28)
        val params = WindowManager.LayoutParams(
            edgeWidth,
            WindowManager.LayoutParams.MATCH_PARENT,
            0, 0, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = gravity
        val edgeView = View(this).apply {
            setBackgroundColor(0x01000000)
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
        handler.removeCallbacks(stateCheck)
        removeOverlays()
        super.onDestroy()
    }
}
