package com.minimalist.launcher

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

object AppTimerManager {
    private var handler: Handler? = null
    private var isRunning = false

    fun start(context: Context) {
        if (isRunning) return
        isRunning = true
        handler = Handler(Looper.getMainLooper())
        
        val runnable = object : Runnable {
            override fun run() {
                checkLimits(context)
                handler?.postDelayed(this, 10000)
            }
        }
        handler?.post(runnable)
    }

    fun stop() {
        handler?.removeCallbacksAndMessages(null)
        isRunning = false
    }

    private fun checkLimits(context: Context) {
        val currentTime = System.currentTimeMillis()
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
        
        for (app in apps) {
            val packageName = app.packageName
            val limitMs = PrefsManager.getLimitMs(packageName)
            if (limitMs > 0) {
                val startTime = PrefsManager.getLimitStart(packageName)
                if (startTime > 0) {
                    val elapsed = currentTime - startTime
                    if (elapsed >= limitMs) {
                        PrefsManager.setLimitStart(packageName, 0)
                        
                        val i = Intent(context, MainActivity::class.java)
                        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        context.startActivity(i)
                    }
                }
            }
        }
    }
}
