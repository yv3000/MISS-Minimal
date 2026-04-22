package com.minimalist.launcher

import android.animation.ValueAnimator
import android.view.animation.AccelerateDecelerateInterpolator
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
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter


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

  // Pomodoro Fields
  private var selectedDurationMins = 25
  private val selectedApps = mutableListOf<String>()
  private var contactName: String? = null
  private var contactNumber: String? = null

  private lateinit var panelPomodoro: FrameLayout
  private lateinit var pom_layoutSetup: LinearLayout
  private lateinit var pom_layoutActive: LinearLayout
  private lateinit var pom_btnDur25: TextView
  private lateinit var pom_btnDur50: TextView
  private lateinit var pom_btnDur75: TextView
  private lateinit var pom_btnDur100: TextView
  private lateinit var pom_slotApp1: TextView
  private lateinit var pom_slotApp2: TextView
  private lateinit var pom_slotApp3: TextView
  private lateinit var pom_tvContactName: TextView
  private lateinit var pom_btnRemoveContact: View
  private lateinit var pom_btnStartPomodoro: View
  private lateinit var pom_tvPhaseLabel: TextView
  private lateinit var pom_tvCountdown: TextView
  private lateinit var pom_tvSessionCount: TextView
  private lateinit var pom_btnCallContact: View
  private lateinit var pom_btnCancelBreak: View
  private lateinit var pom_active_slot1: TextView
  private lateinit var pom_active_slot2: TextView
  private lateinit var pom_active_slot3: TextView

  private lateinit var tabIndicator: View

  private val timerReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      if (intent == null) return
      when (intent.action) {
        PomodoroTimerService.BROADCAST_TICK -> {
          val remaining = intent.getIntExtra("remaining", 0)
          val isWork = intent.getBooleanExtra("is_work", true)
          updatePomTimerUI(remaining, isWork)
        }
        PomodoroTimerService.BROADCAST_PHASE_CHANGE -> {
          val isWork = intent.getBooleanExtra("is_work", true)
          val session = intent.getIntExtra("session", 1)
          onPomPhaseChanged(isWork, session)
        }
        PomodoroTimerService.BROADCAST_COMPLETE -> {
          onPomodoroCompleted()
        }
      }
    }
  }

  private lateinit var vibrator: Vibrator
  private lateinit var gestureDetector: GestureDetector
  private var currentTabIndex = 0
  private val TABS = arrayOf("stopwatch", "timer", "strict", "pomodoro")


  override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)
    handleIntent(intent)
  }

  private fun handleIntent(intent: Intent?) {
    intent ?: return
    
    val tab = intent.getStringExtra("tab")
    if (tab != null) {
      selectTab(tab)
    }

    if (intent.getBooleanExtra("show_strict_timer", false)) {
      showStrictCountdown()
    }

    val openTab = intent.getStringExtra("open_tab")
    if (openTab == "strict") {
      // Switch to strict tab
      selectStrictTab()
      // Show the warning screen with ENABLE button
      showStrictWarningScreen()
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
      setupPomodoro()

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
            if (currentTabIndex > 0) {
                selectTab(TABS[currentTabIndex - 1])
            }
          } else {
            // Swipe left -> Next tab
            if (currentTabIndex < TABS.size - 1) {
                selectTab(TABS[currentTabIndex + 1])
            }
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

  private fun selectStrictTab() {
    updateTabUI(tabStrict)

    panelStrict.visibility = View.VISIBLE
    panelStopwatch.visibility = View.GONE
    panelTimer.visibility = View.GONE
    panelPomodoro.visibility = View.GONE
  }

  private fun updateTabUI(activeTab: TextView) {
    val inactiveColor = android.graphics.Color.parseColor("#8E8E93")
    val activeColor = android.graphics.Color.WHITE

    val tabs = listOf(tabStopwatch, tabTimer, tabStrict, tabPomodoro)
    tabs.forEach { tab ->
      if (tab == activeTab) {
        tab.setTextColor(activeColor)
        tab.animate().alpha(1.0f).setDuration(200).start()
      } else {
        tab.setTextColor(inactiveColor)
        tab.animate().alpha(0.4f).setDuration(200).start()
      }
    }
    
    moveTabIndicator(activeTab)
  }

  private fun moveTabIndicator(targetView: View) {
    tabIndicator.post {
      val targetX = targetView.x
      val targetWidth = targetView.width
      
      tabIndicator.animate()
        .x(targetX)
        .setDuration(300)
        .setInterpolator(AccelerateDecelerateInterpolator())
        .setUpdateListener {
            val params = tabIndicator.layoutParams
            params.width = targetWidth
            tabIndicator.layoutParams = params
        }
        .start()
        
      // For width animation specifically, ValueAnimator is better
      val widthAnimator = ValueAnimator.ofInt(tabIndicator.width, targetWidth)
      widthAnimator.addUpdateListener { animator ->
          val params = tabIndicator.layoutParams
          params.width = animator.animatedValue as Int
          tabIndicator.layoutParams = params
      }
      widthAnimator.duration = 300
      widthAnimator.interpolator = AccelerateDecelerateInterpolator()
      widthAnimator.start()
    }
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
    tabPomodoro.setTextColor(android.graphics.Color.parseColor("#8E8E93"))

    panelStopwatch.visibility = View.GONE
    panelTimer.visibility = View.GONE
    panelStrict.visibility = View.GONE
    panelPomodoro.visibility = View.GONE

    when (tab) {
      "stopwatch" -> {
        currentTabIndex = 0
        updateTabUI(tabStopwatch)
        panelStopwatch.visibility = View.VISIBLE
      }
      "timer" -> {
        currentTabIndex = 1
        updateTabUI(tabTimer)
        panelTimer.visibility = View.VISIBLE
      }
      "strict" -> {
        currentTabIndex = 2
        updateTabUI(tabStrict)
        panelStrict.visibility = View.VISIBLE
      }
      "pomodoro" -> {
        currentTabIndex = 3
        updateTabUI(tabPomodoro)
        panelPomodoro.visibility = View.VISIBLE
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

    tabIndicator = findViewById(R.id.tabIndicator)

    panelPomodoro = findViewById(R.id.panelPomodoro)
    pom_layoutSetup = findViewById(R.id.pom_layoutSetup)
    pom_layoutActive = findViewById(R.id.pom_layoutActive)
    pom_btnDur25 = findViewById(R.id.pom_btnDur25)
    pom_btnDur50 = findViewById(R.id.pom_btnDur50)
    pom_btnDur75 = findViewById(R.id.pom_btnDur75)
    pom_btnDur100 = findViewById(R.id.pom_btnDur100)
    pom_slotApp1 = findViewById(R.id.pom_slotApp1)
    pom_slotApp2 = findViewById(R.id.pom_slotApp2)
    pom_slotApp3 = findViewById(R.id.pom_slotApp3)
    pom_tvContactName = findViewById(R.id.pom_tvContactName)
    pom_btnRemoveContact = findViewById(R.id.pom_btnRemoveContact)
    pom_btnStartPomodoro = findViewById(R.id.pom_btnStartPomodoro)
    pom_tvPhaseLabel = findViewById(R.id.pom_tvPhaseLabel)
    pom_tvCountdown = findViewById(R.id.pom_tvCountdown)
    pom_tvSessionCount = findViewById(R.id.pom_tvSessionCount)
    pom_btnCallContact = findViewById(R.id.pom_btnCallContact)
    pom_btnCancelBreak = findViewById(R.id.pom_btnCancelBreak)
    pom_active_slot1 = findViewById(R.id.pom_active_slot1)
    pom_active_slot2 = findViewById(R.id.pom_active_slot2)
    pom_active_slot3 = findViewById(R.id.pom_active_slot3)
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
    AppFont.applyToActivity(this)
    
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

    // Pomodoro restoration
    val filter = IntentFilter().apply {
      addAction(PomodoroTimerService.BROADCAST_TICK)
      addAction(PomodoroTimerService.BROADCAST_PHASE_CHANGE)
      addAction(PomodoroTimerService.BROADCAST_COMPLETE)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      registerReceiver(timerReceiver, filter, RECEIVER_NOT_EXPORTED)
    } else {
      registerReceiver(timerReceiver, filter)
    }

    if (PomodoroManager.isActive) {
      // Restore state from manager
      selectedApps.clear()
      selectedApps.addAll(PomodoroManager.allowedPackages)
      contactName = PomodoroManager.emergencyContactName
      contactNumber = PomodoroManager.emergencyContactNumber
      
      showPomActiveScreen()
    } else {
      showPomSetupScreen()
      updatePomDurationSelection(selectedDurationMins)
      updatePomAppSlotsUI()
      updatePomContactUI()
    }
  }

  override fun onPause() {
    super.onPause()
    unregisterReceiver(timerReceiver)
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

  private fun setupPomodoro() {
    pom_btnDur25.setOnClickListener { updatePomDurationSelection(25) }
    pom_btnDur50.setOnClickListener { updatePomDurationSelection(50) }
    pom_btnDur75.setOnClickListener { updatePomDurationSelection(75) }
    pom_btnDur100.setOnClickListener { updatePomDurationSelection(100) }

    pom_slotApp1.setOnClickListener { handlePomAppSlotTap(0) }
    pom_slotApp2.setOnClickListener { handlePomAppSlotTap(1) }
    pom_slotApp3.setOnClickListener { handlePomAppSlotTap(2) }

    pom_slotApp1.setOnLongClickListener { selectedApps.getOrNull(0)?.let { selectedApps.removeAt(0); updatePomAppSlotsUI() }; true }
    pom_slotApp2.setOnLongClickListener { selectedApps.getOrNull(1)?.let { selectedApps.removeAt(1); updatePomAppSlotsUI() }; true }
    pom_slotApp3.setOnLongClickListener { selectedApps.getOrNull(2)?.let { selectedApps.removeAt(2); updatePomAppSlotsUI() }; true }

    pom_tvContactName.setOnClickListener { openPomContactPicker() }
    pom_btnRemoveContact.setOnClickListener { removePomContact() }
    pom_btnStartPomodoro.setOnClickListener { startPomodoro() }

    pom_btnCallContact.setOnClickListener {
      contactNumber?.let { num ->
        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$num")))
      }
    }
    pom_btnCancelBreak.setOnClickListener { endPomodoroSession() }
    
    val activeSlots = listOf(pom_active_slot1, pom_active_slot2, pom_active_slot3)
    activeSlots.forEachIndexed { index, slot ->
        slot.setOnClickListener {
            selectedApps.getOrNull(index)?.let { pkg ->
                val intent = packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "Cannot launch $pkg", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
  }

  private fun updatePomDurationSelection(mins: Int) {
    selectedDurationMins = mins
    val buttons = listOf(pom_btnDur25, pom_btnDur50, pom_btnDur75, pom_btnDur100)
    val durations = listOf(25, 50, 75, 100)
    buttons.forEachIndexed { index, btn ->
      val isSelected = durations[index] == mins
      if (isSelected) {
        btn.setBackgroundResource(R.drawable.bg_pill)
        btn.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
        btn.setTextColor(android.graphics.Color.BLACK)
      } else {
        btn.setBackgroundResource(R.drawable.btn_border)
        btn.backgroundTintList = null
        btn.setTextColor(android.graphics.Color.parseColor("#8E8E93"))
      }
    }
  }

  private fun handlePomAppSlotTap(index: Int) {
    if (index < selectedApps.size) {
      Toast.makeText(this, "Long press to remove app", Toast.LENGTH_SHORT).show()
    } else {
      val intent = Intent(this, AppPickerActivity::class.java)
      intent.putExtra("pomodoro_mode", true)
      startActivityForResult(intent, 2001)
    }
  }

  private fun updatePomAppSlotsUI() {
    fun style(slot: TextView, pkg: String?) {
      if (pkg != null) {
        slot.text = pkg.substringAfterLast(".").take(6)
        slot.setBackgroundResource(R.drawable.bg_pill)
        slot.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#222222"))
        slot.setTextColor(android.graphics.Color.WHITE)
      } else {
        slot.text = "+ add"
        slot.setBackgroundResource(R.drawable.btn_border)
        slot.backgroundTintList = null
        slot.setTextColor(android.graphics.Color.parseColor("#666666"))
      }
    }
    style(pom_slotApp1, selectedApps.getOrNull(0))
    style(pom_slotApp2, selectedApps.getOrNull(1))
    style(pom_slotApp3, selectedApps.getOrNull(2))
  }

  private fun openPomContactPicker() {
    val intent = Intent(Intent.ACTION_PICK, android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
    startActivityForResult(intent, 2002)
  }

  private fun removePomContact() {
    contactName = null; contactNumber = null
    updatePomContactUI()
  }

  private fun updatePomContactUI() {
    if (contactName != null) {
      pom_tvContactName.text = contactName
      pom_tvContactName.setTextColor(android.graphics.Color.WHITE)
      pom_btnRemoveContact.visibility = View.VISIBLE
    } else {
      pom_tvContactName.text = "+ select contact"
      pom_tvContactName.setTextColor(android.graphics.Color.parseColor("#666666"))
      pom_btnRemoveContact.visibility = View.GONE
    }
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    if (resultCode == RESULT_OK && data != null) {
      if (requestCode == 2001) {
        val pkg = data.getStringExtra("package_name") ?: return
        if (selectedApps.size < 3 && !selectedApps.contains(pkg)) {
          selectedApps.add(pkg)
          updatePomAppSlotsUI()
        }
      } else if (requestCode == 2002) {
        handlePomContactResult(data)
      }
    }
  }

  private fun handlePomContactResult(data: Intent) {
    try {
        val uri = data.data ?: return
        val cursor = contentResolver.query(uri, arrayOf(
            android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
        ), null, null, null)

        cursor?.use {
            if (it.moveToFirst()) {
                val nameIdx = it.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = it.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
                
                val name = if (nameIdx != -1) it.getString(nameIdx) else null
                val rawNum = if (numIdx != -1) it.getString(numIdx) else null

                if (name != null && rawNum != null) {
                    contactName = name
                    contactNumber = rawNum.replace("[^0-9+]".toRegex(), "")
                    updatePomContactUI()
                } else {
                    Toast.makeText(this, "Contact has no phone number", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Selected contact has no details", Toast.LENGTH_SHORT).show()
            }
        }
    } catch (e: Exception) {
        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
    }
  }

  private fun startPomodoro() {
    // Keep screen on
    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    
    PomodoroManager.start(
        durationMinutes = selectedDurationMins,
        allowedApps = selectedApps,
        emergencyContact = contactNumber,
        context = this
    )
    
    val intent = Intent(this, PomodoroTimerService::class.java)
    intent.action = PomodoroTimerService.ACTION_START
    intent.putExtra(PomodoroTimerService.EXTRA_DURATION, selectedDurationMins * 60)
    startService(intent)
    showPomActiveScreen()
  }

  private fun endPomodoroSession() {
    PomodoroManager.stop(this)
    val intent = Intent(this, PomodoroTimerService::class.java)
    stopService(intent)
    showPomSetupScreen()
  }

  private fun onPomodoroCompleted() {
    vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500), -1))
    AlertDialog.Builder(this)
      .setTitle("Session Complete")
      .setMessage("Well done! Do you want to start another session or finish?")
      .setCancelable(false)
      .setPositiveButton("CONTINUE") { _, _ -> showPomSetupScreen(); PomodoroManager.stop(this) }
      .setNegativeButton("FINISH") { _, _ -> endPomodoroSession() }
      .show()
  }

  private fun showPomSetupScreen() {
    TransitionManager.beginDelayedTransition(panelPomodoro, Fade())
    pom_layoutSetup.visibility = View.VISIBLE
    pom_layoutActive.visibility = View.GONE
    findViewById<View>(R.id.tabBar).visibility = View.VISIBLE
    tabIndicator.visibility = View.VISIBLE
  }

  private fun showPomActiveScreen() {
    TransitionManager.beginDelayedTransition(panelPomodoro, Fade())
    pom_layoutSetup.visibility = View.GONE
    pom_layoutActive.visibility = View.VISIBLE
    findViewById<View>(R.id.tabBar).visibility = View.GONE
    tabIndicator.visibility = View.GONE
    
    pom_btnCallContact.visibility = if (contactNumber != null) View.VISIBLE else View.GONE
    
    // Populate active app slots
    val activeSlots = listOf(pom_active_slot1, pom_active_slot2, pom_active_slot3)
    activeSlots.forEach { it.visibility = View.GONE }
    
    selectedApps.forEachIndexed { index, pkg ->
        if (index < activeSlots.size) {
            val slot = activeSlots[index]
            slot.visibility = View.VISIBLE
            // Use app name if possible, else part of package
            val label = try {
                val info = packageManager.getApplicationInfo(pkg, 0)
                packageManager.getApplicationLabel(info).toString()
            } catch (e: Exception) {
                pkg.substringAfterLast(".").take(8)
            }
            slot.text = label
        }
    }
  }

  private fun updatePomTimerUI(remaining: Int, isWork: Boolean) {
    val mins = remaining / 60
    val secs = remaining % 60
    pom_tvCountdown.text = "%02d:%02d".format(mins, secs)
    pom_tvPhaseLabel.text = if (isWork) "WORK PHASE" else "BREAK TIME"
    pom_tvPhaseLabel.setTextColor(if (isWork) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#4CAF50"))
    pom_btnCancelBreak.visibility = if (!isWork) View.VISIBLE else View.GONE
  }

  private fun onPomPhaseChanged(isWork: Boolean, session: Int) {
    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
    pom_tvSessionCount.text = "session $session"
  }

  override fun onBackPressed() {
    if (StrictModeManager.isActive() || (PomodoroManager.isActive && PomodoroManager.isWorkPhase)) return
    super.onBackPressed()
  }

  override fun onDestroy() {
    handler.removeCallbacksAndMessages(null)
    super.onDestroy()
  }
}
