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
import android.provider.Settings
import android.telephony.TelephonyManager
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.minimalist.launcher.databinding.ActivityQuickSettingsBinding
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper

class QuickSettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityQuickSettingsBinding
    private lateinit var audioManager: AudioManager
    private lateinit var wifiManager: WifiManager
    private lateinit var cameraManager: CameraManager
    private var torchState = false
    private var cameraId: String? = null

    private val handler = Handler(Looper.getMainLooper())
    private val stateUpdateRunnable = object : Runnable {
        override fun run() {
            updateAllStates()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuickSettingsBinding.inflate(layoutInflater)
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
        setupSound()
        setupDisplay()
    }

    override fun onResume() {
        super.onResume()
        applyFontSize()
        updateSoundUI()
        updateAllStates()
        handler.post(stateUpdateRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(stateUpdateRunnable)
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
            startActivity(Intent(Settings.ACTION_DATA_ROAMING_SETTINGS))
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
        binding.btnFlashlight.setOnClickListener {
            try {
                cameraId?.let { cameraManager.setTorchMode(it, !torchState) }
            } catch (e: Exception) {}
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

        // Flashlight (ImageView special case)
        val flashBg = GradientDrawable()
        flashBg.shape = GradientDrawable.RECTANGLE
        flashBg.cornerRadius = 8f * dpToPx
        if (torchState) {
            flashBg.setColor(0xFF1A1A2E.toInt())
            flashBg.setStroke((1 * dpToPx).toInt(), 0xFF4444AA.toInt())
            binding.btnFlashlight.setColorFilter(0xFFAAAAFF.toInt())
        } else {
            flashBg.setColor(0xFF000000.toInt())
            flashBg.setStroke((1 * dpToPx).toInt(), 0xFF2A2A2A.toInt())
            binding.btnFlashlight.setColorFilter(0xFF666666.toInt())
        }
        binding.btnFlashlight.background = flashBg
    }

    private fun setButtonState(view: TextView, isActive: Boolean, density: Float) {
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.RECTANGLE
        bg.cornerRadius = 8f * density
        if (isActive) {
            bg.setColor(0xFF1A1A2E.toInt())
            bg.setStroke((1 * density).toInt(), 0xFF4444AA.toInt())
            view.setTextColor(0xFFAAAAFF.toInt())
        } else {
            bg.setColor(0xFF000000.toInt())
            bg.setStroke((1 * density).toInt(), 0xFF2A2A2A.toInt())
            view.setTextColor(0xFF666666.toInt())
        }
        view.background = bg
    }

    private fun setupSound() {
        binding.btnSoundNormal.setOnClickListener { setRingerMode(AudioManager.RINGER_MODE_NORMAL) }
        binding.btnSoundVibrate.setOnClickListener { setRingerMode(AudioManager.RINGER_MODE_VIBRATE) }
        binding.btnSoundSilent.setOnClickListener { setRingerMode(AudioManager.RINGER_MODE_SILENT) }

        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        
        binding.seekVolume.max = maxVol
        binding.seekVolume.progress = curVol
        binding.tvVolumeVal.text = "${(curVol * 100) / maxVol}%"

        binding.seekVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, AudioManager.FLAG_SHOW_UI)
                    binding.tvVolumeVal.text = "${(progress * 100) / maxVol}%"
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setRingerMode(mode: Int) {
        try {
            audioManager.ringerMode = mode
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
        
        try {
            val curBrightness = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            binding.seekBrightness.progress = curBrightness
        } catch (e: Exception) {}

        binding.seekBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    if (Settings.System.canWrite(this@QuickSettingsActivity)) {
                        Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                        Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, progress)
                        val lp = window.attributes
                        lp.screenBrightness = progress / 255f
                        window.attributes = lp
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

    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(0, R.anim.slide_up_exit)
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, R.anim.slide_up_exit)
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
