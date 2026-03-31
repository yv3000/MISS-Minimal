package com.minimalist.launcher

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.minimalist.launcher.databinding.ActivityTimeLimitBinding

class TimeLimitActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTimeLimitBinding
    private var packageName: String? = null
    private var appName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTimeLimitBinding.inflate(layoutInflater)
        setContentView(binding.root)

        packageName = intent.getStringExtra("packageName")
        appName = intent.getStringExtra("appName")

        binding.tvAppName.text = appName ?: "App"

        binding.btn1m.setOnClickListener { saveLimitAndLaunch(1) }
        binding.btn5m.setOnClickListener { saveLimitAndLaunch(5) }
        binding.btn10m.setOnClickListener { saveLimitAndLaunch(10) }
        binding.btn15m.setOnClickListener { saveLimitAndLaunch(15) }

        binding.btnCustom.setOnClickListener {
            val input = EditText(this)
            input.inputType = android.text.InputType.TYPE_CLASS_NUMBER
            AlertDialog.Builder(this)
                .setTitle("Custom limit (minutes)")
                .setView(input)
                .setPositiveButton("Set") { _, _ ->
                    val min = input.text.toString().toIntOrNull()
                    if (min != null && min > 0) {
                        saveLimitAndLaunch(min)
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun saveLimitAndLaunch(minutes: Int) {
        packageName?.let {
            val ms = minutes * 60000L
            PrefsManager.setLimitMs(it, ms)
            PrefsManager.setLimitStart(it, System.currentTimeMillis())
            
            val launchIntent = packageManager.getLaunchIntentForPackage(it)
            if (launchIntent != null) {
                startActivity(launchIntent)
            }
        }
        finish()
    }

    override fun onResume() {
        super.onResume()
        applyFontSize()
    }

    fun applyFontSize() {
        val size = AppFont.get(this)
        applyToAllTextViews(window.decorView, size)
    }

    fun applyToAllTextViews(view: android.view.View, size: Float) {
        if (view is android.widget.TextView) {
            if (view.tag == "fixed_size") return
            view.textSize = size
        } else if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                applyToAllTextViews(view.getChildAt(i), size)
            }
        }
    }
}
