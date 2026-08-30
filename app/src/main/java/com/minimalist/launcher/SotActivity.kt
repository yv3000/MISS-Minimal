package com.minimalist.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SotActivity : AppCompatActivity() {
    private var refreshGeneration = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sot)

        findViewById<TextView>(R.id.btnBack).setOnClickListener {
            finish()
        }

        findViewById<TextView>(R.id.tvGithubLink).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/yv3000"))
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        AppFont.applyToActivity(this)
        val generation = ++refreshGeneration
        val value = findViewById<TextView>(R.id.tvSotValue)
        val description = findViewById<TextView>(R.id.tvSotDescription)
        if (!UsageAccess.isGranted(this)) {
            value.text = "Usage access required"
            description.text = "Tap here to grant usage access"
            description.setOnClickListener { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            return
        }
        Thread {
            val minutes = SOTManager.getScreenOnTimeToday(this) / 60_000
            runOnUiThread {
                if (generation != refreshGeneration || !UsageAccess.isGranted(this)) return@runOnUiThread
                value.text = if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
                description.text = "screen time today"
                description.setOnClickListener(null)
            }
        }.start()
    }

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
