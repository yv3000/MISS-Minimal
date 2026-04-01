package com.minimalist.launcher

import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.widget.Toast

class StrictModeManager(private val context: Context) {

    private val packagesToSuspend = arrayOf(
        "com.vivo.easyaccess",
        "com.iqoo.easyaccess",
        "com.bbk.easyaccess",
        "com.android.systemui"
    )

    private val adminComponent = ComponentName(context, MissDeviceAdmin::class.java)
    private val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val prefs = context.getSharedPreferences("miss_prefs", Context.MODE_PRIVATE)

    fun startStrictMode(): Boolean {
        if (nm.isNotificationPolicyAccessGranted) {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
        } else {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
            Toast.makeText(context, "Grant DND permission, then try again", Toast.LENGTH_LONG).show()
            return false
        }

        if (dpm.isAdminActive(adminComponent)) {
            try { dpm.setCameraDisabled(adminComponent, true) } catch (e: Exception) {}
            try { dpm.setPackagesSuspended(adminComponent, packagesToSuspend, true) } catch (e: Exception) {}
        } else {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Required for Strict Mode to disable camera")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            Toast.makeText(context, "Grant Admin permission, then try again", Toast.LENGTH_LONG).show()
            return false
        }

        prefs.edit()
            .putBoolean("strict_active", true)
            .putLong("strict_end", System.currentTimeMillis() + 25 * 60 * 1000)
            .apply()

        return true
    }

    fun endStrictMode(onDone: () -> Unit) {
        try {
            if (nm.isNotificationPolicyAccessGranted) {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            }
        } catch (e: Exception) {}

        if (dpm.isAdminActive(adminComponent)) {
            try { dpm.setCameraDisabled(adminComponent, false) } catch (e: Exception) {}
            try { dpm.setPackagesSuspended(adminComponent, packagesToSuspend, false) } catch (e: Exception) {}
        }

        prefs.edit()
            .putBoolean("strict_active", false)
            .remove("strict_end")
            .apply()

        val vibrator = if (android.os.Build.VERSION.SDK_INT >= 31) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        
        val pattern = longArrayOf(0, 200, 100, 200, 100, 200)
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
        
        onDone()
    }
}
