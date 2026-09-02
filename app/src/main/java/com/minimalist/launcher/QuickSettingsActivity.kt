package com.minimalist.launcher

import android.Manifest
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.widget.SeekBar
import android.widget.TextView
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
    private lateinit var wifiManager: WifiManager
    private lateinit var cameraManager: CameraManager
    private lateinit var vibrator: Vibrator
    private var torchState = false
    private var cameraId: String? = null
    private val notificationAdapter = NotificationAdapter()

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) toggleBluetooth() else openBluetoothSettings()
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

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
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

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
            addAction(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(android.net.wifi.WifiManager.WIFI_STATE_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(this, stateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
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
        unregisterReceiver(stateReceiver)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(notificationReceiver)
        contentResolver.unregisterContentObserver(brightnessObserver)
    }

    private fun setupConnectivity() {
        // WIFI
        binding.btnWifi.setOnClickListener {
            val enable = !wifiManager.isWifiEnabled
            PrivilegedToggle.chain(
                "wifi",
                listOf(
                    // Still honoured on Android 9 and below (CHANGE_WIFI_STATE). On Q+ WifiService
                    // requires the signature-level NETWORK_SETTINGS/NETWORK_STACK, so this returns
                    // false and we fall through. Writing Settings.Global "wifi_on" is deliberately
                    // NOT attempted: the write succeeds but WifiSettingsStore never reads it back,
                    // so it changes nothing and corrupts the stored state after a reboot.
                    PrivilegedToggle.Attempt("WifiManager.setWifiEnabled") {
                        @Suppress("DEPRECATION")
                        wifiManager.setWifiEnabled(enable)
                    },
                    PrivilegedToggle.root(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                            "cmd wifi set-wifi-enabled ${if (enable) "enabled" else "disabled"}"
                        else "svc wifi ${if (enable) "enable" else "disable"}"
                    )
                ),
                { wifiManager.isWifiEnabled == enable }
            ) { winner ->
                if (isFinishing || isDestroyed) return@chain
                updateAllStates()
                // Panel = slim bottom sheet, not the full Settings app.
                if (winner == null) openSettings(Settings.Panel.ACTION_WIFI, Settings.ACTION_WIFI_SETTINGS)
            }
        }
        binding.btnWifi.setOnLongClickListener {
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            true
        }

        // DATA — use internet connectivity panel
        binding.btnData.setOnClickListener {
            val current = isMobileDataEnabled()
            if (current == null) {
                openSettings(Settings.Panel.ACTION_INTERNET_CONNECTIVITY, Settings.ACTION_DATA_USAGE_SETTINGS)
                return@setOnClickListener
            }
            val enable = !current
            PrivilegedToggle.chain(
                "data",
                listOf(
                    // No third-party path exists: setDataEnabled needs MODIFY_PHONE_STATE
                    // (signature|privileged, not adb-grantable). Writing Settings.Global
                    // "mobile_data" is telephony's own persisted state, not a command, so it is
                    // not attempted — it would only make the UI lie.
                    PrivilegedToggle.root(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                            "cmd phone data ${if (enable) "enable" else "disable"}"
                        else "svc data ${if (enable) "enable" else "disable"}"
                    )
                ),
                { isMobileDataEnabled() == enable }
            ) { winner ->
                if (isFinishing || isDestroyed) return@chain
                updateAllStates()
                if (winner == null) openSettings(Settings.Panel.ACTION_INTERNET_CONNECTIVITY, Settings.ACTION_DATA_USAGE_SETTINGS)
            }
        }
        binding.btnData.setOnLongClickListener {
            openSettings(Settings.ACTION_DATA_USAGE_SETTINGS)
            true
        }

        // BLUETOOTH
        binding.btnBluetooth.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
            ) {
                bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                toggleBluetooth()
            }
        }
        binding.btnBluetooth.setOnLongClickListener {
            openBluetoothSettings()
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

        // LOCATION — third-party apps cannot directly change this setting.
        binding.btnLocation.visibility = View.VISIBLE
        binding.btnLocation.setOnClickListener {
            toggleLocation()
        }

        // HOTSPOT — tethering changes require privileged/carrier access.
        binding.btnHotspot.visibility = View.VISIBLE
        binding.btnHotspot.setOnClickListener {
            toggleHotspot()
        }
        binding.btnHotspot.setOnLongClickListener {
            openTetherSettings()
            true
        }

        // AIRPLANE
        binding.btnAirplane.visibility = View.VISIBLE
        binding.btnAirplane.setOnClickListener {
            val enable = !isAirplaneModeEnabled()
            PrivilegedToggle.chain(
                "airplane",
                listOf(
                    // Settings.Global.AIRPLANE_MODE_ON is writable with WRITE_SECURE_SETTINGS, but
                    // the radios only react to Intent.ACTION_AIRPLANE_MODE_CHANGED, which is a
                    // protected broadcast that only the system may send. A bare write would
                    // therefore make the UI claim success while nothing changed, so it is not
                    // attempted. Root is the only non-privileged path that really works.
                    PrivilegedToggle.root("cmd connectivity airplane-mode ${if (enable) "enable" else "disable"}")
                ),
                { isAirplaneModeEnabled() == enable }
            ) { winner ->
                if (isFinishing || isDestroyed) return@chain
                updateAllStates()
                // Internet panel exposes airplane mode and is a slim sheet, not the Settings app.
                if (winner == null) openSettings(
                    Settings.Panel.ACTION_INTERNET_CONNECTIVITY,
                    Settings.ACTION_AIRPLANE_MODE_SETTINGS
                )
            }
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
        setButtonState(binding.btnWifi, stateOf { wifiManager.isWifiEnabled }, dpToPx)

        setButtonState(binding.btnData, isMobileDataEnabled() == true, dpToPx)

        setButtonState(binding.btnBluetooth, stateOf {
            getSystemService(BluetoothManager::class.java).adapter?.isEnabled == true
        }, dpToPx)

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
        setButtonState(binding.btnLocation, stateOf { isLocationOn() }, dpToPx)

        // Hotspot
        setButtonState(binding.btnHotspot, stateOf { isHotspotEnabled() }, dpToPx)

        // Airplane
        setButtonState(binding.btnAirplane, stateOf {
            isAirplaneModeEnabled()
        }, dpToPx)
        
        updateSoundUI()
        updateDisplayUI()
    }

    private fun stateOf(read: () -> Boolean) = try {
        read()
    } catch (_: SecurityException) {
        false
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

    private fun openTetherSettings() {
        // Since Android 8.0+, we can\u0027t easily toggle hotspot without high-level permissions.
        // Opening settings is the safest way.
        try {
            startActivity(Intent("android.settings.TETHER_SETTINGS"))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
        }
    }

    private fun toggleHotspot() {
        val enable = !isHotspotEnabled()
        PrivilegedToggle.chain(
            "hotspot",
            listOf(
                // Removed from the public SDK in API 26 and permission-gated (TETHER_PRIVILEGED)
                // since then; tried anyway because some OEM builds still expose it.
                PrivilegedToggle.Attempt("WifiManager.setWifiApEnabled (reflection)") {
                    val method = wifiManager.javaClass.getMethod(
                        "setWifiApEnabled",
                        android.net.wifi.WifiConfiguration::class.java,
                        Boolean::class.javaPrimitiveType
                    )
                    method.invoke(wifiManager, null, enable) as? Boolean ?: true
                },
                PrivilegedToggle.root("svc wifi ${if (enable) "enable" else "disable"}ap"),
                PrivilegedToggle.root("cmd wifi ${if (enable) "start-softap" else "stop-softap"}")
            ),
            { isHotspotEnabled() == enable }
        ) { winner ->
            if (isFinishing || isDestroyed) return@chain
            updateAllStates()
            if (winner == null) openTetherSettings()
        }
    }

    private fun isHotspotEnabled(): Boolean = runCatching {
        val method = wifiManager.javaClass.getMethod("isWifiApEnabled")
        method.invoke(wifiManager) as? Boolean ?: false
    }.getOrDefault(false)

    private fun openSettings(primary: String, fallback: String = Settings.ACTION_SETTINGS) {
        try {
            startActivity(Intent(primary))
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(fallback))
        }
    }

    private fun toggleBluetooth() {
        try {
            val adapter = getSystemService(BluetoothManager::class.java).adapter ?: return
            val enable = !adapter.isEnabled
            PrivilegedToggle.chain(
                "bluetooth",
                listOf(
                    // Public until API 32, hidden (but usually still present) on 33+.
                    PrivilegedToggle.Attempt("BluetoothAdapter.${if (enable) "enable" else "disable"}") {
                        val method = adapter.javaClass.getMethod(if (enable) "enable" else "disable")
                        method.invoke(adapter) as? Boolean ?: true
                    },
                    PrivilegedToggle.root("cmd bluetooth_manager ${if (enable) "enable" else "disable"}"),
                    PrivilegedToggle.root("svc bluetooth ${if (enable) "enable" else "disable"}")
                ),
                { adapter.isEnabled == enable }
            ) { winner ->
                if (isFinishing || isDestroyed) return@chain
                updateAllStates()
                if (winner == null) {
                    // Small system dialog beats the full Settings app.
                    if (enable) startActivity(Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE))
                    else openBluetoothSettings()
                }
            }
        } catch (_: SecurityException) {
            openBluetoothSettings()
        }
    }

    private fun openBluetoothSettings() {
        openSettings(Settings.ACTION_BLUETOOTH_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS)
    }

    private fun isMobileDataEnabled(): Boolean? = try {
        val subId = SubscriptionManager.getDefaultDataSubscriptionId()
        getSystemService(TelephonyManager::class.java)
            .createForSubscriptionId(subId)
            .isDataEnabled
    } catch (_: Exception) { null }

    private fun isAirplaneModeEnabled() =
        Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1

    /** LocationManager.isLocationEnabled is API 28; minSdk is 26, so fall back to LOCATION_MODE. */
    private fun isLocationOn(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getSystemService(LocationManager::class.java).isLocationEnabled
        } else {
            Settings.Secure.getInt(contentResolver, Settings.Secure.LOCATION_MODE, 0) !=
                Settings.Secure.LOCATION_MODE_OFF
        }

    private fun toggleLocation() {
        val enable = !isLocationOn()
        PrivilegedToggle.chain(
            "location",
            listOf(
                // This is exactly what `adb shell settings put secure location_mode` does, and it
                // keeps working on Android 12-16 as long as WRITE_SECURE_SETTINGS is granted.
                // The old code gated this to API <= 30, so on newer phones it never even tried.
                PrivilegedToggle.Attempt("Settings.Secure LOCATION_MODE") {
                    PrivilegedToggle.writeSecure(
                        this,
                        Settings.Secure.LOCATION_MODE,
                        if (enable) Settings.Secure.LOCATION_MODE_HIGH_ACCURACY else Settings.Secure.LOCATION_MODE_OFF
                    )
                },
                PrivilegedToggle.root(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                        "cmd location set-location-enabled $enable --user current"
                    else "settings put secure location_mode ${if (enable) 3 else 0}"
                )
            ),
            { isLocationOn() == enable }
        ) { winner ->
            if (isFinishing || isDestroyed) return@chain
            updateAllStates()
            if (winner == null) openSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        }
    }
}
