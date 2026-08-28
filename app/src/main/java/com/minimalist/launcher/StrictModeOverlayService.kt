package com.minimalist.launcher

import android.app.NotificationManager
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView

class StrictModeOverlayService : Service() {

    private var overlayView: View? = null
    private lateinit var wm: WindowManager
    private var remainingSeconds = 0
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        var isRunning = false
    }

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(
        intent: Intent?, flags: Int, startId: Int): Int {

        if (intent?.action == "STOP") {
            stopStrict()
            stopSelf()
            isRunning = false
            return START_NOT_STICKY
        }

        remainingSeconds = intent?.getIntExtra(
            "seconds", 25 * 60) ?: (25 * 60)
        isRunning = true
        showOverlay()
        startCountdown()
        return START_STICKY
    }

    fun showOverlay() {
        if (overlayView != null) return
        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        val type = if (Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            0, 0, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    or WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP

        // Build the overlay UI
        val inflater = LayoutInflater.from(this)
        overlayView = inflater.inflate(
            R.layout.overlay_strict_mode, null)

        // Set countdown text
        updateCountdownUI()

        wm.addView(overlayView, params)
    }

    fun startCountdown() {
        handler.post(object : Runnable {
            override fun run() {
                if (remainingSeconds <= 0) {
                    stopStrict()
                    stopSelf()
                    isRunning = false
                    return
                }
                val m = remainingSeconds / 60
                val s = remainingSeconds % 60
                overlayView
                    ?.findViewById<TextView>(R.id.tvOverlayTimer)
                    ?.text = "%02d:%02d".format(m, s)

                // Save remaining time for restore
                getSharedPreferences("miss_prefs", MODE_PRIVATE)
                    .edit()
                    .putInt("strict_remaining", remainingSeconds)
                    .apply()

                remainingSeconds--
                handler.postDelayed(this, 1000)
            }
        })
    }

    fun updateCountdownUI() {
        val m = remainingSeconds / 60
        val s = remainingSeconds % 60
        overlayView
            ?.findViewById<TextView>(R.id.tvOverlayTimer)
            ?.text = "%02d:%02d".format(m, s)
    }

    fun stopStrict() {
        handler.removeCallbacksAndMessages(null)

        // Re-enable notifications/DND
        val nm = getSystemService(NOTIFICATION_SERVICE)
                as NotificationManager
        if (nm.isNotificationPolicyAccessGranted) {
            nm.setInterruptionFilter(
                NotificationManager.INTERRUPTION_FILTER_ALL)
        }

        // Re-enable camera via DevicePolicyManager
        try {
            val dpm = getSystemService(DEVICE_POLICY_SERVICE)
                    as DevicePolicyManager
            val adminComp = ComponentName(
                this, MissDeviceAdmin::class.java)
            if (dpm.isAdminActive(adminComp)) {
                dpm.setCameraDisabled(adminComp, false)
            }
        } catch (e: Exception) {}

        // Clear prefs
        getSharedPreferences("miss_prefs", MODE_PRIVATE)
            .edit()
            .putBoolean("strict_active", false)
            .remove("strict_remaining")
            .apply()

        // Remove overlay
        overlayView?.let {
            try { wm.removeView(it) } catch (e: Exception) {}
            overlayView = null
        }

        // Vibrate 3 times — session done
        val vibrator = if (Build.VERSION.SDK_INT >= 31)
            getSystemService(VibratorManager::class.java)
                .defaultVibrator
        else
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator

        if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createWaveform(
                longArrayOf(0,200,100,200,100,200), -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0,200,100,200,100,200),-1)
        }
    }

    override fun onDestroy() {
        stopStrict()
        super.onDestroy()
    }
}
