package com.minimalist.launcher

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.minimalist.launcher.databinding.ActivityFocusBinding

class FocusActivity : AppCompatActivity() {
    private lateinit var binding: ActivityFocusBinding
    private val REQUEST_DEVICE_ADMIN = 2001
    private var pendingStrictStart = false
    private var seconds = 0
    private val handler = Handler(Looper.getMainLooper())
    private val runnable = object : Runnable {
        override fun run() {
            seconds++
            val m = seconds / 60
            val s = seconds % 60
            binding.tvTimer.text = String.format("%02d:%02d", m, s)
            handler.postDelayed(this, 1000)
        }
    }

    private var timeRemainingMs: Long = 0
    private var timerRunning = false
    private var timerPaused = false
    
    private var timerHours = 0
    private var timerMinutes = 0
    private var timerSeconds = 0
    private var remainingSeconds = 0
    
    private val blinkHandler = Handler(Looper.getMainLooper())
    private var blinkState = false
    private val blinkRunnable = object : Runnable {
        override fun run() {
            blinkState = !blinkState
            binding.tvTimeUp.visibility = if (blinkState) View.VISIBLE else View.INVISIBLE
            blinkHandler.postDelayed(this, 1000)
        }
    }

    private var strictRemainingSeconds = 0L
    private val strictHandler = Handler(Looper.getMainLooper())
    private val strictRunnable = object : Runnable {
        override fun run() {
            if (strictRemainingSeconds <= 0) {
                StrictModeManager(this@FocusActivity).endStrictMode {
                    binding.tvStrictCountdown.text = "DONE"
                    binding.tvStrictStatus.text = "strict mode ended"
                }
                return
            }
            strictRemainingSeconds--
            val m = strictRemainingSeconds / 60
            val s = strictRemainingSeconds % 60
            binding.tvStrictCountdown.text = String.format("%02d:%02d", m, s)
            strictHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFocusBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Tabs
        binding.tabStopwatch.setOnClickListener {
            binding.layoutStopwatch.visibility = View.VISIBLE
            binding.layoutTimer.visibility = View.GONE
            binding.layoutStrict.visibility = View.GONE
            binding.tabStopwatch.setTextColor(resources.getColor(R.color.white, null))
            binding.tabTimer.setTextColor(resources.getColor(R.color.grey_555, null))
            binding.tabStrict.setTextColor(resources.getColor(R.color.grey_555, null))
        }

        binding.tabTimer.setOnClickListener {
            binding.layoutStopwatch.visibility = View.GONE
            binding.layoutTimer.visibility = View.VISIBLE
            binding.layoutStrict.visibility = View.GONE
            binding.tabStopwatch.setTextColor(resources.getColor(R.color.grey_555, null))
            binding.tabTimer.setTextColor(resources.getColor(R.color.white, null))
            binding.tabStrict.setTextColor(resources.getColor(R.color.grey_555, null))
        }

        binding.tabStrict.setOnClickListener {
            binding.layoutStopwatch.visibility = View.GONE
            binding.layoutTimer.visibility = View.GONE
            binding.layoutStrict.visibility = View.VISIBLE
            binding.tabStopwatch.setTextColor(resources.getColor(R.color.grey_555, null))
            binding.tabTimer.setTextColor(resources.getColor(R.color.grey_555, null))
            binding.tabStrict.setTextColor(resources.getColor(R.color.white, null))

            binding.boxStrictWarning.visibility = View.VISIBLE
            binding.btnEnableStrict.visibility = View.VISIBLE
            binding.tvStrictCountdown.visibility = View.GONE
        }

        // Stopwatch actions
        binding.btnStopwatchStart.setOnClickListener {
            handler.post(runnable)
            binding.btnStopwatchStart.visibility = View.GONE
            binding.btnStopwatchPause.visibility = View.VISIBLE
            binding.btnStopwatchResume.visibility = View.GONE
            binding.btnStopwatchStop.visibility = View.VISIBLE
        }
        binding.btnStopwatchPause.setOnClickListener {
            handler.removeCallbacks(runnable)
            binding.btnStopwatchPause.visibility = View.GONE
            binding.btnStopwatchResume.visibility = View.VISIBLE
        }
        binding.btnStopwatchResume.setOnClickListener {
            handler.post(runnable)
            binding.btnStopwatchResume.visibility = View.GONE
            binding.btnStopwatchPause.visibility = View.VISIBLE
        }
        binding.btnStopwatchStop.setOnClickListener {
            handler.removeCallbacks(runnable)
            seconds = 0
            binding.tvTimer.text = "00:00"
            binding.btnStopwatchStart.visibility = View.VISIBLE
            binding.btnStopwatchPause.visibility = View.GONE
            binding.btnStopwatchResume.visibility = View.GONE
            binding.btnStopwatchStop.visibility = View.GONE
        }

        // Timer actions

        binding.pickerHours.minValue = 0
        binding.pickerHours.maxValue = 99
        binding.pickerHours.wrapSelectorWheel = true
        
        binding.pickerMinutes.minValue = 0
        binding.pickerMinutes.maxValue = 59
        binding.pickerMinutes.wrapSelectorWheel = true
        
        binding.pickerSeconds.minValue = 0
        binding.pickerSeconds.maxValue = 59
        binding.pickerSeconds.wrapSelectorWheel = true

        styleNumberPicker(binding.pickerHours)
        styleNumberPicker(binding.pickerMinutes)
        styleNumberPicker(binding.pickerSeconds)

        binding.pickerHours.setOnValueChangedListener { _, _, newVal -> timerHours = newVal }
        binding.pickerMinutes.setOnValueChangedListener { _, _, newVal -> timerMinutes = newVal }
        binding.pickerSeconds.setOnValueChangedListener { _, _, newVal -> timerSeconds = newVal }

        binding.btnTimerStart.setOnClickListener {
            timerHours = binding.pickerHours.value
            timerMinutes = binding.pickerMinutes.value
            timerSeconds = binding.pickerSeconds.value
            
            val totalSeconds = (timerHours * 3600) + (timerMinutes * 60) + timerSeconds
            
            if (totalSeconds <= 0) {
                android.widget.Toast.makeText(this, "Set a time first", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startTimer(totalSeconds)
        }
        binding.btnTimerPause.setOnClickListener { pauseTimer() }
        binding.btnTimerResume.setOnClickListener { resumeTimer() }
        binding.btnTimerStop.setOnClickListener { resetTimer() }
        binding.btnTimerReset.setOnClickListener { resetTimer() }

        // Strict mode actions
        binding.btnEnableStrict.setOnClickListener {
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
            val adminComponent = android.content.ComponentName(this, MissDeviceAdmin::class.java)

            if (!dpm.isAdminActive(adminComponent)) {
                // Not admin yet - request it
                pendingStrictStart = true
                val intent = Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                intent.putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                intent.putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION, "MISS Minimal needs this to block camera and distractions during Strict Mode.")
                startActivityForResult(intent, REQUEST_DEVICE_ADMIN)
                return@setOnClickListener
            }
            // Already admin - show confirmation dialog
            showStrictConfirmDialog()
        }
    }

    private fun showStrictConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("Are you sure?")
            .setMessage("Calls, messages and all notifications will be silenced for 25 minutes.\nThis cannot be cancelled.")
            .setPositiveButton("YES, BLOCK EVERYTHING") { _, _ ->
                startStrictMode()
            }
            .setNegativeButton("Cancel", null)
            .show()
            .window?.setBackgroundDrawableResource(android.R.color.black)
    }

    fun startStrictMode() {
        // Check DND permission
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) {
            startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            android.widget.Toast.makeText(this,
                "Grant DND access, then enable Strict Mode again",
                android.widget.Toast.LENGTH_LONG).show()
            return
        }

        // Check Device Admin
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
        val adminComp = android.content.ComponentName(this, MissDeviceAdmin::class.java)
        if (!dpm.isAdminActive(adminComp)) {
            pendingStrictStart = true
            val intent = Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComp)
            intent.putExtra(
                android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "MISS Minimal needs Device Admin to block camera and distractions during Strict Mode.")
            startActivityForResult(intent, REQUEST_DEVICE_ADMIN)
            return
        }

