package com.minimalist.launcher

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.net.ConnectivityManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.telephony.TelephonyManager
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.minimalist.launcher.databinding.ActivityQuickSettingsBinding
import android.graphics.drawable.GradientDrawable
import android.database.ContentObserver
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.view.GestureDetector

class QuickSettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityQuickSettingsBinding
    private lateinit var audioManager: AudioManager
    private lateinit var wifiManager: WifiManager
    private lateinit var cameraManager: CameraManager
    private var torchState = false
    private var cameraId: String? = null

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                updateSoundUI()
            }
        }
    }

    private val brightnessObserver = object : ContentObserver(android.os.Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            updateDisplayUI()
            updateAllStates()
        }
    }

    private lateinit var gestureDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuickSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        setupGestures()

        try {
            cameraId = cameraManager.cameraIdList.firstOrNull()
        } catch (e: Exception) {}

        cameraManager.registerTorchCallback(object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(id: String, enabled: Boolean) {
                if (id == cameraId) {
                    torchState = enabled
                    updateAllStates()
                }
            }
        }, null)

        setupConnectivity()
        setupSound()
        setupDisplay()
    }

    private fun setupGestures() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: android.view.MotionEvent?, e2: android.view.MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val diffY = e2.y - e1.y
                if (diffY < -100 && Math.abs(velocityY) > 100) {
                    finish()
                    overridePendingTransition(0, R.anim.slide_up_exit)
                    return true
                }
                return false
            }
        })
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
    }

    override fun onResume() {
        super.onResume()
        AppFont.applyToActivity(this)
        updateSoundUI()
        updateDisplayUI()
        updateAllStates()
        
        registerReceiver(volumeReceiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"))
        contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
            false, brightnessObserver
        )
        contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE),
            false, brightnessObserver
        )
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(volumeReceiver)
        contentResolver.unregisterContentObserver(brightnessObserver)
    }

    private fun setupConnectivity() {
        // WIFI
        binding.btnWifi.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startActivity(Intent(Settings.Panel.ACTION_WIFI))
            } else {
                @Suppress("DEPRECATION")
                wifiManager.isWifiEnabled = !wifiManager.isWifiEnabled
                updateAllStates()
            }
        }
        binding.btnWifi.setOnLongClickListener {
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            true
        }

        // DATA
        binding.btnData.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val intent = Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                startActivity(intent)
            } else {
                startActivity(Intent(Settings.ACTION_DATA_ROAMING_SETTINGS))
            }
        }
        binding.btnData.setOnLongClickListener {
            startActivity(Intent(Settings.ACTION_DATA_ROAMING_SETTINGS))
            true
        }

        // BLUETOOTH
        binding.btnBluetooth.setOnClickListener {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }
        binding.btnBluetooth.setOnLongClickListener {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            true
        }

        // DND
        binding.btnDnd.setOnClickListener {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.isNotificationPolicyAccessGranted) {
                val currentFilter = nm.currentInterruptionFilter
                if (currentFilter == NotificationManager.INTERRUPTION_FILTER_ALL) {
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
                } else {
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                }
                updateAllStates()
            } else {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            }
        }
        binding.btnDnd.setOnLongClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            true
        }

        // FLASHLIGHT
        binding.btnFlashlightText.setOnClickListener {
            try {
                cameraId?.let { cameraManager.setTorchMode(it, !torchState) }
            } catch (e: Exception) {}
        }

        // ROTATE
        binding.btnRotate.setOnClickListener {
            if (Settings.System.canWrite(this)) {
                val current = Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0)
                Settings.System.putInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (current == 1) 0 else 1)
                updateAllStates()
            } else {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        }

        // LOCATION
        binding.btnLocation.setOnClickListener {
            toggleLocation(this)
        }

        // HOTSPOT
        binding.btnHotspot.setOnClickListener {
            toggleHotspot(this)
        }

        // AIRPLANE
        binding.btnAirplane.setOnClickListener {
            toggleAirplaneMode(this)
        }

        setupMicroInteractions()
    }

    private fun setupMicroInteractions() {
        val buttons = listOf(
            binding.btnWifi, binding.btnData, binding.btnBluetooth, 
            binding.btnDnd, binding.btnFlashlightText, binding.btnRotate,
            binding.btnLocation, binding.btnHotspot, binding.btnAirplane,
            binding.btnAutoBrightness, binding.btnSoundNormal, 
            binding.btnSoundVibrate, binding.btnSoundSilent
        )
        buttons.forEach { it.addClickFeedback() }
    }

    private fun android.view.View.addClickFeedback() {
        setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).start()
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                }
            }
            false
        }
    }

    private fun updateAllStates() {
        val dpToPx = resources.displayMetrics.density

        // Wifi
        setButtonState(binding.btnWifi, wifiManager.isWifiEnabled, dpToPx)

        // Data
        try {
            @Suppress("DEPRECATION")
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            @Suppress("DEPRECATION")
            val isDataOn = cm.activeNetworkInfo?.type == ConnectivityManager.TYPE_MOBILE
            setButtonState(binding.btnData, isDataOn, dpToPx)
        } catch (e: Exception) {
            setButtonState(binding.btnData, false, dpToPx)
        }

        @Suppress("DEPRECATION")
        val btAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
        setButtonState(binding.btnBluetooth, btAdapter?.isEnabled == true, dpToPx)

        // DND
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val isDnd = nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        setButtonState(binding.btnDnd, isDnd, dpToPx)

        // Auto Brightness
        try {
            val isAuto = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
            setButtonState(binding.btnAutoBrightness, isAuto, dpToPx)
        } catch (e: Exception) {}

        // Flashlight
        val flashBg = GradientDrawable()
        flashBg.shape = GradientDrawable.RECTANGLE
        flashBg.cornerRadius = 8f * dpToPx
        if (torchState) {
            flashBg.setColor(0xFF1A1A2E.toInt())
            flashBg.setStroke((1 * dpToPx).toInt(), 0xFF4444AA.toInt())
            binding.btnFlashlightText.setTextColor(0xFFAAAAFF.toInt())
        } else {
            flashBg.setColor(0xFF000000.toInt())
            flashBg.setStroke((1 * dpToPx).toInt(), 0xFF2A2A2A.toInt())
            binding.btnFlashlightText.setTextColor(0xFF666666.toInt())
        }
        binding.btnFlashlightText.background = flashBg

        // Rotate
        val isRotateOn = Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
        setButtonState(binding.btnRotate, isRotateOn, dpToPx)

        // Location
        val lm = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        val isGpsOn = lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
        setButtonState(binding.btnLocation, isGpsOn, dpToPx)

        // Hotspot
        val hotspotOn = try { isHotspotEnabled(wifiManager) } catch (e: Exception) { false }
        setButtonState(binding.btnHotspot, hotspotOn, dpToPx) 

        // Airplane
        val isAirplaneOn = Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
        setButtonState(binding.btnAirplane, isAirplaneOn, dpToPx)
    }

    private fun setButtonState(view: TextView, isActive: Boolean, density: Float) {
        updateToggleState(view, isActive)
    }

    private fun updateToggleState(btn: TextView, isActive: Boolean) {
        val density = resources.displayMetrics.density
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.RECTANGLE
        bg.cornerRadius = 8f * density
        
        if (isActive) {
            // Monochromatic active state (white-ish text, dark blue-grey bg)
            bg.setColor(0xFF1A1A2E.toInt())
            bg.setStroke((1 * density).toInt(), 0xFF4444AA.toInt())
            btn.setTextColor(0xFFAAAAFF.toInt())
        } else {
            // Inactive state (grey text, black bg)
            bg.setColor(0xFF000000.toInt())
            bg.setStroke((1 * density).toInt(), 0xFF2A2A2A.toInt())
            btn.setTextColor(0xFF666666.toInt())
        }
        btn.background = bg
    }

    // ── HOTSPOT HELPER METHODS ──
    private fun toggleHotspot(context: Context) {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val isOn = isHotspotEnabled(wm)
        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        
        // Try reflection-based tethering via ConnectivityManager (works on Android 8+)
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (isOn) {
                // Stop tethering
                val stopMethod = cm.javaClass.getDeclaredMethod("stopTethering", Int::class.java)
                stopMethod.isAccessible = true
                stopMethod.invoke(cm, 0) // 0 = TETHERING_WIFI
                android.widget.Toast.makeText(context, "Hotspot OFF", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                // Start tethering
                val callbackClass = Class.forName("android.net.ConnectivityManager\$OnStartTetheringCallback")
                val callback = try {
                    callbackClass.getDeclaredConstructor().newInstance()
                } catch (e: Exception) {
                    // Fallback for some ROMs where constructor is not accessible
                    java.lang.reflect.Proxy.newProxyInstance(
                        callbackClass.classLoader,
                        arrayOf(callbackClass)
                    ) { _, _, _ -> null }
                }
                
                val startMethod = cm.javaClass.getDeclaredMethod(
                    "startTethering", Int::class.java, Boolean::class.java, callbackClass
                )
                startMethod.isAccessible = true
                startMethod.invoke(cm, 0, false, callback)
                android.widget.Toast.makeText(context, "Hotspot ON", android.widget.Toast.LENGTH_SHORT).show()
            }
            // Update UI after a brief delay
            binding.btnHotspot.postDelayed({ updateAllStates() }, 800)
            return
        } catch (e: Exception) {
            // Reflection failed
        }
        
        // Fallback: try the old WifiManager startSoftAp / stopSoftAp
        try {
            if (isOn) {
                stopHotspot(wm)
            } else {
                startHotspot(wm, context)
            }
            binding.btnHotspot.postDelayed({ updateAllStates() }, 800)
            return
        } catch (e: Exception) {
            // Also failed
        }
        
        // Last resort: open tethering settings directly
        try {
            val intent = Intent(Intent.ACTION_MAIN)
            intent.setClassName("com.android.settings", "com.android.settings.TetherSettings")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    private fun isHotspotEnabled(wifiManager: WifiManager): Boolean {
        return try {
            val method = wifiManager.javaClass.getDeclaredMethod("isWifiApEnabled")
            method.isAccessible = true
            method.invoke(wifiManager) as Boolean
        } catch (e: Exception) { false }
    }

    private fun animateClick(view: View, action: () -> Unit) {
        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        view.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).withEndAction {
            view.animate().scaleX(1f).scaleY(1f).setDuration(100).withEndAction {
                action()
            }
        }.start()
    }

    private fun startHotspot(wifiManager: WifiManager, context: Context) {
        try {
            val method = wifiManager.javaClass.getDeclaredMethod("setWifiApEnabled",
                android.net.wifi.WifiConfiguration::class.java, Boolean::class.java)
            method.isAccessible = true
            method.invoke(wifiManager, null, true)
        } catch (e1: Exception) {
            try {
                val method = wifiManager.javaClass.getDeclaredMethod("startSoftAp",
                    android.net.wifi.WifiConfiguration::class.java)
                method.isAccessible = true
                method.invoke(wifiManager, null)
            } catch (e2: Exception) { throw e2 }
        }
    }

    private fun stopHotspot(wifiManager: WifiManager) {
        try {
            val method = wifiManager.javaClass.getDeclaredMethod("setWifiApEnabled",
                android.net.wifi.WifiConfiguration::class.java, Boolean::class.java)
            method.isAccessible = true
            method.invoke(wifiManager, null, false)
        } catch (e1: Exception) {
            try {
                val method = wifiManager.javaClass.getDeclaredMethod("stopSoftAp")
                method.isAccessible = true
                method.invoke(wifiManager)
            } catch (e2: Exception) { throw e2 }
        }
    }

    // ── LOCATION HELPER METHODS ──
    private fun toggleLocation(context: Context) {
        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        // On Android 10+, apps cannot toggle location directly.
        // Best approach: open the Location settings in a focused, minimal way.
        // We use startActivityForResult so we can update the toggle when user returns.
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivityForResult(intent, 5001)
    }

    private fun isLocationEnabled(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= 28) {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            lm.isLocationEnabled
        } else {
            try {
                Settings.Secure.getInt(context.contentResolver, Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF) != Settings.Secure.LOCATION_MODE_OFF
            } catch (e: Exception) { false }
        }
    }

    // ── AIRPLANE HELPER METHODS ──
    private fun toggleAirplaneMode(context: Context) {
        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        // On Android 10+, non-system apps CANNOT write Settings.Global.AIRPLANE_MODE_ON.
        // The ONLY approach is to show the in-app internet connectivity panel.
        if (Build.VERSION.SDK_INT >= 29) {
            // This opens a BOTTOM SHEET panel inside the app — NOT a full settings page
            val intent = Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
            startActivityForResult(intent, 5002)
        } else {
            // Pre-Android 10: direct toggle
            try {
                val isCurrentlyOn = Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
                val newState = !isCurrentlyOn
                Settings.Global.putInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, if (newState) 1 else 0)
                val intent = Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED).apply { putExtra("state", newState) }
                context.sendBroadcast(intent)
                binding.btnAirplane.postDelayed({ updateAllStates() }, 500)
            } catch (e: SecurityException) {
                val intent = Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                startActivityForResult(intent, 5002)
            }
        }
    }

    private fun isAirplaneModeOn(context: Context): Boolean {
        return Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // When user returns from Location or Airplane panel, refresh all states
        if (requestCode == 5001 || requestCode == 5002) {
            updateAllStates()
        }
    }

    private fun setupSound() {
        binding.btnSoundNormal.setOnClickListener { setRingerMode(AudioManager.RINGER_MODE_NORMAL) }
        binding.btnSoundVibrate.setOnClickListener { setRingerMode(AudioManager.RINGER_MODE_VIBRATE) }
        binding.btnSoundSilent.setOnClickListener { setRingerMode(AudioManager.RINGER_MODE_SILENT) }

        binding.seekVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val targetVol = (progress * maxVol) / 100
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setRingerMode(mode: Int) {
        try {
            audioManager.ringerMode = mode
            
            // Sync DND state with ringer mode for a more predictable 'Mute'
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.isNotificationPolicyAccessGranted) {
                if (mode == AudioManager.RINGER_MODE_SILENT) {
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
                } else {
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                }
            }
            
            updateSoundUI()
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            startActivity(intent)
        }
    }

    private fun updateSoundUI() {
        val mode = audioManager.ringerMode
        val activeColor = ContextCompat.getColor(this, R.color.white)
        val inactiveColor = ContextCompat.getColor(this, R.color.grey_666)

        binding.btnSoundNormal.setTextColor(if (mode == AudioManager.RINGER_MODE_NORMAL) activeColor else inactiveColor)
        binding.btnSoundVibrate.setTextColor(if (mode == AudioManager.RINGER_MODE_VIBRATE) activeColor else inactiveColor)
        binding.btnSoundSilent.setTextColor(if (mode == AudioManager.RINGER_MODE_SILENT) activeColor else inactiveColor)

        // Update Volume SeekBar
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val progress = if (maxVol > 0) (curVol * 100) / maxVol else 0
        binding.seekVolume.progress = progress
    }

    private fun setupDisplay() {
        binding.btnAutoBrightness.setOnClickListener {
            if (Settings.System.canWrite(this)) {
                try {
                    val isAuto = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                    if (isAuto) {
                        Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                    } else {
                        Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
                        val lp = window.attributes
                        lp.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                        window.attributes = lp
                    }
                    updateAllStates()
                } catch (e: Exception) {}
            } else {
                startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                })
            }
        }

        binding.seekBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && Settings.System.canWrite(this@QuickSettingsActivity)) {
                    val brightness = (progress * 255) / 100
                    Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, brightness)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun updateDisplayUI() {
        try {
            val curBrightness = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            binding.seekBrightness.progress = (curBrightness * 100) / 255
        } catch (e: Exception) {}
    }
}
