package com.minimalist.launcher

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat

class SotActivity : AppCompatActivity() {
    private var refreshGeneration = 0
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = Runnable { refreshUsage() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sot)

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        refreshUsage()
    }

    override fun onPause() {
        ++refreshGeneration
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    private fun refreshUsage() {
        val generation = ++refreshGeneration
        val value = findViewById<TextView>(R.id.tvSotValue)
        val description = findViewById<TextView>(R.id.tvSotDescription)
        val list = findViewById<LinearLayout>(R.id.layoutAppUsage)

        Thread {
            val result = runCatching {
                if (!UsageAccess.isGranted(this)) null else {
                    val usage = SOTManager.getTodayAppUsage(this)
                    usage.map { (pkg, ms) -> SOTManager.labelFor(packageManager, pkg) to ms }
                }
            }
            runOnUiThread {
                if (generation != refreshGeneration || isFinishing || isDestroyed) return@runOnUiThread
                val current = result.mapCatching { rows ->
                    if (UsageAccess.isGranted(this)) rows else null
                }
                val rows = current.getOrNull()
                description.setOnClickListener(null)
                when {
                    current.isFailure -> {
                        list.removeAllViews()
                        value.text = "Screen time unavailable"
                        description.text = "Could not load usage history. Retrying shortly."
                    }
                    rows == null -> {
                        list.removeAllViews()
                        value.text = "Usage access required"
                        description.text = "Tap here to grant usage access"
                        description.setOnClickListener {
                            runCatching { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                                .onFailure { description.text = "Open usage access in Android Settings" }
                        }
                    }
                    else -> {
                        value.text = SOTManager.format(rows.sumOf { it.second })
                        description.text = "screen time today"
                        renderList(list, rows)
                    }
                }
                handler.postDelayed(refresh, 30_000L)
            }
        }.start()
    }

    /** Text-only rows: app name on the left, duration on the right — same style as Settings. */
    private fun renderList(container: LinearLayout, rows: List<Pair<String, Long>>) {
        container.removeAllViews()
        val size = AppFont.get(this)
        val regular = ResourcesCompat.getFont(this, R.font.plus_jakarta_sans)
        rows.forEach { (name, ms) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, dp(14))
            }
            row.addView(label(name, "#999999", size, regular), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(label(SOTManager.format(ms), "#666666", size, regular))
            container.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
    }

    private fun label(text: String, color: String, sizeSp: Float, font: android.graphics.Typeface?) =
        TextView(this).apply {
            this.text = text
            setTextColor(android.graphics.Color.parseColor(color))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            typeface = font
        }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onBackPressed() {
        super.onBackPressed()
    }

    companion object {
        fun saveModeSession(context: Context, mode: String, minutes: Int) {
            val prefs = context.getSharedPreferences("mode_stats", Context.MODE_PRIVATE)
            val currentTotal = prefs.getInt(mode, 0)
            prefs.edit().putInt(mode, currentTotal + minutes).apply()
        }
    }
}
