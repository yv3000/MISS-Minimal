package com.minimalist.launcher

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat

class SotActivity : AppCompatActivity() {
    private var refreshGeneration = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sot)

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        val generation = ++refreshGeneration
        val value = findViewById<TextView>(R.id.tvSotValue)
        val description = findViewById<TextView>(R.id.tvSotDescription)
        val list = findViewById<LinearLayout>(R.id.layoutAppUsage)

        if (!UsageAccess.isGranted(this)) {
            list.removeAllViews()
            value.text = "Usage access required"
            description.text = "Tap here to grant usage access"
            description.setOnClickListener { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            return
        }

        Thread {
            val usage = SOTManager.getTodayAppUsage(this)
            val rows = usage.mapNotNull { (pkg, ms) ->
                SOTManager.labelFor(packageManager, pkg)?.let { it to ms }
            }
            val total = usage.values.sum()
            runOnUiThread {
                if (generation != refreshGeneration || !UsageAccess.isGranted(this)) return@runOnUiThread
                value.text = SOTManager.format(total)
                description.text = "screen time today"
                description.setOnClickListener(null)
                renderList(list, rows)
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
