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
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telephony.TelephonyManager
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.minimalist.launcher.databinding.ActivityLauncherSettingsBinding

class LauncherSettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLauncherSettingsBinding
    private lateinit var audioManager: AudioManager
    private lateinit var wifiManager: WifiManager
    private lateinit var cameraManager: CameraManager
    private var torchState = false
    private var cameraId: String? = null

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
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

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
        setupDisplay()
        setupSound()
        setupAppearance()

        findViewById<TextView>(R.id.tvTermsLink).setOnClickListener {
            startActivity(Intent(this, TermsActivity::class.java))
        }
    }

    private fun setupAppearance() {
        binding.btnFontSize.setOnClickListener {
            val sizes = arrayOf("8","9","10","11","12","13","14","15","16","17","18","19","20")
            val currentSize = AppFont.get(this).toInt().toString()
            var currentIndex = sizes.indexOf(currentSize)
            if (currentIndex == -1) currentIndex = sizes.indexOf("14")

            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Font Size")
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startActivity(Intent(Settings.Panel.ACTION_WIFI))
            } else {
                @Suppress("DEPRECATION")
                wifiManager.isWifiEnabled = !wifiManager.isWifiEnabled
                updateAllStates()
            }
        }

        binding.btnData.setOnClickListener {
            startActivity(Intent(Settings.ACTION_DATA_ROAMING_SETTINGS))
        }
        binding.btnData.setOnLongClickListener {
            startActivity(Intent(Settings.ACTION_DATA_ROAMING_SETTINGS))
            true
        }

        binding.btnBluetooth.setOnClickListener {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }

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

        binding.btnFlashlight.setOnClickListener {
            try {
                cameraId?.let { cameraManager.setTorchMode(it, !torchState) }
            } catch (e: Exception) {}
        }
    }

    private fun setupDisplay() {
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
        setLabelState(binding.tvWifiState, wifiManager.isWifiEnabled)

        // Data
        try {
            @Suppress("DEPRECATION")
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            @Suppress("DEPRECATION")
            val isDataOn = cm.activeNetworkInfo?.type == ConnectivityManager.TYPE_MOBILE
            setLabelState(binding.tvDataState, isDataOn)
        } catch (e: Exception) {
            setLabelState(binding.tvDataState, false)
        }

        // Bluetooth
        @Suppress("DEPRECATION")
        val btAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
        setLabelState(binding.tvBluetoothState, btAdapter?.isEnabled == true)

        // Font Size
        binding.tvFontSizeState.text = AppFont.get(this).toString()

        // DND
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val isDnd = nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        setLabelState(binding.tvDndState, isDnd)

        // Flashlight
        setLabelState(binding.tvFlashlightState, torchState)

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

    private fun setLabelState(tv: TextView, isActive: Boolean) {
        if (isActive) {
            tv.text = "ON"
            tv.setTextColor(0xFFAAAAFF.toInt())
        } else {
            tv.text = "OFF"
            tv.setTextColor(0xFF444444.toInt())
        }
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
