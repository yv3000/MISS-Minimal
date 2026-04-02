package com.minimalist.launcher

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class PermissionOnboardingActivity : AppCompatActivity() {

    private lateinit var rowAccessibility: View
    private lateinit var rowUsage: View
    private lateinit var rowOverlay: View
    private lateinit var rowDnd: View
    private lateinit var rowBattery: View
    private lateinit var btnContinue: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission_onboarding)

        rowAccessibility = findViewById(R.id.rowAccessibility)
        rowUsage = findViewById(R.id.rowUsage)
        rowOverlay = findViewById(R.id.rowOverlay)
        rowDnd = findViewById(R.id.rowDnd)
        rowBattery = findViewById(R.id.rowBattery)
        btnContinue = findViewById(R.id.btnContinue)

        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        refreshAllPermissions()
    }

    private fun setupClickListeners() {
        rowAccessibility.findViewById<View>(R.id.btnAllowAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        rowUsage.findViewById<View>(R.id.btnAllowUsage).setOnClickListener {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        }

        rowOverlay.findViewById<View>(R.id.btnAllowOverlay).setOnClickListener {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
        }

        rowDnd.findViewById<View>(R.id.btnAllowDnd).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }

        rowBattery.findViewById<View>(R.id.btnAllowBattery).setOnClickListener {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            startActivity(intent)
        }

        btnContinue.setOnClickListener {
            val prefs = getSharedPreferences("strict_prefs", MODE_PRIVATE)
            prefs.edit()
                .putBoolean("start_immediately", true)
                .apply()
            finish()
        }
    }

    fun refreshAllPermissions() {
        updateRow(rowAccessibility, R.id.btnAllowAccessibility, R.id.tvStatusAccessibility, isAccessibilityServiceEnabled())
        updateRow(rowUsage, R.id.btnAllowUsage, R.id.tvStatusUsage, isUsageStatsGranted())
        updateRow(rowOverlay, R.id.btnAllowOverlay, R.id.tvStatusOverlay, Settings.canDrawOverlays(this))
        updateRow(rowDnd, R.id.btnAllowDnd, R.id.tvStatusDnd, isNotificationPolicyGranted())
        updateRow(rowBattery, R.id.btnAllowBattery, R.id.tvStatusBattery, isBatteryOptimizationIgnored())

        val allGranted = isAccessibilityServiceEnabled() &&
                isUsageStatsGranted() &&
                Settings.canDrawOverlays(this) &&
                isNotificationPolicyGranted() &&
                isBatteryOptimizationIgnored()

        btnContinue.isEnabled = allGranted
        btnContinue.alpha = if (allGranted) 1f else 0.3f
    }

    fun updateRow(row: View, btnId: Int, statusId: Int, isGranted: Boolean) {
        val btnAllow = row.findViewById<Button>(btnId)
        val tvStatus = row.findViewById<TextView>(statusId)
        if (isGranted) {
            btnAllow.visibility = View.GONE
            tvStatus.visibility = View.VISIBLE
            tvStatus.text = "✓ Granted"
            tvStatus.setTextColor(Color.parseColor("#4CAF50"))
        } else {
            btnAllow.visibility = View.VISIBLE
            tvStatus.visibility = View.GONE
        }
    }

    fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponent = ComponentName(this, StrictModeService::class.java)
        val expectedString = expectedComponent.flattenToString()

        // Method 1: check enabled services string
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""

        if (enabledServices.split(":")
                .any { it.equals(expectedString, ignoreCase = true) }) {
            return true
        }

        // Method 2: check via AccessibilityManager
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledList = am.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )

        return enabledList.any {
            it.resolveInfo.serviceInfo.packageName == packageName &&
                    it.resolveInfo.serviceInfo.name == StrictModeService::class.java.name
        }
    }

    private fun isUsageStatsGranted(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun isNotificationPolicyGranted(): Boolean {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        return nm.isNotificationPolicyAccessGranted
    }

    private fun isBatteryOptimizationIgnored(): Boolean {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm.isIgnoringBatteryOptimizations(packageName)
        } else {
            true
        }
    }
}
