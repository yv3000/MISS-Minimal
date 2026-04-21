package com.minimalist.launcher

import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.transition.Fade
import android.transition.TransitionManager
import android.view.View
import android.view.ViewGroup


import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.abs

class FocusActivity : AppCompatActivity() {

  private val handler = Handler(Looper.getMainLooper())
  private var swSeconds = 0
  private var swRunning = false
  private var swPaused = false

  private var timerHours = 0
  private var timerMinutes = 0
  private var timerSeconds = 0
  private var timerRemaining = 0
  private var timerRunning = false
  private var timerPaused = false

  private var strictRemainingSeconds = 0
  private var strictCountdownRunnable: Runnable? = null

  private lateinit var tabStopwatch: TextView
  private lateinit var tabTimer: TextView
  private lateinit var tabStrict: TextView
  private lateinit var tabPomodoro: TextView
  private lateinit var panelStopwatch: LinearLayout
  private lateinit var panelTimer: LinearLayout
  private lateinit var panelStrict: LinearLayout
  private lateinit var panelStrictActive: FrameLayout
  private lateinit var tvStopwatch: TextView
  private lateinit var tvStrictCountdown: TextView
  private lateinit var tvStrictStatus: TextView
  private lateinit var layoutStrictComplete: LinearLayout
  private lateinit var btnStartAgain: TextView
  private lateinit var btnExitStrict: TextView
  private lateinit var btnEnableStrict: TextView

