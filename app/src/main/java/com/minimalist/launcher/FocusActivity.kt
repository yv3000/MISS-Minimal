package com.minimalist.launcher

import android.app.AlertDialog
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class FocusActivity : AppCompatActivity() {

  // Handlers
  private val handler = Handler(Looper.getMainLooper())

  // Stopwatch state
  private var swSeconds = 0
  private var swRunning = false
  private var swPaused = false

  // Timer state
  private var timerHours = 0
  private var timerMinutes = 0
  private var timerSeconds = 0
  private var timerRemaining = 0
  private var timerRunning = false
  private var timerPaused = false

  // Strict state
  private var strictRemainingSeconds = 0
  private var strictCountdownRunnable: java.lang.Runnable? = null

  // Views
  private lateinit var tabStopwatch: TextView
  private lateinit var tabTimer: TextView
  private lateinit var tabStrict: TextView
  private lateinit var panelStopwatch: LinearLayout
  private lateinit var panelTimer: LinearLayout
  private lateinit var panelStrict: LinearLayout
  private lateinit var panelStrictActive: FrameLayout
  private lateinit var tvStopwatch: TextView
  private lateinit var btnSwStart: TextView
  private lateinit var btnSwPause: TextView
  private lateinit var btnSwStop: TextView
  private lateinit var btnSwResume: TextView
  private lateinit var pickerHours: NumberPicker
  private lateinit var pickerMinutes: NumberPicker
  private lateinit var pickerSeconds: NumberPicker
  private lateinit var layoutPickers: LinearLayout
  private lateinit var tvTimerCountdown: TextView
  private lateinit var tvTimeUp: TextView
  private lateinit var btnTimerStart: TextView
  private lateinit var btnTimerPause: TextView
  private lateinit var btnTimerStop: TextView
  private lateinit var btnTimerResume: TextView
  private lateinit var btnTimerReset: TextView
  private lateinit var btnEnableStrict: TextView
  private lateinit var tvStrictCountdown: TextView
  private lateinit var tvStrictStatus: TextView
  private lateinit var btnExitStrict: TextView

  private lateinit var prefs: SharedPreferences
  private lateinit var vibrator: Vibrator

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Wrap entire onCreate in try-catch to prevent crash
    try {
      setContentView(R.layout.activity_focus)
      
      prefs = getSharedPreferences("miss_prefs", 
        MODE_PRIVATE)
      
      vibrator = if (Build.VERSION.SDK_INT >= 31)
        getSystemService(VibratorManager::class.java)
          .defaultVibrator
      else
        @Suppress("DEPRECATION")
        getSystemService(VIBRATOR_SERVICE) as Vibrator

      bindViews()
      setupTabs()
      setupStopwatch()
      setupTimer()
      setupStrictMode()

    } catch (e: Exception) {
      e.printStackTrace()
      // Never crash — go back gracefully
      finish()
    }
  }

  fun bindViews() {
    tabStopwatch = findViewById(R.id.tabStopwatch)
    tabTimer = findViewById(R.id.tabTimer)
    tabStrict = findViewById(R.id.tabStrict)
    panelStopwatch = findViewById(R.id.panelStopwatch)
    panelTimer = findViewById(R.id.panelTimer)
    panelStrict = findViewById(R.id.panelStrict)
    panelStrictActive = findViewById(R.id.panelStrictActive)
    tvStopwatch = findViewById(R.id.tvStopwatch)
    btnSwStart = findViewById(R.id.btnSwStart)
    btnSwPause = findViewById(R.id.btnSwPause)
    btnSwStop = findViewById(R.id.btnSwStop)
    btnSwResume = findViewById(R.id.btnSwResume)
    pickerHours = findViewById(R.id.pickerHours)
    pickerMinutes = findViewById(R.id.pickerMinutes)
    pickerSeconds = findViewById(R.id.pickerSeconds)
    layoutPickers = findViewById(R.id.layoutPickers)
    tvTimerCountdown = findViewById(R.id.tvTimerCountdown)
    tvTimeUp = findViewById(R.id.tvTimeUp)
    btnTimerStart = findViewById(R.id.btnTimerStart)
    btnTimerPause = findViewById(R.id.btnTimerPause)
    btnTimerStop = findViewById(R.id.btnTimerStop)
    btnTimerResume = findViewById(R.id.btnTimerResume)
    btnTimerReset = findViewById(R.id.btnTimerReset)
    btnEnableStrict = findViewById(R.id.btnEnableStrict)
    tvStrictCountdown = findViewById(R.id.tvStrictCountdown)
    tvStrictStatus = findViewById(R.id.tvStrictStatus)
    btnExitStrict = findViewById(R.id.btnExitStrict)
  }

  fun setupTabs() {
    fun selectTab(tab: Int) {
      // Reset all
      tabStopwatch.setTextColor(
        android.graphics.Color.parseColor("#666666"))
      tabTimer.setTextColor(
        android.graphics.Color.parseColor("#666666"))
      tabStrict.setTextColor(
        android.graphics.Color.parseColor("#666666"))
      panelStopwatch.visibility = View.GONE
      panelTimer.visibility = View.GONE
      panelStrict.visibility = View.GONE

      when (tab) {
        0 -> {
          tabStopwatch.setTextColor(
            android.graphics.Color.WHITE)
          panelStopwatch.visibility = View.VISIBLE
        }
        1 -> {
          tabTimer.setTextColor(
            android.graphics.Color.WHITE)
          panelTimer.visibility = View.VISIBLE
        }
        2 -> {
          tabStrict.setTextColor(
            android.graphics.Color.WHITE)
          panelStrict.visibility = View.VISIBLE
          // Always show warning when tab opened
          findViewById<LinearLayout>(
            R.id.layoutStrictWarning)
            .visibility = View.VISIBLE
          btnEnableStrict.visibility = View.VISIBLE
        }
      }
    }

    selectTab(0) // Default: stopwatch
    tabStopwatch.setOnClickListener { selectTab(0) }
    tabTimer.setOnClickListener { selectTab(1) }
    tabStrict.setOnClickListener { selectTab(2) }
  }

  fun setupStopwatch() {
    btnSwStart.setOnClickListener {
      swRunning = true
      swPaused = false
      btnSwStart.visibility = View.GONE
      btnSwPause.visibility = View.VISIBLE
      btnSwStop.visibility = View.VISIBLE
      runStopwatch()
    }
    btnSwPause.setOnClickListener {
      swPaused = true
      handler.removeCallbacksAndMessages(null)
      btnSwPause.visibility = View.GONE
      btnSwResume.visibility = View.VISIBLE
    }
    btnSwResume.setOnClickListener {
      swPaused = false
      btnSwResume.visibility = View.GONE
      btnSwPause.visibility = View.VISIBLE
      runStopwatch()
    }
    btnSwStop.setOnClickListener {
      handler.removeCallbacksAndMessages(null)
      swRunning = false
      swPaused = false
      swSeconds = 0
      tvStopwatch.text = "00:00"
      btnSwStart.visibility = View.VISIBLE
      btnSwPause.visibility = View.GONE
      btnSwStop.visibility = View.GONE
      btnSwResume.visibility = View.GONE
    }
  }

  fun runStopwatch() {
    handler.post(object : Runnable {
      override fun run() {
        if (!swRunning || swPaused) return
        swSeconds++
        val m = swSeconds / 60
        val s = swSeconds % 60
        tvStopwatch.text = "%02d:%02d".format(m, s)
        handler.postDelayed(this, 1000)
      }
    })
  }

  fun setupTimer() {
    pickerHours.minValue = 0
    pickerHours.maxValue = 99
    pickerHours.wrapSelectorWheel = true
    pickerMinutes.minValue = 0
    pickerMinutes.maxValue = 59
    pickerMinutes.wrapSelectorWheel = true
    pickerSeconds.minValue = 0
    pickerSeconds.maxValue = 59
    pickerSeconds.wrapSelectorWheel = true

    // Style pickers white
    styleNumberPicker(pickerHours)
    styleNumberPicker(pickerMinutes)
    styleNumberPicker(pickerSeconds)

    pickerHours.setOnValueChangedListener { 
      _, _, v -> timerHours = v }
    pickerMinutes.setOnValueChangedListener { 
      _, _, v -> timerMinutes = v }
    pickerSeconds.setOnValueChangedListener { 
      _, _, v -> timerSeconds = v }

    btnTimerStart.setOnClickListener {
      timerHours = pickerHours.value
      timerMinutes = pickerMinutes.value
      timerSeconds = pickerSeconds.value
      val total = timerHours * 3600 + 
                  timerMinutes * 60 + timerSeconds
      if (total <= 0) {
        Toast.makeText(this, 
          "Set a time first", Toast.LENGTH_SHORT).show()
        return@setOnClickListener
      }
      timerRemaining = total
      startTimer()
    }

    btnTimerPause.setOnClickListener {
      timerPaused = true
      handler.removeCallbacksAndMessages(null)
      btnTimerPause.visibility = View.GONE
      btnTimerResume.visibility = View.VISIBLE
    }

    btnTimerResume.setOnClickListener {
      timerPaused = false
      btnTimerResume.visibility = View.GONE
      btnTimerPause.visibility = View.VISIBLE
      runTimer()
    }

    btnTimerStop.setOnClickListener { resetTimer() }
    btnTimerReset.setOnClickListener { resetTimer() }
  }

  fun startTimer() {
    timerRunning = true
    timerPaused = false
    layoutPickers.visibility = View.GONE
    tvTimerCountdown.visibility = View.VISIBLE
    btnTimerStart.visibility = View.GONE
    btnTimerPause.visibility = View.VISIBLE
    btnTimerStop.visibility = View.VISIBLE
    runTimer()
  }

  fun runTimer() {
    handler.post(object : Runnable {
      override fun run() {
        if (!timerRunning || timerPaused) return
        if (timerRemaining <= 0) {
          onTimerDone()
          return
        }
        val h = timerRemaining / 3600
        val m = (timerRemaining % 3600) / 60
        val s = timerRemaining % 60
        tvTimerCountdown.text = 
          "%02d:%02d:%02d".format(h, m, s)
        timerRemaining--
        handler.postDelayed(this, 1000)
      }
    })
  }

  fun onTimerDone() {
    timerRunning = false
    tvTimerCountdown.text = "00:00:00"
    tvTimeUp.visibility = View.VISIBLE
    btnTimerPause.visibility = View.GONE
    btnTimerStop.visibility = View.GONE
    btnTimerReset.visibility = View.VISIBLE
    // Vibrate subtle 3s pattern
    val pattern = longArrayOf(0,300,200,300,200,300,200)
    if (Build.VERSION.SDK_INT >= 26) {
      vibrator.vibrate(
        VibrationEffect.createWaveform(pattern, -1))
    } else {
      @Suppress("DEPRECATION")
      vibrator.vibrate(pattern, -1)
    }
  }

  fun resetTimer() {
    handler.removeCallbacksAndMessages(null)
    timerRunning = false
    timerPaused = false
    timerRemaining = 0
    tvTimeUp.visibility = View.GONE
    tvTimerCountdown.visibility = View.GONE
    layoutPickers.visibility = View.VISIBLE
    btnTimerStart.visibility = View.VISIBLE
    btnTimerPause.visibility = View.GONE
    btnTimerStop.visibility = View.GONE
    btnTimerResume.visibility = View.GONE
    btnTimerReset.visibility = View.GONE
    pickerHours.value = 0
    pickerMinutes.value = 0
    pickerSeconds.value = 0
    timerHours = 0; timerMinutes = 0; timerSeconds = 0
  }

  fun styleNumberPicker(p: NumberPicker) {
    try {
      val f = NumberPicker::class.java
        .getDeclaredField("mSelectorWheelPaint")
      f.isAccessible = true
      (f.get(p) as android.graphics.Paint)
        .color = android.graphics.Color.WHITE
      p.invalidate()
    } catch (e: Exception) { e.printStackTrace() }
  }

  override fun onResume() {
    super.onResume()
    val prefs = getSharedPreferences("strict_prefs", MODE_PRIVATE)
    val active = prefs.getBoolean("active", false)
    val endTime = prefs.getLong("end_time", 0)
    
    if (active && System.currentTimeMillis() < endTime) {
      val remaining = (endTime - System.currentTimeMillis()) / 1000
      strictRemainingSeconds = remaining.toInt()
      StrictModeService.isStrictModeActive = true
      StrictModeService.strictEndTimeMs = endTime
      showStrictCountdown()
    } else if (active) {
      endStrictMode()
    } else {
      val layoutStrictWarning = findViewById<LinearLayout>(R.id.layoutStrictWarning)
      val tvStrictDesc = layoutStrictWarning.getChildAt(1) as? TextView
      if (!isAccessibilityServiceEnabled()) {
         tvStrictDesc?.text = "To use Strict Mode:\n1. Tap 'Open Settings' below\n2. Find 'MISS Minimal Strict Mode'\n3. Toggle it ON\n4. Come back here"
         btnEnableStrict.text = "Open Settings →"
      } else {
         tvStrictDesc?.text = "Enabling this will silence ALL calls,\nmessages and notifications for 25 minutes.\n\nCamera will be disabled.\nNo apps can be opened.\nThis CANNOT be cancelled once started.\n\nOnly enable if fully committed.\nUse at your own risk."
         btnEnableStrict.text = "ENABLE STRICT MODE"
      }
    }
  }

  fun isAccessibilityServiceEnabled(): Boolean {
    val expectedServiceName = "${packageName}/.StrictModeService"
    val enabledServices = Settings.Secure.getString(
      contentResolver,
      Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabledServices.contains(expectedServiceName)
  }

  fun setupStrictMode() {
    btnEnableStrict.setOnClickListener {
      if (!isAccessibilityServiceEnabled()) {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
      } else {
        showStrictConfirmDialog()
      }
    }
  }

  fun showStrictConfirmDialog() {
    AlertDialog.Builder(this)
      .setMessage(
        "25 minutes of complete focus.\n\n" +
        "No apps can be opened.\n" +
        "No calls. No messages.\n" +
        "Cannot be cancelled.\n\n" +
        "Are you ready?")
      .setPositiveButton("START") { _, _ ->
        startStrictMode()
      }
      .setNegativeButton("Cancel", null)
      .show()
  }

  fun startStrictMode() {
    val durationMs = 25L * 60L * 1000L // 25 minutes
    
    // Enable DND
    val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    if (nm.isNotificationPolicyAccessGranted) {
      nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
    }
    
    // Set strict mode active via companion object
    StrictModeService.startStrict(durationMs)
    
    // Save to SharedPreferences for persistence
    getSharedPreferences("strict_prefs", MODE_PRIVATE)
      .edit()
      .putBoolean("active", true)
      .putLong("end_time", System.currentTimeMillis() + durationMs)
      .apply()
    
    strictRemainingSeconds = 25 * 60
    showStrictCountdown()
  }

  fun showStrictCountdown() {
    findViewById<View>(R.id.tabBar).visibility = View.GONE
    panelStopwatch.visibility = View.GONE
    panelTimer.visibility = View.GONE
    panelStrict.visibility = View.GONE
    panelStrictActive.visibility = View.VISIBLE
    
    tvStrictStatus.text = "focus session active"
    btnExitStrict.visibility = View.GONE
    tvStrictCountdown.text = "%02d:%02d".format(strictRemainingSeconds / 60, strictRemainingSeconds % 60)

    strictCountdownRunnable?.let { handler.removeCallbacks(it) }
    strictCountdownRunnable = object : Runnable {
      override fun run() {
        if (strictRemainingSeconds <= 0) {
          endStrictMode()
          return
        }
        val m = strictRemainingSeconds / 60
        val s = strictRemainingSeconds % 60
        tvStrictCountdown.text = "%02d:%02d".format(m, s)
        strictRemainingSeconds--
        handler.postDelayed(this, 1000)
      }
    }
    handler.post(strictCountdownRunnable!!)
  }

  fun endStrictMode() {
    StrictModeService.endStrict()
    
    val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    if (nm.isNotificationPolicyAccessGranted) {
      nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
    }
    
    getSharedPreferences("strict_prefs", MODE_PRIVATE)
      .edit().clear().apply()
    
    val vibrator = if (Build.VERSION.SDK_INT >= 31) {
      getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
      @Suppress("DEPRECATION")
      getSystemService(VIBRATOR_SERVICE) as Vibrator
    }
    val pattern = longArrayOf(0,300,200,300,200,300)
    if (Build.VERSION.SDK_INT >= 26) {
      vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    } else {
      @Suppress("DEPRECATION")
      vibrator.vibrate(pattern, -1)
    }
    
    tvStrictCountdown.text = "DONE"
    tvStrictStatus.text = "session complete"
    
    btnExitStrict.visibility = View.VISIBLE
    btnExitStrict.setOnClickListener {
      showStrictWarningScreen()
    }
  }

  fun showStrictWarningScreen() {
    findViewById<View>(R.id.tabBar).visibility = View.VISIBLE
    panelStrictActive.visibility = View.GONE
    
    tabStopwatch.setTextColor(android.graphics.Color.parseColor("#666666"))
    tabTimer.setTextColor(android.graphics.Color.parseColor("#666666"))
    tabStrict.setTextColor(android.graphics.Color.WHITE)
    
    panelStopwatch.visibility = View.GONE
    panelTimer.visibility = View.GONE
    panelStrict.visibility = View.VISIBLE
  }

  override fun onBackPressed() {
    val prefs = getSharedPreferences("strict_prefs", MODE_PRIVATE)
    if (prefs.getBoolean("active", false)) return // Block back during strict
    super.onBackPressed()
  }

  override fun onDestroy() {
    handler.removeCallbacksAndMessages(null)
    super.onDestroy()
  }
}
