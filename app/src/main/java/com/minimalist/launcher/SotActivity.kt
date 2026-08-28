package com.minimalist.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SotActivity : AppCompatActivity() {

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
