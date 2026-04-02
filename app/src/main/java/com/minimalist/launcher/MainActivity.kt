package com.minimalist.launcher

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.minimalist.launcher.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var gestureDetector: GestureDetector

    private val timeRunnable = object : Runnable {
        override fun run() {
            updateTime()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PrefsManager.init(this)
        AppTimerManager.start(this)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Thread {
            val apps = mutableListOf<AppItem>()
            val pm = packageManager
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (app in installedApps) {
                if (pm.getLaunchIntentForPackage(app.packageName) != null) {
                    val name = pm.getApplicationLabel(app).toString()
                    apps.add(AppItem(app.packageName, name))
                }
            }
            apps.sortBy { it.name.lowercase() }
            AppDrawerManager.cachedApps = apps
            AppDrawerManager.isReady = true
        }.start()

        setupGestures()
        setupClickListeners()

        handler.post(timeRunnable)
        
        binding.tvTime.textSize = 72f
        binding.tvDate.textSize = 16f


        if (!isDefaultLauncher()) {
            requestLauncherRole()
        }

        hideSystemUI()
    }

    override fun onResume() {
        super.onResume()
        applyFontSize()
        updateHomescreenApps()
        startNotificationBlocker()
        hideSystemUI()
        
        if (isDefaultLauncher()) {
            requestAllPermissionsOnFirstLaunch()
        }
        
        getSharedPreferences("miss_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("block_notif_panel", true)
            .apply()
        checkAndPromptAccessibility()
    }

    override fun onPause() {
        super.onPause()
        if (!StrictModeManager.isActive()) {
            stopNotificationBlocker()
        }
        getSharedPreferences("miss_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("block_notif_panel", false)
            .apply()
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 3001) {
            if (Build.VERSION.SDK_INT >= 23 && Settings.canDrawOverlays(this)) {
                startNotificationBlocker()
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) {
            val handler = Handler(Looper.getMainLooper())
            handler.postDelayed({
                hideSystemUI()
            }, 50)
        } else {
            hideSystemUI()
            startNotificationBlocker()
        }
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
    }

    private fun requestLauncherRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager != null && !roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                startActivityForResult(intent, 1001)
            }
        } else {
            val intent = Intent(Settings.ACTION_HOME_SETTINGS)
            startActivity(intent)
        }
    }

    private fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        val info = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return info?.activityInfo?.packageName == packageName
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(timeRunnable)
        stopNotificationBlocker()
    }

    override fun onBackPressed() {
        // Must do absolutely nothing
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
    }

    private fun updateTime() {
        val now = Date()
        val timeFormat = SimpleDateFormat("HH\nmm", Locale.getDefault())
        val fullTimeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEE · dd MMMM", Locale.getDefault()).format(now).uppercase()

        binding.tvTime.text = timeFormat.format(now)
        binding.tvDate.text = dateFormat

        binding.tvFullscreenTime.text = fullTimeFormat.format(now)
        binding.tvFullscreenDate.text = dateFormat
    }

    private fun setupClickListeners() {
    binding.btnExit.setOnClickListener {
        try {
            val intent = Intent(Settings.ACTION_HOME_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Intent.ACTION_MAIN)
                intent.addCategory(Intent.CATEGORY_HOME)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
            } catch (e2: Exception) {}
        }
    }

        binding.btnFocus.setOnClickListener { animateClick(it) {
            startActivity(Intent(this, FocusActivity::class.java))
            overridePendingTransition(R.anim.slide_up_enter, 0)
        }}

        binding.btnTime.setOnClickListener { animateClick(it) {
            binding.fullscreenTimeOverlay.visibility = View.VISIBLE
        }}

        binding.fullscreenTimeOverlay.setOnClickListener {
            binding.fullscreenTimeOverlay.visibility = View.GONE
        }

        binding.tvAppPhone.setOnClickListener {
            val intent = Intent(Intent.ACTION_DIAL)
            startActivity(intent)
        }
        binding.tvAppCamera.setOnClickListener {
            fun openCamera() {
                val pm = packageManager
                val camPackages = listOf(
                    "com.android.camera2",
                    "com.android.camera", 
                    "org.codeaurora.snapcam",
                    "com.google.android.GoogleCamera",
                    "com.sec.android.app.camera"
                )
                for (pkg in camPackages) {
                    val launchIntent = pm.getLaunchIntentForPackage(pkg)
                    if (launchIntent != null) {
                        startActivity(launchIntent)
                        return
                    }
                }
                val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                val resolvedApps = pm.queryIntentActivities(intent, 0)
                if (resolvedApps.isNotEmpty()) {
                    val cameraApp = resolvedApps[0].activityInfo
                    val explicit = Intent()
                    explicit.setClassName(cameraApp.packageName, cameraApp.name)
                    startActivity(explicit)
                    return
                }
                try {
                    startActivity(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
                } catch (e: Exception) {
                    android.widget.Toast.makeText(this@MainActivity, "Camera not found", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            openCamera()
        }
        
        binding.tvAppSlot1.setOnClickListener { launchSlot(1, it) }
        binding.tvAppSlot2.setOnClickListener { launchSlot(2, it) }
        binding.tvAppSlot3.setOnClickListener { launchSlot(3, it) }

        binding.tvAppSlot1.setOnLongClickListener { showSlotOptions(1); true }
        binding.tvAppSlot2.setOnLongClickListener { showSlotOptions(2); true }
        binding.tvAppSlot3.setOnLongClickListener { showSlotOptions(3); true }
    }

    private fun animateClick(view: View, action: () -> Unit) {
        view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).withEndAction {
            view.animate().scaleX(1f).scaleY(1f).setDuration(80).withEndAction {
                action()
            }
        }.start()
    }

    private fun updateHomescreenApps() {
        updateSlotUI(1, binding.tvAppSlot1)
        updateSlotUI(2, binding.tvAppSlot2)
        updateSlotUI(3, binding.tvAppSlot3)
    }

    private fun updateSlotUI(slot: Int, tv: TextView) {
        val packageName = PrefsManager.getHomeApp(slot)
        if (!packageName.isNullOrEmpty()) {
            val custom = PrefsManager.getCustomName(packageName)
            if (!custom.isNullOrEmpty()) {
                tv.text = custom
            } else {
                try {
                    val info = packageManager.getApplicationInfo(packageName, 0)
                    tv.text = packageManager.getApplicationLabel(info).toString()
                } catch (e: Exception) {
                    tv.text = "App $slot"
                }
            }
        } else {
            tv.text = "+ add app"
        }
    }

    private fun launchSlot(slot: Int, view: View) {
        animateClick(view) {
            val pkg = PrefsManager.getHomeApp(slot)
            if (!pkg.isNullOrEmpty()) {
                launchApp(pkg, null)
            } else {
                openAppPicker(slot)
            }
        }
    }

    private fun launchApp(packageName: String, view: View?) {
        val action = {
            val limitMs = PrefsManager.getLimitMs(packageName)
            val elapsed = System.currentTimeMillis() - PrefsManager.getLimitStart(packageName)
            if (limitMs > 0 && elapsed < limitMs) {
                val intent = packageManager.getLaunchIntentForPackage(packageName)
                if (intent != null) startActivity(intent)
            } else if (limitMs > 0 && elapsed >= limitMs) {
                val intent = Intent(this, TimeLimitActivity::class.java)
                intent.putExtra("packageName", packageName)
                
                var appName = packageName
                try {
                    val custom = PrefsManager.getCustomName(packageName)
                    if (!custom.isNullOrEmpty()) {
                        appName = custom
                    } else {
                        val info = packageManager.getApplicationInfo(packageName, 0)
                        appName = packageManager.getApplicationLabel(info).toString()
                    }
                } catch (e: Exception) {}
                intent.putExtra("appName", appName)
                
                startActivity(intent)
            } else {
                val intent = packageManager.getLaunchIntentForPackage(packageName)
                if (intent != null) startActivity(intent)
            }
        }
        
        if (view != null) animateClick(view, action) else action()
    }

    private fun showSlotOptions(slot: Int) {
        val pkg = PrefsManager.getHomeApp(slot)
        if (pkg.isNullOrEmpty()) {
            openAppPicker(slot)
            return
        }
        
        val bs = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_home_options, null)
        bs.setContentView(view)

        view.findViewById<View>(R.id.btnChangeApp).setOnClickListener {
            bs.dismiss()
            openAppPicker(slot)
        }

        view.findViewById<View>(R.id.btnRemoveApp).setOnClickListener {
            bs.dismiss()
            PrefsManager.setHomeApp(slot, "")
            PrefsManager.clearLimit(pkg)
            updateHomescreenApps()
        }

        bs.show()
    }

    private fun openAppPicker(slot: Int) {
        val intent = Intent(this, AppPickerActivity::class.java)
        intent.putExtra("slot", slot)
        startActivity(intent)
    }

    private fun setupGestures() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val diffY = e2.y - e1.y
                val diffX = e2.x - e1.x

                if (Math.abs(diffY) > Math.abs(diffX)) {
                    if (Math.abs(diffY) > 100 && Math.abs(velocityY) > 100) {
                        if (diffY > 0) {
                            startActivity(Intent(this@MainActivity, QuickSettingsActivity::class.java))
                            overridePendingTransition(R.anim.slide_down_enter, 0)
                        } else {
                            startActivity(Intent(this@MainActivity, AppDrawerActivity::class.java))
                            overridePendingTransition(R.anim.slide_up_enter, 0)
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
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

    fun startNotificationBlocker() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (!Settings.canDrawOverlays(this)) {
                // Request overlay permission
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, 3001)
                return
            }
        }
        val intent = Intent(this, NotificationBlockerService::class.java)
        startService(intent)
    }

    fun stopNotificationBlocker() {
        val intent = Intent(this, NotificationBlockerService::class.java)
        intent.action = "STOP"
        startService(intent)
    }

    fun requestAllPermissionsOnFirstLaunch() {
        val prefs = getSharedPreferences(
            "miss_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("first_launch_done", false)) 
            return
        prefs.edit()
            .putBoolean("first_launch_done", true).apply()

        // 1. Overlay permission
        if (Build.VERSION.SDK_INT >= 23 &&
            !Settings.canDrawOverlays(this)) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("MISS Minimal Setup")
                .setMessage("Step 1/3: Allow display over " +
                    "other apps — needed to block system " +
                    "notification panel.")
                .setPositiveButton("Grant") { _, _ ->
                    startActivity(Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:$packageName")))
                }.setCancelable(false).show()
            return
        }

        // 2. DND
        val nm = getSystemService(Context.NOTIFICATION_SERVICE)
            as android.app.NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("MISS Minimal Setup")
                .setMessage("Step 2/3: Allow Do Not Disturb " +
                    "access — needed for Strict Mode.")
                .setPositiveButton("Grant") { _, _ ->
                    startActivity(Intent(
                        Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                }.setNegativeButton("Skip", null).show()
        }

        // 3. Accessibility
        checkAndPromptAccessibility()
    }

    fun checkAndPromptAccessibility() {
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.contains(packageName) == true
        
        if (!enabled) {
            val asked = getSharedPreferences(
                "miss_prefs", Context.MODE_PRIVATE)
                .getBoolean("asked_accessibility", false)
            if (!asked) {
                getSharedPreferences("miss_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("asked_accessibility", true)
                    .apply()
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Block System Panel?")
                    .setMessage(
                        "Enable Accessibility Service so swipe-down " +
                        "doesn't open system notifications while " +
                        "launcher is active.\n\n" +
                        "Settings → Accessibility → Installed Apps " +
                        "→ MISS Minimal → ON")
                    .setPositiveButton("Open Settings") { _, _ ->
                        startActivity(Intent(
                            Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                    .setNegativeButton("Skip", null)
                    .show()
            }
        }
    }
}
