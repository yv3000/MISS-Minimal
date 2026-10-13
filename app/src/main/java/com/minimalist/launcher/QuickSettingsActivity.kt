package com.minimalist.launcher

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
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
    private lateinit var cameraManager: CameraManager
    private lateinit var vibrator: Vibrator
    private var torchState: Boolean? = null
    private var cameraId: String? = null
    private val notificationAdapter = NotificationAdapter()

    private val connectivity = ConnectivityControls(this) { updateAllStates() }


    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) {
            if (id == cameraId && !isDestroyed) {
                torchState = enabled
                updateAllStates()
            }
        }

        override fun onTorchModeUnavailable(id: String) {
            if (id == cameraId && !isDestroyed) {
                if (torchState == true) {
                    Toast.makeText(this@QuickSettingsActivity, "Flashlight unavailable", Toast.LENGTH_SHORT).show()
                }
                torchState = null
                updateAllStates()
            }
        }
    }

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

    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = notificationAdapter.refresh()
    }

    private lateinit var gestureDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuickSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
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
            cameraId = cameraManager.cameraIdList.firstOrNull {
                cameraManager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            cameraManager.registerTorchCallback(torchCallback, android.os.Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            Toast.makeText(this, "Flashlight unavailable: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }

        setupConnectivity()
        setupSound()
        setupDisplay()
        binding.rvNotifications.layoutManager = LinearLayoutManager(this)
        binding.rvNotifications.adapter = notificationAdapter
        notificationAdapter.attachSwipe(binding.rvNotifications)
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

    /**
     * The panel's root is a ScrollView, which consumes every vertical gesture, so an
     * Activity.onTouchEvent override is never reached — that is why swipe-up-to-close looked
     * "broken". dispatchTouchEvent sees the whole gesture before any child does; we only observe
     * it here (return value untouched) so scrolling and button taps keep working.
     */
    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        gestureDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onResume() {
        super.onResume()
        updateSoundUI()
        updateDisplayUI()
        updateAllStates()
        
        ContextCompat.registerReceiver(
            this,
            volumeReceiver,
            IntentFilter("android.media.VOLUME_CHANGED_ACTION"),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
            false, brightnessObserver
        )
        contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE),
            false, brightnessObserver
        )

        LocalBroadcastManager.getInstance(this).registerReceiver(
            notificationReceiver,
            IntentFilter(NotificationService.ACTION_NOTIFY_UPDATED)
        )
        if (NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)) {
            notificationAdapter.refresh()
        } else {
            notificationAdapter.submit(emptyList())
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(volumeReceiver)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(notificationReceiver)
        contentResolver.unregisterContentObserver(brightnessObserver)
    }

    private fun setupConnectivity() {
        binding.btnWifi.setOnClickListener { connectivity.toggle("wifi") }
        binding.btnWifi.setOnLongClickListener {
            openSettings(Settings.ACTION_WIFI_SETTINGS)
            true
        }

        binding.btnData.setOnClickListener { connectivity.toggle("data") }
        binding.btnData.setOnLongClickListener {
            openSettings(Settings.ACTION_DATA_USAGE_SETTINGS)
            true
        }

        binding.btnBluetooth.setOnClickListener { connectivity.toggle("bluetooth") }
        binding.btnBluetooth.setOnLongClickListener {
            openSettings(Settings.ACTION_BLUETOOTH_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS)
            true
        }

        // DND
        binding.btnDnd.setOnClickListener {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            try {
                if (!nm.isNotificationPolicyAccessGranted) {
                    openSettings(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    return@setOnClickListener
                }
                val currentFilter = nm.currentInterruptionFilter
                if (currentFilter == NotificationManager.INTERRUPTION_FILTER_ALL) {
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
                } else {
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                }
                updateAllStates()
            } catch (_: SecurityException) {
                openSettings(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            }
        }
        binding.btnDnd.setOnLongClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            true
        }

        // FLASHLIGHT
        binding.btnFlashlightText.setOnClickListener { toggleFlashlight() }

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

        binding.btnLocation.visibility = View.VISIBLE
        binding.btnLocation.setOnClickListener { connectivity.toggle("location") }
        binding.btnLocation.setOnLongClickListener {
            openSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            true
        }

        binding.btnHotspot.visibility = View.VISIBLE
        binding.btnHotspot.setOnClickListener { connectivity.toggle("hotspot") }
        binding.btnHotspot.setOnLongClickListener {
            openSettings("android.settings.TETHER_SETTINGS", Settings.ACTION_WIRELESS_SETTINGS)
            true
        }

        binding.btnAirplane.visibility = View.VISIBLE
        binding.btnAirplane.setOnClickListener { connectivity.toggle("airplane") }
        binding.btnAirplane.setOnLongClickListener {
            openSettings(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
            true
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

    private fun updateAllStates() {
        val dpToPx = resources.displayMetrics.density

        // Wifi
        setButtonState(binding.btnWifi, connectivity.state("wifi"), dpToPx)
        setButtonState(binding.btnData, connectivity.state("data"), dpToPx)
        setButtonState(binding.btnBluetooth, connectivity.state("bluetooth"), dpToPx)

        // DND
        setButtonState(binding.btnDnd, stateOf {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        }, dpToPx)

        // Flashlight
        setButtonState(binding.btnFlashlightText, torchState, dpToPx)

        // Rotate
        setButtonState(binding.btnRotate, stateOf {
            Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
        }, dpToPx)

        // Location
        setButtonState(binding.btnLocation, connectivity.state("location"), dpToPx)

        // Hotspot
        setButtonState(binding.btnHotspot, connectivity.state("hotspot"), dpToPx)

        // Airplane
        setButtonState(binding.btnAirplane, connectivity.state("airplane"), dpToPx)
        
        updateSoundUI()
        updateDisplayUI()
    }

    private fun stateOf(read: () -> Boolean) = try {
        read()
    } catch (_: SecurityException) {
        null
    }

    private fun setButtonState(view: View, active: Boolean?, dpToPx: Float) {
        val color = if (active == true)
            ContextCompat.getColor(this, R.color.white) 
        else 
            ContextCompat.getColor(this, R.color.text_secondary)
        
        if (view is TextView) {
            val label = view.text.toString().removeSuffix(" ?")
            view.text = label + if (active == null) " ?" else ""
            view.contentDescription = "$label, ${when (active) { true -> "ON"; false -> "OFF"; null -> "UNKNOWN" }}"
            view.setTextColor(color)
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12 * dpToPx
                setStroke((1.5 * dpToPx).toInt(), color)
                if (active == true) {
                    setColor(color)
                } else {
                    setColor(android.graphics.Color.TRANSPARENT)
                }
            }
            view.background = drawable
            if (active == true) {
                view.setTextColor(ContextCompat.getColor(this, R.color.black))
            }
        }
    }

    private fun setupSound() {
        binding.btnSoundNormal.setOnClickListener {
            setRingerMode(AudioManager.RINGER_MODE_NORMAL)
        }
        binding.btnSoundVibrate.setOnClickListener {
            setRingerMode(AudioManager.RINGER_MODE_VIBRATE)
        }
        binding.btnSoundSilent.setOnClickListener {
            setRingerMode(AudioManager.RINGER_MODE_SILENT)
        }

        binding.seekVolume.contentDescription = "Media volume"
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

    private fun setRingerMode(mode: Int) {
        try {
            audioManager.ringerMode = mode
            updateAllStates()
        } catch (_: SecurityException) {
            openSettings(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        }
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

        binding.seekBrightness.contentDescription = "Brightness"
        binding.seekBrightness.max = 255
        binding.seekBrightness.progress = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        binding.seekBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && Settings.System.canWrite(this@QuickSettingsActivity)) {
                    Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                    Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                if (!Settings.System.canWrite(this@QuickSettingsActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                        data = Uri.parse("package:$packageName")
                    })
                }
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun updateDisplayUI() {
        val dpToPx = resources.displayMetrics.density
        val isAuto = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0) == 1
        setButtonState(binding.btnAutoBrightness, isAuto, dpToPx)
        binding.seekBrightness.progress = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
    }

    private fun openSettings(primary: String, fallback: String = Settings.ACTION_SETTINGS) {
        try {
            startActivity(Intent(primary))
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(fallback))
        }
    }

    private fun toggleFlashlight() {

        try {
            val id = cameraId ?: cameraManager.cameraIdList.firstOrNull {
                cameraManager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }?.also { cameraId = it }
            if (id == null) {
                Toast.makeText(this, "Flashlight unavailable", Toast.LENGTH_SHORT).show()
                return
            }
            // Retry registration if camera access was unavailable during onCreate.
            cameraManager.registerTorchCallback(torchCallback, android.os.Handler(Looper.getMainLooper()))
            cameraManager.setTorchMode(id, torchState != true)
        } catch (e: Exception) {
            torchState = null
            updateAllStates()
            Toast.makeText(this, "Unable to change flashlight: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        connectivity.close()
        cameraManager.unregisterTorchCallback(torchCallback)
        super.onDestroy()
    }
}
