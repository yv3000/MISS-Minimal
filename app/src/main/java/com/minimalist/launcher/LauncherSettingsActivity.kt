package com.minimalist.launcher

import android.Manifest
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.minimalist.launcher.databinding.ActivityLauncherSettingsBinding

class LauncherSettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLauncherSettingsBinding
    private lateinit var audioManager: AudioManager
    private lateinit var cameraManager: CameraManager
    private var torchState: Boolean? = null
    private var cameraId: String? = null

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
                    Toast.makeText(this@LauncherSettingsActivity, "Flashlight unavailable", Toast.LENGTH_SHORT).show()
                }
                torchState = null
                updateAllStates()
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val stateRunnable = object : Runnable {
        override fun run() {
            updateAllStates()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLauncherSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        try {
            cameraId = cameraManager.cameraIdList.firstOrNull {
                cameraManager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            cameraManager.registerTorchCallback(torchCallback, handler)
        } catch (e: Exception) {
            Toast.makeText(this, "Flashlight unavailable: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }

        setupConnectivity()
        setupDisplay()
        setupSound()
        setupAppearance()

        findViewById<TextView>(R.id.tvTermsLink).setOnClickListener {
            startActivity(Intent(this, TermsActivity::class.java))
        }
    }

    private fun setupAppearance() {
        binding.btnFontSize.setOnClickListener {
            val sizes = arrayOf("12", "14", "16", "18", "20", "22", "24")
            val currentSize = AppFont.get(this).toInt().toString()
            var currentIndex = sizes.indexOf(currentSize)
            if (currentIndex == -1) currentIndex = 0 // Default to 12

            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Select Font Size")
                .setSingleChoiceItems(sizes, currentIndex) { dialog, which ->
                    val selected = sizes[which].toFloat()
                    PrefsManager.setFontSize(this, selected)
                    AppFont.applyToActivity(this)
                    binding.tvFontSizeState.text = selected.toString()
                    dialog.dismiss()
                }.show()
        }
    }

    override fun onResume() {
        super.onResume()
        AppFont.applyToActivity(this)
        updateAllStates()
        handler.post(stateRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(stateRunnable)
    }

    private fun setupConnectivity() {
        binding.btnWifi.setOnClickListener {
            vibrate()
            connectivity.toggle("wifi")
        }
        binding.btnWifi.setOnLongClickListener {
            openSettings(Settings.ACTION_WIFI_SETTINGS)
            true
        }

        binding.btnData.setOnClickListener {
            vibrate()
            connectivity.toggle("data")
        }
        binding.btnData.setOnLongClickListener {
            openSettings(Settings.ACTION_DATA_USAGE_SETTINGS)
            true
        }

        binding.btnBluetooth.setOnClickListener {
            vibrate()
            connectivity.toggle("bluetooth")
        }
        binding.btnBluetooth.setOnLongClickListener {
            openSettings(Settings.ACTION_BLUETOOTH_SETTINGS)
            true
        }

        binding.btnDnd.setOnClickListener {
            vibrate()
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

        binding.btnFlashlight.setOnClickListener {
            vibrate()
            toggleFlashlight()
        }

        binding.btnRotate.setOnClickListener {
            vibrate()
            if (Settings.System.canWrite(this)) {
                val current = Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0)
                Settings.System.putInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (current == 1) 0 else 1)
                updateAllStates()
            } else {
                startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                })
            }
        }

        binding.btnAirplane.setOnClickListener {
            vibrate()
            connectivity.toggle("airplane")
        }
        binding.btnAirplane.setOnLongClickListener {
            openSettings(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
            true
        }

        binding.btnLocation.setOnClickListener {
            vibrate()
            connectivity.toggle("location")
        }
        binding.btnLocation.setOnLongClickListener {
            openSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            true
        }

        binding.btnHotspot.setOnClickListener {
            vibrate()
            connectivity.toggle("hotspot")
        }
        binding.btnHotspot.setOnLongClickListener {
            openSettings("android.settings.TETHER_SETTINGS", Settings.ACTION_WIRELESS_SETTINGS)
            true
        }
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
            cameraManager.registerTorchCallback(torchCallback, handler)
            cameraManager.setTorchMode(id, torchState != true)
        } catch (e: Exception) {
            torchState = null
            updateAllStates()
            Toast.makeText(this, "Unable to change flashlight: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun vibrate() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        if (Build.VERSION.SDK_INT >= 29) {
            vibrator.vibrate(android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_CLICK))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(10)
        }
    }

    private fun setupDisplay() {
        binding.seekBrightness.contentDescription = "Brightness"
        try {
            val curBrightness = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            binding.seekBrightness.progress = curBrightness
        } catch (e: Exception) {}

        binding.seekBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    if (Settings.System.canWrite(this@LauncherSettingsActivity)) {
                        Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                        Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, progress)
                        updateAllStates()
                    } else {
                        startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                            data = Uri.parse("package:$packageName")
                        })
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupSound() {
        binding.seekVolume.contentDescription = "Media volume"
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        
        binding.seekVolume.max = maxVol
        binding.seekVolume.progress = curVol

        binding.seekVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
                    updateAllStates()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun updateAllStates() {
        // Wifi
        setLabelState(binding.tvWifiState, connectivity.state("wifi"))

        // Data
        setLabelState(binding.tvDataState, connectivity.state("data"))

        // Bluetooth
        setLabelState(binding.tvBluetoothState, connectivity.state("bluetooth"))

        // Font Size
        binding.tvFontSizeState.text = AppFont.get(this).toString()

        // DND
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val isDnd = nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        setLabelState(binding.tvDndState, isDnd)

        // Flashlight
        setLabelState(binding.tvFlashlightState, torchState)

        // Rotate
        val isRotateOn = Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
        setLabelState(binding.tvRotateState, isRotateOn)

        // Airplane
        setLabelState(binding.tvAirplaneState, connectivity.state("airplane"))

        // Location
        setLabelState(binding.tvLocationState, connectivity.state("location"))

        // Hotspot
        setLabelState(binding.tvHotspotState, connectivity.state("hotspot"))

        // Brightness
        try {
            val isAuto = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
            if (isAuto) {
                binding.tvBrightnessState.text = "Auto"
                binding.tvBrightnessState.setTextColor(0xFFAAAAFF.toInt())
            } else {
                val curBrightness = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                val pct = (curBrightness * 100) / 255
                binding.tvBrightnessState.text = "$pct%"
                binding.tvBrightnessState.setTextColor(0xFF444444.toInt())
                if (binding.seekBrightness.progress != curBrightness) {
                    binding.seekBrightness.progress = curBrightness
                }
            }
        } catch (e: Exception) {}

        // Volume
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        binding.tvVolumeState.text = "$curVol/$maxVol"
        binding.tvVolumeState.setTextColor(if (curVol > 0) 0xFFAAAAFF.toInt() else 0xFF444444.toInt())
        if (binding.seekVolume.progress != curVol) {
            binding.seekVolume.progress = curVol
        }
    }

    private fun setLabelState(tv: TextView, isActive: Boolean?) {
        tv.text = when (isActive) { true -> "ON"; false -> "OFF"; null -> "UNKNOWN" }
        tv.setTextColor(if (isActive == true) 0xFFAAAAFF.toInt() else 0xFF444444.toInt())
        val row = tv.parent as ViewGroup
        val label = (row.getChildAt(0) as TextView).text
        row.contentDescription = "$label, ${tv.text}"
    }

    override fun onDestroy() {
        connectivity.close()
        handler.removeCallbacks(stateRunnable)
        cameraManager.unregisterTorchCallback(torchCallback)
        super.onDestroy()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(0, R.anim.slide_down_exit)
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, R.anim.slide_down_exit)
    }

    // Moved font logic to AppFont.kt
}
