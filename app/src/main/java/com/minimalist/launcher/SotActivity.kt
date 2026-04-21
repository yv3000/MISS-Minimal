package com.minimalist.launcher

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashMap

class SotActivity : AppCompatActivity() {

    private lateinit var tvDate: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var layoutModes: LinearLayout
    private lateinit var layoutApps: LinearLayout
    private lateinit var tvNoSessions: TextView
    private lateinit var btnRefresh: TextView
    private lateinit var btnGrantUsage: TextView

    data class AppUsageItem(
        val packageName: String,
        val appName: String,
        val totalMs: Long
    )

    companion object {
        fun saveModeSession(context: Context, mode: String, durationMinutes: Int) {
            val prefs = context.getSharedPreferences("sot_prefs", Context.MODE_PRIVATE)
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val lastDate = prefs.getString("sot_last_reset_date", "")

            if (lastDate != today) {
                prefs.edit().clear()
                    .putString("sot_last_reset_date", today)
                    .apply()
            }

            val sessionKey = "${mode}_sessions_today"
            val minuteKey = "${mode}_minutes_today"
            val sessions = prefs.getInt(sessionKey, 0) + 1
            val minutes = prefs.getInt(minuteKey, 0) + durationMinutes

            prefs.edit()
                .putInt(sessionKey, sessions)
                .putInt(minuteKey, minutes)
                .apply()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sot)

        tvDate = findViewById(R.id.tvDate)
        tvTotalTime = findViewById(R.id.tvTotalTime)
        layoutModes = findViewById(R.id.layoutModes)
        layoutApps = findViewById(R.id.layoutApps)
        tvNoSessions = findViewById(R.id.tvNoSessions)
        btnRefresh = findViewById(R.id.btnRefresh)
        btnGrantUsage = findViewById(R.id.btnGrantUsage)

        btnRefresh.setOnClickListener {
            loadData()
        }

        btnGrantUsage.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        val dateFormat = SimpleDateFormat("EEE · dd MMM", Locale.getDefault())
        tvDate.text = dateFormat.format(Date()).uppercase()
    }

    override fun onResume() {
        super.onResume()
        AppFont.applyToActivity(this)
        loadData()
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

    private fun loadData() {
        if (!isUsageStatsGranted()) {
            btnGrantUsage.visibility = View.VISIBLE
            return
        }
        btnGrantUsage.visibility = View.GONE

        // Load mode stats
        loadModeStats()

        // Load app usage
        val usageList = getTodayAppUsage()
        val totalMs = usageList.sumOf { it.totalMs }
        
        tvTotalTime.text = formatTimeFriendly(totalMs)
        
        layoutApps.removeAllViews()
        for (item in usageList) {
            val proportion = if (totalMs > 0) item.totalMs.toFloat() / totalMs else 0f
            layoutApps.addView(createAppRow(item, proportion))
            layoutApps.addView(createDivider())
        }
    }

    private fun loadModeStats() {
        val prefs = getSharedPreferences("sot_prefs", Context.MODE_PRIVATE)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val lastDate = prefs.getString("sot_last_reset_date", "")

        if (lastDate != today) {
            prefs.edit().clear()
                .putString("sot_last_reset_date", today)
                .apply()
        }

        layoutModes.removeAllViews()
        var hasAny = false

        fun addModeRow(name: String, key: String) {
            val sessions = prefs.getInt("${key}_sessions_today", 0)
            val minutes = prefs.getInt("${key}_minutes_today", 0)
            if (sessions > 0) {
                hasAny = true
                layoutModes.addView(createModeRow(name, sessions, minutes))
                layoutModes.addView(createDivider())
            }
        }

        addModeRow("Strict Mode", "strict")
        addModeRow("Pomodoro", "pomodoro")
        addModeRow("Focus Stopwatch", "stopwatch")
        addModeRow("Focus Timer", "timer")

        if (!hasAny) {
            tvNoSessions.visibility = View.VISIBLE
            layoutModes.addView(tvNoSessions)
        } else {
            tvNoSessions.visibility = View.GONE
        }
    }

    private fun createModeRow(name: String, sessions: Int, minutes: Int): View {
        val container = LinearLayout(this)
        container.orientation = LinearLayout.HORIZONTAL
        container.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        container.setPadding(0, 16, 0, 16)

        val tvName = TextView(this)
        tvName.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        tvName.text = name
        tvName.setTextColor(android.graphics.Color.parseColor("#aaaaaa"))
        tvName.textSize = 11f
        tvName.typeface = android.graphics.Typeface.MONOSPACE

        val sTxt = if (sessions == 1) "1 session" else "$sessions sessions"
        
        val tvDuration = TextView(this)
        tvDuration.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        tvDuration.text = "$sTxt · ${minutes}m"
        tvDuration.setTextColor(android.graphics.Color.parseColor("#555555"))
        tvDuration.textSize = 11f
        tvDuration.typeface = android.graphics.Typeface.MONOSPACE

        container.addView(tvName)
        container.addView(tvDuration)
        
        val size = AppFont.get(this)
        AppFont.applyToAllTextViews(container, size)
        return container
    }

    private fun createAppRow(item: AppUsageItem, proportion: Float): View {
        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        container.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        container.setPadding(0, 16, 0, 16)

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        val tvName = TextView(this)
        tvName.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        tvName.text = item.appName
        tvName.setTextColor(android.graphics.Color.parseColor("#bbbbbb"))
        tvName.textSize = 11f
        tvName.typeface = android.graphics.Typeface.MONOSPACE

        val tvTime = TextView(this)
        tvTime.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        tvTime.text = formatTime(item.totalMs)
        tvTime.setTextColor(android.graphics.Color.parseColor("#555555"))
        tvTime.textSize = 11f
        tvTime.typeface = android.graphics.Typeface.MONOSPACE
        tvTime.gravity = Gravity.END

        row.addView(tvName)
        row.addView(tvTime)
        
        // Progress bar container to enforce max width sizing
        val barContainer = LinearLayout(this)
        barContainer.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (1 * resources.displayMetrics.density).toInt()
        )
        (barContainer.layoutParams as LinearLayout.LayoutParams).topMargin = (6 * resources.displayMetrics.density).toInt()
        barContainer.orientation = LinearLayout.HORIZONTAL
        
        val bar = View(this)
        val pWeight = if (proportion > 0) proportion else 0.001f
        bar.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, pWeight)
        bar.setBackgroundColor(android.graphics.Color.parseColor("#333333"))
        