  private lateinit var vibrator: Vibrator
  private lateinit var gestureDetector: GestureDetector
  private var currentTabIndex = 0
  private val TABS = arrayOf("stopwatch", "timer", "strict", "pomodoro")


  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    handleIntent(intent)
  }

  private fun handleIntent(intent: Intent?) {
    val tab = intent?.getStringExtra("tab")
    if (tab != null) {
      selectTab(tab)
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {

    super.onCreate(savedInstanceState)
    try {
      setContentView(R.layout.activity_focus)
      vibrator = if (Build.VERSION.SDK_INT >= 31)
        getSystemService(VibratorManager::class.java).defaultVibrator
      else
        @Suppress("DEPRECATION") getSystemService(VIBRATOR_SERVICE) as Vibrator

      bindViews()
      setupGestures()
      setupTabs()
      setupStopwatch()
      setupTimer()
      setupStrictMode()

      handleIntent(intent)
    } catch (e: Exception) {
      e.printStackTrace()
      finish()
    }
  }

  private fun setupGestures() {
    gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
      private val SWIPE_THRESHOLD = 100
      private val SWIPE_VELOCITY_THRESHOLD = 100

      override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
        if (e1 == null) return false
        val diffX = e2.x - e1.x
        val diffY = e2.y - e1.y
        if (abs(diffX) > abs(diffY) && abs(diffX) > SWIPE_THRESHOLD && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
          if (diffX > 0) {
            // Swipe right -> Previous tab
            if (currentTabIndex > 0) selectTab(TABS[currentTabIndex - 1])
          } else {
            // Swipe left -> Next tab
            if (currentTabIndex < TABS.size - 1) selectTab(TABS[currentTabIndex + 1])
          }


          return true
        }
        return false
      }
    })
  }

  override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
    gestureDetector.onTouchEvent(ev)
    return super.dispatchTouchEvent(ev)
  }

  override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)
    intent?.let { handleIntent(it) }
  }

  private fun handleIntent(intent: Intent) {
    if (intent.getBooleanExtra("show_strict_timer", false)) {
      showStrictCountdown()
    }
    val tab = intent.getStringExtra("open_tab")
    if (tab == "strict") {
      // Switch to strict tab
      selectStrictTab()
      // Show the warning screen with ENABLE button
      showStrictWarningScreen()
    }
  }

  private fun selectStrictTab() {
    tabStopwatch.setTextColor(android.graphics.Color.parseColor("#8E8E93"))
    tabTimer.setTextColor(android.graphics.Color.parseColor("#8E8E93"))
    tabPomodoro.setTextColor(android.graphics.Color.parseColor("#8E8E93"))
    tabStrict.setTextColor(android.graphics.Color.WHITE)

    panelStrict.visibility = View.VISIBLE
    panelStopwatch.visibility = View.GONE
    panelTimer.visibility = View.GONE
  }

  fun selectTab(tab: String) {
    if (tab == "strict") {
      onStrictTabSelected()
      return
    }

    val root = findViewById<ViewGroup>(android.R.id.content)
    TransitionManager.beginDelayedTransition(root, Fade())

    tabStopwatch.setTextColor(android.graphics.Color.parseColor("#8E8E93"))
    tabTimer.setTextColor(android.graphics.Color.parseColor("#8E8E93"))
    tabStrict.setTextColor(android.graphics.Color.parseColor("#8E8E93"))

    panelStopwatch.visibility = View.GONE
    panelTimer.visibility = View.GONE
    panelStrict.visibility = View.GONE

    when (tab) {
      "stopwatch" -> {
        currentTabIndex = 0
        tabStopwatch.setTextColor(android.graphics.Color.WHITE)
        panelStopwatch.visibility = View.VISIBLE
      }
      "timer" -> {
        currentTabIndex = 1
        tabTimer.setTextColor(android.graphics.Color.WHITE)
        panelTimer.visibility = View.VISIBLE
      }
      "pomodoro" -> {
        currentTabIndex = 3
        tabPomodoro.setTextColor(android.graphics.Color.WHITE)
        // Since Pomodoro is an activity, we start it
        startActivity(Intent(this, PomodoroActivity::class.java))
      }

    }
    savePersistedTab(tab)
  }

  fun onStrictTabSelected() {
    val prefs = getSharedPreferences("strict_prefs", MODE_PRIVATE)
    val startNow = prefs.getBoolean("start_immediately", false)
    
    if (startNow) {
      prefs.edit()
        .remove("start_immediately")
        .apply()
      startStrictMode()
      return
    }
    
    currentTabIndex = 2
    savePersistedTab("strict")
    
    if (StrictModeManager.isActive()) {
      val remaining = StrictModeManager.getRemainingMs() / 1000
      strictRemainingSeconds = remaining.toInt()
      showStrictCountdown()
      resumeCountdown()
    } else {
      selectStrictTab()
      showStrictWarningScreen()
    }
  }

  private fun savePersistedTab(tab: String) {
    getSharedPreferences("miss_prefs", MODE_PRIVATE)
        .edit().putString("last_focus_tab", tab).apply()
  }

  private fun showStrictWarningScreen() {
    findViewById<View>(R.id.layoutStrictWarning).visibility = View.VISIBLE
    btnEnableStrict.visibility = View.VISIBLE
  }

  private fun bindViews() {
    tabStopwatch = findViewById(R.id.tabStopwatch)
    tabTimer = findViewById(R.id.tabTimer)
    tabStrict = findViewById(R.id.tabStrict)
    tabPomodoro = findViewById(R.id.tabPomodoro)
    panelStopwatch = findViewById(R.id.panelStopwatch)
    panelTimer = findViewById(R.id.panelTimer)
    panelStrict = findViewById(R.id.panelStrict)
    panelStrictActive = findViewById(R.id.panelStrictActive)
    tvStopwatch = findViewById(R.id.tvStopwatch)
    tvStrictCountdown = findViewById(R.id.tvStrictCountdown)
    tvStrictStatus = findViewById(R.id.tvStrictStatus)
    layoutStrictComplete = findViewById(R.id.layoutStrictComplete)
    btnStartAgain = findViewById(R.id.btnStartAgain)
    btnExitStrict = findViewById(R.id.btnExitStrict)
    btnEnableStrict = findViewById(R.id.btnEnableStrict)
  }

  private fun setupTabs() {
    fun resolveTab(tab: Int) {
      when (tab) {
        0 -> selectTab("stopwatch")
        1 -> selectTab("timer")
        2 -> selectTab("strict")
        3 -> selectTab("pomodoro")
      }
    }

    val lastTab = getSharedPreferences("miss_prefs", MODE_PRIVATE).getString("last_focus_tab", "stopwatch")
    selectTab(lastTab ?: "stopwatch")
    
    tabStopwatch.setOnClickListener { resolveTab(0) }
    tabTimer.setOnClickListener { resolveTab(1) }
    tabStrict.setOnClickListener {
      if (!checkAllStrictPermissions()) {
        startActivity(Intent(this, PermissionOnboardingActivity::class.java))
      } else {
        onStrictTabSelected()
      }
    }
    tabPomodoro.setOnClickListener {
        selectTab("pomodoro")
        startActivity(Intent(this, PomodoroActivity::class.java))
    }
  }

  private fun setupStopwatch() {
    val btnStart = findViewById<TextView>(R.id.btnSwStart)
    val btnPause = findViewById<TextView>(R.id.btnSwPause)
    val btnStop = findViewById<TextView>(R.id.btnSwStop)
    val btnResume = findViewById<TextView>(R.id.btnSwResume)

    val btnFlag = findViewById<TextView>(R.id.btnSwFlag)
    val scrollTimestamps = findViewById<View>(R.id.scrollTimestamps)
    val tvTimestamps = findViewById<TextView>(R.id.tvTimestamps)
    val timestamps = mutableListOf<String>()

    btnStart.setOnClickListener {
      swRunning = true; swPaused = false
      btnStart.visibility = View.GONE
      btnPause.visibility = View.VISIBLE
      btnStop.visibility = View.VISIBLE
      btnFlag.visibility = View.VISIBLE
      scrollTimestamps.visibility = View.VISIBLE
      runStopwatch()
    }
    btnPause.setOnClickListener {
      swPaused = true
      handler.removeCallbacksAndMessages(null)
      btnPause.visibility = View.GONE; btnResume.visibility = View.VISIBLE
      btnFlag.visibility = View.GONE
    }
    btnResume.setOnClickListener {
      swPaused = false
      btnResume.visibility = View.GONE; btnPause.visibility = View.VISIBLE
      btnFlag.visibility = View.VISIBLE
      runStopwatch()
    }
    btnFlag.setOnClickListener {
      val m = swSeconds / 60; val s = swSeconds % 60
      val ts = "%02d:%02d".format(m, s)
      timestamps.add(ts)
      tvTimestamps.text = timestamps.joinToString("\n")
    }
    btnStop.setOnClickListener {
      handler.removeCallbacksAndMessages(null)
      swRunning = false; swPaused = false
      val elapsedSeconds = swSeconds
      swSeconds = 0
      tvStopwatch.text = "00:00"
      timestamps.clear()
      tvTimestamps.text = ""
      btnStart.visibility = View.VISIBLE; btnPause.visibility = View.GONE
      btnStop.visibility = View.GONE; btnResume.visibility = View.GONE
      btnFlag.visibility = View.GONE
      scrollTimestamps.visibility = View.GONE
      SotActivity.saveModeSession(this, "stopwatch", elapsedSeconds / 60)
    }

  }

  private fun runStopwatch() {
    handler.post(object : Runnable {
      override fun run() {
        if (!swRunning || swPaused) return
        swSeconds++
        val m = swSeconds / 60; val s = swSeconds % 60
        tvStopwatch.text = "%02d:%02d".format(m, s)
        handler.postDelayed(this, 1000)
      }
    })
  }

  private fun setupTimer() {
    val hPicker = findViewById<NumberPicker>(R.id.pickerHours)
    val mPicker = findViewById<NumberPicker>(R.id.pickerMinutes)
    val sPicker = findViewById<NumberPicker>(R.id.pickerSeconds)
    hPicker.minValue = 0; hPicker.maxValue = 99
    mPicker.minValue = 0; mPicker.maxValue = 59
    sPicker.minValue = 0; sPicker.maxValue = 59
    styleNumberPicker(hPicker); styleNumberPicker(mPicker); styleNumberPicker(sPicker)

    val btnStart = findViewById<TextView>(R.id.btnTimerStart)
    val btnPause = findViewById<TextView>(R.id.btnTimerPause)
    val btnStop = findViewById<TextView>(R.id.btnTimerStop)
    val btnResume = findViewById<TextView>(R.id.btnTimerResume)
    val btnReset = findViewById<TextView>(R.id.btnTimerReset)
    val layoutPickers = findViewById<View>(R.id.layoutPickers)
    val tvCountdown = findViewById<TextView>(R.id.tvTimerCountdown)
    val tvUp = findViewById<TextView>(R.id.tvTimeUp)

    btnStart.setOnClickListener {
      val total = hPicker.value * 3600 + mPicker.value * 60 + sPicker.value
      if (total <= 0) {
        Toast.makeText(this, "Set a time first", Toast.LENGTH_SHORT).show()
        return@setOnClickListener
      }
      timerRemaining = total; timerRunning = true; timerPaused = false
      layoutPickers.visibility = View.GONE; tvCountdown.visibility = View.VISIBLE
      btnStart.visibility = View.GONE; btnPause.visibility = View.VISIBLE; btnStop.visibility = View.VISIBLE
      runTimer(tvCountdown, tvUp, btnPause, btnStop, btnReset)
    }

    btnPause.setOnClickListener { timerPaused = true; btnPause.visibility = View.GONE; btnResume.visibility = View.VISIBLE }
    btnResume.setOnClickListener { timerPaused = false; btnResume.visibility = View.GONE; btnPause.visibility = View.VISIBLE; runTimer(tvCountdown, tvUp, btnPause, btnStop, btnReset) }
    btnStop.setOnClickListener { resetTimer(layoutPickers, tvCountdown, tvUp, btnStart, btnPause, btnStop, btnResume, btnReset, hPicker, mPicker, sPicker) }
    btnReset.setOnClickListener { resetTimer(layoutPickers, tvCountdown, tvUp, btnStart, btnPause, btnStop, btnResume, btnReset, hPicker, mPicker, sPicker) }
  }

  private fun runTimer(tv: TextView, up: TextView, pause: View, stop: View, reset: View) {
    handler.post(object : Runnable {
      override fun run() {
        if (!timerRunning || timerPaused) return
        if (timerRemaining <= 0) {
          timerRunning = false; up.visibility = View.VISIBLE; pause.visibility = View.GONE; stop.visibility = View.GONE; reset.visibility = View.VISIBLE
          val pattern = longArrayOf(0, 300, 200, 300, 200, 300, 200)
          if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
          else @Suppress("DEPRECATION") vibrator.vibrate(pattern, -1)
          val hPicker = findViewById<NumberPicker>(R.id.pickerHours)
          val mPicker = findViewById<NumberPicker>(R.id.pickerMinutes)
          val sPicker = findViewById<NumberPicker>(R.id.pickerSeconds)
          val totalMins = (hPicker.value * 3600 + mPicker.value * 60 + sPicker.value) / 60
          SotActivity.saveModeSession(this@FocusActivity, "timer", totalMins)
          return
        }
        val h = timerRemaining / 3600; val m = (timerRemaining % 3600) / 60; val s = timerRemaining % 60
        tv.text = "%02d:%02d:%02d".format(h, m, s)
        timerRemaining--; handler.postDelayed(this, 1000)
      }
    })
  }

  private fun resetTimer(lp: View, tv: View, up: View, start: View, pause: View, stop: View, resume: View, reset: View, hp: NumberPicker, mp: NumberPicker, sp: NumberPicker) {
    timerRunning = false; timerPaused = false; timerRemaining = 0
    lp.visibility = View.VISIBLE; tv.visibility = View.GONE; up.visibility = View.GONE
    start.visibility = View.VISIBLE; pause.visibility = View.GONE; stop.visibility = View.GONE; resume.visibility = View.GONE; reset.visibility = View.GONE
    hp.value = 0; mp.value = 0; sp.value = 0
  }

  private fun styleNumberPicker(p: NumberPicker) {
    try {
      val f = NumberPicker::class.java.getDeclaredField("mSelectorWheelPaint")
      f.isAccessible = true
      (f.get(p) as android.graphics.Paint).color = android.graphics.Color.WHITE
      p.invalidate()
    } catch (e: Exception) {}
  }

  fun applyStrictUiState(state: StrictUiState) {
    val layoutStrictWarning = findViewById<LinearLayout>(R.id.layoutStrictWarning)
    StrictModeManager.uiState = state
    when (state) {
      StrictUiState.SETUP -> {
        layoutStrictWarning.visibility = View.VISIBLE
        panelStrictActive.visibility = View.GONE
        layoutStrictComplete.visibility = View.GONE
        findViewById<View>(R.id.tabBar).visibility = View.VISIBLE
      }
      StrictUiState.RUNNING -> {
        layoutStrictWarning.visibility = View.GONE
        panelStrictActive.visibility = View.VISIBLE
        layoutStrictComplete.visibility = View.GONE
        findViewById<View>(R.id.tabBar).visibility = View.GONE
      }
      StrictUiState.COMPLETE -> {
        layoutStrictWarning.visibility = View.GONE
        panelStrictActive.visibility = View.VISIBLE
        layoutStrictComplete.visibility = View.VISIBLE
        findViewById<View>(R.id.tabBar).visibility = View.GONE
        tvStrictStatus.text = "session complete"
      }
    }
  }

  override fun onResume() {
    super.onResume()
    
    val navPrefs = getSharedPreferences("strict_nav", MODE_PRIVATE)
    val proceed = navPrefs.getBoolean("proceed", false)
    if (proceed) {
      navPrefs.edit().remove("proceed").apply()
      selectTab("strict")
      startStrictMode()
      return
    }

    // Check if returning from permission screen
    val prefs = getSharedPreferences("strict_prefs", MODE_PRIVATE)
    val startNow = prefs.getBoolean("start_immediately", false)
    
    if (startNow) {
      prefs.edit()
        .remove("start_immediately")
        .apply()
      // Switch to strict tab and start timer
      selectTab("strict")
      startStrictMode()
      return
    }
    
    // Restore timer if already running
    StrictModeManager.restoreFromPrefs(this)
    when {
      StrictModeManager.isActive() -> {
        selectTab("strict")
        applyStrictUiState(StrictUiState.RUNNING)
        val remaining = StrictModeManager.getRemainingMs() / 1000
        strictRemainingSeconds = remaining.toInt()
        showStrictCountdown()
        resumeCountdown()
      }
      StrictModeManager.uiState == StrictUiState.COMPLETE -> {
        selectTab("strict")
        applyStrictUiState(StrictUiState.COMPLETE)
      }
      else -> {
        applyStrictUiState(StrictUiState.SETUP)
        updateStrictWarningUI()
      }
    }
  }

  private fun updateStrictWarningUI() {
    val layoutStrictWarning = findViewById<LinearLayout>(R.id.layoutStrictWarning)
    val tvStrictDesc = layoutStrictWarning.getChildAt(1) as? TextView
    if (!isAccessibilityServiceEnabled()) {
      tvStrictDesc?.text = "To use Strict Mode:\n1. Tap 'Open Settings' below\n2. Find 'MISS Minimal Strict Mode'\n3. Toggle it ON\n4. Come back here"
      btnEnableStrict.text = "Open Settings →"
    } else if (!checkAllStrictPermissions()) {
      tvStrictDesc?.text = "Additional permissions required for Strict Mode.\nPlease tap below to set up."
      btnEnableStrict.text = "COMPLETE SETUP"
    } else {
      tvStrictDesc?.text = "Enabling this will silence ALL calls,\nmessages and notifications for 25 minutes.\n\nCamera will be disabled.\nNo apps can be opened.\nThis CANNOT be cancelled once started.\n\nOnly enable if fully committed.\nUse at your own risk."
      btnEnableStrict.text = "ENABLE STRICT MODE"
    }
  }

  private fun setupStrictMode() {
    btnEnableStrict.setOnClickListener {
      if (!checkAllStrictPermissions()) {
        startActivity(Intent(this, PermissionOnboardingActivity::class.java))
      } else {
        showStrictConfirmDialog()
      }
    }
    btnStartAgain.setOnClickListener {
      showStrictConfirmDialog()
    }
    btnExitStrict.setOnClickListener {
      StrictModeManager.stop(this)
      applyStrictUiState(StrictUiState.SETUP)
      finish()
      val home = Intent(Intent.ACTION_MAIN); home.addCategory(Intent.CATEGORY_HOME)
      home.flags = Intent.FLAG_ACTIVITY_NEW_TASK; startActivity(home)
    }
  }

  private fun showStrictConfirmDialog() {
    AlertDialog.Builder(this)
      .setMessage("25 more minutes of strict focus.\nNo apps. No notifications.\nAre you ready?")
      .setPositiveButton("YES") { _, _ -> startStrictMode() }
      .setNegativeButton("NO", null).show()
  }

  private fun startStrictMode() {
    val durationMs = 25 * 60 * 1000L
    StrictModeManager.start(this, durationMs)
    val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    if (nm.isNotificationPolicyAccessGranted) nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
    strictRemainingSeconds = (durationMs / 1000).toInt()
    applyStrictUiState(StrictUiState.RUNNING)
    showStrictCountdown()
    resumeCountdown()
  }

  private fun showStrictCountdown() {
    tvStrictStatus.text = "focus session active"
    tvStrictCountdown.text = "%02d:%02d".format(strictRemainingSeconds / 60, strictRemainingSeconds % 60)
  }

  private fun resumeCountdown() {
    strictCountdownRunnable?.let { handler.removeCallbacks(it) }
    strictCountdownRunnable = object : Runnable {
      override fun run() {
        if (!StrictModeManager.isActive()) {
          onStrictTimerDone()
          return
        }
        val remaining = StrictModeManager.getRemainingMs() / 1000
        strictRemainingSeconds = remaining.toInt()
        if (strictRemainingSeconds <= 0) {
          onStrictTimerDone()
          return
        }
        val m = strictRemainingSeconds / 60; val s = strictRemainingSeconds % 60
        tvStrictCountdown.text = "%02d:%02d".format(m, s)
        handler.postDelayed(this, 1000)
      }
    }
    handler.post(strictCountdownRunnable!!)
  }

  private fun onStrictTimerDone() {
    val completedMs = 25 * 60 * 1000 - StrictModeManager.getRemainingMs()
    val completedMins = (completedMs.toInt() / 60_000).coerceAtLeast(0)
    
    StrictModeManager.stop(this)
    val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    if (nm.isNotificationPolicyAccessGranted) nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
    
    val pattern = longArrayOf(0, 300, 200, 300, 200, 300)
    if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    else @Suppress("DEPRECATION") vibrator.vibrate(pattern, -1)

    tvStrictCountdown.text = "00:00"
    applyStrictUiState(StrictUiState.COMPLETE)
    
    SotActivity.saveModeSession(this, "strict", completedMins)
  }

  private fun checkAllStrictPermissions(): Boolean {
    return isAccessibilityServiceEnabled() && isUsageStatsGranted() && Settings.canDrawOverlays(this) && isNotificationPolicyGranted() && isBatteryOptimizationIgnored()
  }

  private fun isUsageStatsGranted(): Boolean {
    val appOps = getSystemService(APP_OPS_SERVICE) as android.app.AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
    }
    return mode == android.app.AppOpsManager.MODE_ALLOWED
  }

  private fun isNotificationPolicyGranted(): Boolean {
    val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    return nm.isNotificationPolicyAccessGranted
  }

  private fun isBatteryOptimizationIgnored(): Boolean {
    val pm = getSystemService(POWER_SERVICE) as android.os.PowerManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) pm.isIgnoringBatteryOptimizations(packageName) else true
  }

  private fun isAccessibilityServiceEnabled(): Boolean {
    val expected = "$packageName/.StrictModeService"
    val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    return enabled.contains(expected)
  }

  override fun onBackPressed() {
    if (StrictModeManager.isActive()) return
    super.onBackPressed()
  }

  override fun onDestroy() {
    handler.removeCallbacksAndMessages(null)
    super.onDestroy()
  }
}