        // Check overlay permission
        if (android.os.Build.VERSION.SDK_INT >= 23 && !android.provider.Settings.canDrawOverlays(this)) {
            startActivityForResult(
                Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:$packageName")), 3001)
            android.widget.Toast.makeText(this,
                "Grant overlay permission, then enable again",
                android.widget.Toast.LENGTH_LONG).show()
            return
        }

        // All permissions OK — enable DND
        nm.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_NONE)

        // Disable camera
        if (dpm.isAdminActive(adminComp)) {
            dpm.setCameraDisabled(adminComp, true)
        }

        // Save state
        getSharedPreferences("miss_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("strict_active", true)
            .apply()

        // Start overlay service
        val svc = Intent(this, StrictModeOverlayService::class.java)
        svc.putExtra("seconds", 25 * 60)
        startService(svc)

        // Close FocusActivity — overlay takes over
        finish()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_DEVICE_ADMIN) {
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
            val adminComponent = android.content.ComponentName(this, MissDeviceAdmin::class.java)
            if (dpm.isAdminActive(adminComponent)) {
                if (pendingStrictStart) {
                    pendingStrictStart = false
                    showStrictConfirmDialog()
                }
            } else {
                pendingStrictStart = false
                android.widget.Toast.makeText(this, "Device Admin required for Strict Mode", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startStrictCountdown(seconds: Long) {
        binding.boxStrictWarning.visibility = View.GONE
        binding.btnEnableStrict.visibility = View.GONE
        binding.tvStrictCountdown.visibility = View.VISIBLE
        binding.tvStrictStatus.text = "all distractions blocked"
        
        strictRemainingSeconds = seconds
        strictHandler.removeCallbacks(strictRunnable)
        strictHandler.post(strictRunnable)
    }

    private fun styleNumberPicker(picker: android.widget.NumberPicker) {
        try {
            val f = android.widget.NumberPicker::class.java.getDeclaredField("mSelectorWheelPaint")
            f.isAccessible = true
            (f.get(picker) as android.graphics.Paint).color = android.graphics.Color.WHITE
            picker.invalidate()
        } catch (e: Exception) {}
        
        try {
            val dividerField = android.widget.NumberPicker::class.java.getDeclaredField("mSelectionDivider")
            dividerField.isAccessible = true
            dividerField.set(picker, android.graphics.drawable.ColorDrawable(android.graphics.Color.parseColor("#222222")))
        } catch (e: Exception) {}
    }

    private fun startTimer(totalSecs: Int) {
        remainingSeconds = totalSecs
        timerRunning = true
        timerPaused = false
        binding.layoutTimerInput.visibility = View.GONE
        binding.tvTimerDisplay.visibility = View.VISIBLE
        binding.btnTimerStart.visibility = View.GONE
        binding.btnTimerPause.visibility = View.VISIBLE
        binding.btnTimerStop.visibility = View.VISIBLE
        runTimer()
    }

    private fun runTimer() {
        handler.post(object : Runnable {
            override fun run() {
                if (!timerRunning || timerPaused) return
                if (remainingSeconds <= 0) {
                    onTimerFinished()
                    return
                }
                val h = remainingSeconds / 3600
                val m = (remainingSeconds % 3600) / 60
                val s = remainingSeconds % 60
                binding.tvTimerDisplay.text = String.format("%02d:%02d:%02d", h, m, s)
                remainingSeconds--
                handler.postDelayed(this, 1000)
            }
        })
    }

    private fun pauseTimer() {
        if (timerRunning) {
            timerPaused = true
            binding.btnTimerPause.visibility = View.GONE
            binding.btnTimerResume.visibility = View.VISIBLE
        }
    }

    private fun resumeTimer() {
        if (timerPaused) {
            timerPaused = false
            binding.btnTimerResume.visibility = View.GONE
            binding.btnTimerPause.visibility = View.VISIBLE
            runTimer()
        }
    }

    private fun resetTimer() {
        timerRunning = false
        timerPaused = false
        remainingSeconds = 0
        blinkHandler.removeCallbacks(blinkRunnable)
        binding.tvTimeUp.visibility = View.GONE
        binding.tvTimerDisplay.visibility = View.GONE
        binding.layoutTimerInput.visibility = View.VISIBLE
        binding.btnTimerStart.visibility = View.VISIBLE
        binding.btnTimerPause.visibility = View.GONE
        binding.btnTimerResume.visibility = View.GONE
        binding.btnTimerStop.visibility = View.GONE
        binding.btnTimerReset.visibility = View.GONE
        
        binding.pickerHours.value = 0
        binding.pickerMinutes.value = 0
        binding.pickerSeconds.value = 0
        timerHours = 0
        timerMinutes = 0
        timerSeconds = 0
    }

    private fun onTimerFinished() {
        timerRunning = false
        binding.tvTimerDisplay.text = "00:00:00"
        
        val vibrator = if (android.os.Build.VERSION.SDK_INT >= 31) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        val pattern = longArrayOf(0, 300, 200, 300, 200, 300, 200, 300, 200, 300)
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(android.os.VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
        
        binding.tvTimeUp.visibility = View.VISIBLE
        blinkState = true
        blinkHandler.postDelayed(blinkRunnable, 1000)
        
        binding.btnTimerReset.visibility = View.VISIBLE
        binding.btnTimerPause.visibility = View.GONE
        binding.btnTimerStop.visibility = View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        blinkHandler.removeCallbacks(blinkRunnable)
        strictHandler.removeCallbacks(strictRunnable)
        timerRunning = false
    }

    override fun onBackPressed() {
        if (StrictModeOverlayService.isRunning) {
            // Cannot go back during strict mode
            return
        }
        super.onBackPressed()
        overridePendingTransition(0, R.anim.slide_down_exit)
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, R.anim.slide_down_exit)
    }

    override fun onResume() {
        super.onResume()
        applyFontSize()
        binding.boxStrictWarning.visibility = View.VISIBLE
        binding.btnEnableStrict.visibility = View.VISIBLE
        binding.tvStrictCountdown.visibility = View.GONE
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