        val emptySpace = View(this)
        emptySpace.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f - pWeight)
        
        barContainer.addView(bar)
        barContainer.addView(emptySpace)

        container.addView(row)
        container.addView(barContainer)

        val size = AppFont.get(this)
        AppFont.applyToAllTextViews(container, size)
        return container
    }

    private fun createDivider(): View {
        val divider = View(this)
        divider.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            1 // roughly 0.5px depending on density, using 1px 
        )
        divider.setBackgroundColor(android.graphics.Color.parseColor("#0d0d0d"))
        return divider
    }

    private fun getTodayAppUsage(): List<AppUsageItem> {
        val usm = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager

        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfDay = cal.timeInMillis

        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startOfDay,
            System.currentTimeMillis()
        )

        if (stats == null || stats.isEmpty()) return emptyList()

        val merged = HashMap<String, Long>()
        for (stat in stats) {
            val pkg = stat.packageName
            val time = stat.totalTimeInForeground
            if (time > 0) {
                merged[pkg] = (merged[pkg] ?: 0L) + time
            }
        }

        return merged.entries
            .filter { it.value > 60_000L } // min 1 min
            .filter { hasLaunchIntent(it.key) }
            .sortedByDescending { it.value }
            .take(15) // top 15
            .map {
                AppUsageItem(it.key, getAppName(it.key), it.value)
            }
    }

    private fun hasLaunchIntent(pkg: String): Boolean {
        return try {
            packageManager.getLaunchIntentForPackage(pkg) != null
        } catch (e: Exception) { false }
    }

    private fun getAppName(pkg: String): String {
        return try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(pkg, 0)
            ).toString()
        } catch (e: Exception) { pkg }
    }

    private fun formatTime(ms: Long): String {
        val totalMins = ms / 60_000
        val h = totalMins / 60
        val m = totalMins % 60
        return when {
            h > 0 -> "${h}h ${m}m"
            m > 0 -> "${m}m"
            else -> "<1m"
        }
    }
    
    private fun formatTimeFriendly(ms: Long): String {
        val totalMins = ms / 60_000
        val h = totalMins / 60
        val m = totalMins % 60
        return "${h}h ${m}m"
    }
}
