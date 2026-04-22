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
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
import android.view.View

class QuickSettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityQuickSettingsBinding
    private lateinit var audioManager: AudioManager
    private lateinit var wifiManager: WifiManager
    private lateinit var cameraManager: CameraManager
    private lateinit var vibrator: Vibrator
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

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
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
        
        vibrator = if (Build.VERSION.SDK_INT >= 31) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

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

        val filter = IntentFilter().apply {
            // addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            // addAction("android.net.wifi.WIFI_AP_STATE_CHANGED")
            // addAction("android.location.PROVIDERS_CHANGED")
            addAction(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(android.net.wifi.WifiManager.WIFI_STATE_CHANGED_ACTION)
        }
        registerReceiver(stateReceiver, filter)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(volumeReceiver)
        unregisterReceiver(stateReceiver)
        contentResolver.unregisterContentObserver(brightnessObserver)
    }

    private fun setupConnectivity() {
        // WIFI — use system panel (works on all Android 10+ phones)
        binding.btnWifi.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startActivity(Intent(Settings.Panel.ACTION_WIFI))
            } else {
                @Suppress("DEPRECATION")
                wifiManager.isWifiEnabled = !wifiManager.isWifiEnabled
                android.os.Handler(Looper.getMainLooper()).postDelayed({ updateAllStates() }, 600)
            }
        }
        binding.btnWifi.setOnLongClickListener {
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            true
        }

        // DATA — use internet connectivity panel
        binding.btnData.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startActivity(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
            } else {
                startActivity(Intent(Settings.ACTION_DATA_ROAMING_SETTINGS))
            }
        }
        binding.btnData.setOnLongClickListener {
            startActivity(Intent(Settings.ACTION_DATA_ROAMING_SETTINGS))
            true
        }

        // BLUETOOTH — use system settings (no dedicated Panel for Bluetooth in Android 10+)
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
        /*
        binding.btnLocation.setOnClickListener {
            toggleLocation(this)
        }
        */

        // HOTSPOT
        /*
        binding.btnHotspot.setOnClickListener {
            toggleHotspot(this)
        }
        binding.btnHotspot.setOnLongClickListener {
            try {
                val i = Intent(Intent.ACTION_MAIN)
                i.setClassName("com.android.settings", "com.android.settings.TetherSettings")
                startActivity(i)
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
            }
            true
        }
        */

        // AIRPLANE
        /*
        binding.btnAirplane.setOnClickListener {
            toggleAirplaneMode(this)
        }
        */

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

    private fun View.addClickFeedback() {
        setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    vibrateTick()
                    v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(100).start()
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                }
            }
            false
        }
    }

    private fun vibrateTick() {
        if (Build.VERSION.SDK_INT >= 29) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(10)
        }
    }

    private fun animateClick(view: View) {
        view.animate()
            .scaleX(0.92f)
            .scaleY(0.92f)
            .setDuration(100)
            .withEndAction {
                view.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(150)
                    .start()
            }
            .start()
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
        val isDndOn = nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        setButtonState(binding.btnDnd, isDndOn, dpToPx)

        // Flashlight
        setButtonState(binding.btnFlashlightText, torchState, dpToPx)

        // Rotate
        val isRotateOn = Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
        setButtonState(binding.btnRotate, isRotateOn, dpToPx)

        // Location
        // setButtonState(binding.btnLocation, isLocationOn, dpToPx)

        // Hotspot
        // setButtonState(binding.btnHotspot, isHotspotEnabled(this), dpToPx)

        // Airplane
        // setButtonState(binding.btnAirplane, isAirplaneOn, dpToPx)
        
        updateSoundUI()
        updateDisplayUI()
    }

    private fun setButtonState(view: View, active: Boolean, dpToPx: Float) {
        val color = if (active) 
            ContextCompat.getColor(this, R.color.white) 
        else 
            ContextCompat.getColor(this, R.color.text_secondary)
        
        if (view is TextView) {
            view.setTextColor(color)
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12 * dpToPx
                setStroke((1.5 * dpToPx).toInt(), color)
                if (active) {
                    setColor(color)
                } else {
                    setColor(android.graphics.Color.TRANSPARENT)
                }
            }
            view.background = drawable
            if (active) {
                view.setTextColor(ContextCompat.getColor(this, R.color.black))
            }
        }
    }

    private fun setupSound() {
        binding.btnSoundNormal.setOnClickListener {
            audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
            updateSoundUI()
        }
        binding.btnSoundVibrate.setOnClickListener {
            audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
            updateSoundUI()
        }
        binding.btnSoundSilent.setOnClickListener {
            audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
            updateSoundUI()
        }

        binding.seekVolume.max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        binding.seekVolume.progress = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        binding.seekVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun updateSoundUI() {
        val dpToPx = resources.displayMetrics.density
        val mode = audioManager.ringerMode
        setButtonState(binding.btnSoundNormal, mode == AudioManager.RINGER_MODE_NORMAL, dpToPx)
        setButtonState(binding.btnSoundVibrate, mode == AudioManager.RINGER_MODE_VIBRATE, dpToPx)
        setButtonState(binding.btnSoundSilent, mode == AudioManager.RINGER_MODE_SILENT, dpToPx)
        
        binding.seekVolume.progress = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    }

    private fun setupDisplay() {
        binding.btnAutoBrightness.setOnClickListener {
            if (Settings.System.canWrite(this)) {
                val mode = if (Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0) == 1) 0 else 1
                Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, mode)
                updateDisplayUI()
            } else {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        }

        binding.seekBrightness.max = 255
        binding.seekBrightness.progress = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        binding.seekBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && Settings.System.canWrite(this@QuickSettingsActivity)) {
                    Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun updateDisplayUI() {
        val dpToPx = resources.displayMetrics.density
        val isAuto = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0) == 1
        setButtonState(binding.btnAutoBrightness, isAuto, dpToPx)
        binding.seekBrightness.progress = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
    }

    private fun toggleLocation(context: Context) {
        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    }

    private fun toggleAirplaneMode(context: Context) {
        startActivity(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
    }

    private fun toggleHotspot(context: Context) {
        // Since Android 8.0+, we can\u0027t easily toggle hotspot without high-level permissions.
        // Opening settings is the safest way.
        try {
            val intent = Intent()
            intent.setClassName("com.android.settings", "com.android.settings.TetherSettings")
            startActivity(intent)
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
        }
    }

    private fun isHotspotEnabled(context: Context): Boolean {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val method = wm.javaClass.getDeclaredMethod("getWifiApState")
            val state = method.invoke(wm) as Int
            state == 13 // WIFI_AP_STATE_ENABLED
        } catch (e: Exception) {
            false
        }
    }
}
