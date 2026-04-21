package com.minimalist.launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class PomodoroTimerService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var remainingSeconds = 0
    private var isWorkPhase = true

    companion object {
        const val ACTION_START = "START"
        const val ACTION_STOP = "STOP"
        const val EXTRA_DURATION = "duration_seconds"
        const val BROADCAST_TICK = "com.minimalist.launcher.POMODORO_TICK"
        const val BROADCAST_PHASE_CHANGE = "com.minimalist.launcher.POMODORO_PHASE"
        var isRunning = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                remainingSeconds = intent.getIntExtra(EXTRA_DURATION, 25 * 60)
                isWorkPhase = true
                isRunning = true
                startForegroundNotification()
                startTicking()
            }
            ACTION_STOP -> {
                stopTicking()
                isRunning = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (remainingSeconds > 0) {
                remainingSeconds--
                // Broadcast tick to PomodoroActivity
                val tickIntent = Intent(BROADCAST_TICK)
                tickIntent.putExtra("remaining", remainingSeconds)
                tickIntent.putExtra("is_work", isWorkPhase)
                sendBroadcast(tickIntent)
                // Update notification
                updateNotification(remainingSeconds, isWorkPhase)
                handler.postDelayed(this, 1000)
            } else {
                // Phase complete
                if (isWorkPhase) {
                    isWorkPhase = false
                    remainingSeconds = 5 * 60 // 5 min break
                } else {
                    isWorkPhase = true
                    PomodoroManager.sessionCount++
                    remainingSeconds = PomodoroManager.workDurationSeconds
                }
                val phaseIntent = Intent(BROADCAST_PHASE_CHANGE)
                phaseIntent.putExtra("is_work", isWorkPhase)
                phaseIntent.putExtra("session", PomodoroManager.sessionCount)
                sendBroadcast(phaseIntent)
                handler.post(this)
            }
        }
    }

    private fun startTicking() {
        handler.removeCallbacks(tickRunnable)
        handler.post(tickRunnable)
    }

    private fun stopTicking() {
        handler.removeCallbacks(tickRunnable)
    }

    private fun startForegroundNotification() {
        createNotificationChannel()
        val notification = buildNotification(remainingSeconds, isWorkPhase)
        startForeground(2001, notification)
    }

    private fun buildNotification(secs: Int, isWork: Boolean): Notification {
        val m = secs / 60
        val s = secs % 60
        val title = if (isWork) "Focus Session" else "Break"
        val text = "%02d:%02d remaining".format(m, s)

        val notifIntent = Intent(this, PomodoroActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notifIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, "pomodoro")
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(secs: Int, isWork: Boolean) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(2001, buildNotification(secs, isWork))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                "pomodoro", "Pomodoro Timer",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopTicking()
        isRunning = false
        super.onDestroy()
    }
}
