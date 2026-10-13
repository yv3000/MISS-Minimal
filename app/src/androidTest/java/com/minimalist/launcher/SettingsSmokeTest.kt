package com.minimalist.launcher

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView

/** Run on a test device without Shizuku/root to check the permission-denied path. */
class SettingsSmokeTest : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val result = Bundle()
        try {
            val settingsStarts = java.util.concurrent.atomic.AtomicInteger()
            val settings = object : ActivityMonitor() {
                override fun onStartActivity(intent: Intent): ActivityResult? {
                    if (intent.action == Settings.ACTION_WIFI_SETTINGS || intent.action == Settings.Panel.ACTION_WIFI) {
                        settingsStarts.incrementAndGet()
                    }
                    return null
                }
            }
            addMonitor(settings)
            repeat(2) {
                for (screen in listOf(QuickSettingsActivity::class.java, LauncherSettingsActivity::class.java)) {
                    val activity = startActivitySync(Intent(targetContext, screen).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    try {
                        waitForIdleSync()
                        Thread.sleep(1_000)
                        val previousSettingsHits = settingsStarts.get()
                        runOnMainSync { activity.findViewById<android.view.View>(R.id.btnWifi).performClick() }
                        Thread.sleep(10_000)
                        waitForIdleSync()
                        check(!activity.isFinishing && !activity.isDestroyed) { "${screen.simpleName} closed unexpectedly" }
                        check(settingsStarts.get() == previousSettingsHits) { "A radio tap opened system Wi-Fi settings" }
                    } finally {
                        runOnMainSync { activity.finish() }
                        waitForIdleSync()
                    }
                }
                val sot = startActivitySync(Intent(targetContext, SotActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                try {
                    Thread.sleep(3_000)
                    runOnMainSync {
                        val text = sot.findViewById<TextView>(R.id.tvSotValue).text.toString()
                        check(text.isNotBlank()) { "SOT did not render" }
                        if (!UsageAccess.isGranted(targetContext)) {
                            check(text == "Usage access required") { "Denied usage access was shown as $text" }
                        }
                    }
                } finally {
                    runOnMainSync { sot.finish() }
                }
            }
            removeMonitor(settings)
            result.putString("stream", "Settings and SOT smoke checks passed twice.\n")
            finish(Activity.RESULT_OK, result)
        } catch (error: Throwable) {
            result.putString("stream", error.stackTraceToString())
            finish(Activity.RESULT_CANCELED, result)
        }
    }
}
