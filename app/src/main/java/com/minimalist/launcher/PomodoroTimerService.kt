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
    private var remainingSeconds = WORK_DURATION_SECONDS

    companion object {
        const val ACTION_START = "START"
        const val ACTION_STOP = "STOP"
        const val ACTION_SKIP_BREAK = "SKIP_BREAK"
        const val EXTRA_DURATION = "duration"
        const val BROADCAST_TICK = "com.minimalist.launcher.POMODORO_TICK"
        const val BROADCAST_PHASE_CHANGE = "com.minimalist.launcher.POMODORO_PHASE"
        const val BROADCAST_COMPLETE = "com.minimalist.launcher.POMODORO_COMPLETE"
        var isRunning = false

        const val WORK_DURATION_SECONDS = 25 * 60
        const val BREAK_DURATION_SECONDS = 5 * 60
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                isRunning = true
                val durationSecs = intent.getIntExtra(EXTRA_DURATION, PomodoroManager.selectedDurationMinutes * 60)
                remainingSeconds = if (durationSecs > 0) durationSecs else WORK_DURATION_SECONDS
                startForegroundNotification()
                startTicking()
            }
            ACTION_SKIP_BREAK -> {
                if (PomodoroManager.isBreakActive()) {
                    transitionToNextWorkSession()
                }
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
                broadcastTick()
                updateNotification(remainingSeconds, PomodoroManager.isWorkPhase)
                handler.postDelayed(this, 1000)
            } else {
                handlePhaseCompletion()
            }
        }
    }

    private fun handlePhaseCompletion() {
        if (PomodoroManager.isWorkPhase) {
            // 25 min work session completed
            if (PomodoroManager.currentSession < PomodoroManager.totalSessions) {
                // Start 5 min break
                PomodoroManager.startBreak(this@PomodoroTimerService)
                remainingSeconds = BREAK_DURATION_SECONDS
                broadcastPhaseChange()
                updateNotification(remainingSeconds, false)
                startTicking()
            } else {
                // Final session finished -> Pomodoro complete
                isRunning = false
                PomodoroManager.stop(this@PomodoroTimerService)
                val completeIntent = Intent(BROADCAST_COMPLETE).apply {
                    setPackage(packageName)
                }
                sendBroadcast(completeIntent)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        } else {
            // 5 min break completed -> start next 25 min work session
            transitionToNextWorkSession()
        }
    }

    private fun transitionToNextWorkSession() {
        PomodoroManager.resumeNextWorkSession(this@PomodoroTimerService)
        remainingSeconds = WORK_DURATION_SECONDS
        broadcastPhaseChange()
        updateNotification(remainingSeconds, true)
        startTicking()
    }

    private fun broadcastTick() {
        val tickIntent = Intent(BROADCAST_TICK).apply {
            setPackage(packageName)
            putExtra("remaining", remainingSeconds)
            putExtra("is_work", PomodoroManager.isWorkPhase)
            putExtra("session", PomodoroManager.currentSession)
            putExtra("total_sessions", PomodoroManager.totalSessions)
        }
        sendBroadcast(tickIntent)
    }

    private fun broadcastPhaseChange() {
        val phaseIntent = Intent(BROADCAST_PHASE_CHANGE).apply {
            setPackage(packageName)
            putExtra("is_work", PomodoroManager.isWorkPhase)
            putExtra("session", PomodoroManager.currentSession)
            putExtra("total_sessions", PomodoroManager.totalSessions)
            putExtra("remaining", remainingSeconds)
        }
        sendBroadcast(phaseIntent)
    }

    private fun startTicking() {
        handler.removeCallbacks(tickRunnable)
        broadcastTick()
        updateNotification(remainingSeconds, PomodoroManager.isWorkPhase)
        handler.postDelayed(tickRunnable, 1000)
    }

    private fun stopTicking() {
        handler.removeCallbacks(tickRunnable)
    }

    private fun startForegroundNotification() {
        createNotificationChannel()
        val notification = buildNotification(remainingSeconds, PomodoroManager.isWorkPhase)
        startForeground(2001, notification)
    }

    private fun buildNotification(secs: Int, isWork: Boolean): Notification {
        val m = secs / 60
        val s = secs % 60
        val title = if (isWork) {
            if (PomodoroManager.totalSessions > 1) {
                "Work Session ${PomodoroManager.currentSession} of ${PomodoroManager.totalSessions}"
            } else {
                "Focus Session"
            }
        } else {
            "Break Time"
        }
        val text = "%02d:%02d remaining".format(m, s)

        val intent = Intent(this, FocusActivity::class.java).apply {
            putExtra("tab", "pomodoro")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
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
