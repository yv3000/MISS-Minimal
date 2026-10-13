package com.minimalist.launcher

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Lightweight, instant connectivity controller with zero background delays. */
class ConnectivityControls(private val activity: AppCompatActivity, private val onChanged: () -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val refreshing = AtomicBoolean(false)
    private val refreshPending = AtomicBoolean(false)
    private val poll = object : Runnable {
        override fun run() {
            refresh()
            main.postDelayed(this, 3_000)
        }
    }
    @Volatile private var closed = false
    private var started = false
    private var askingBluetooth = false
    private var states: Map<String, Boolean?> = RadioCommands.controls.associateWith { null }

    private val bluetoothPermission = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        askingBluetooth = false
        if (!closed) performToggle("bluetooth")
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refresh()
    }

    private val lifecycle = object : DefaultLifecycleObserver {
        override fun onCreate(owner: LifecycleOwner) {
            if (closed) return
            started = true
            ContextCompat.registerReceiver(activity, receiver, IntentFilter().apply {
                addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(LocationManager.MODE_CHANGED_ACTION)
                addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
                addAction("android.net.wifi.WIFI_AP_STATE_CHANGED")
                addAction("android.net.conn.TETHER_STATE_CHANGED")
                addAction("android.intent.action.ANY_DATA_STATE")
            }, ContextCompat.RECEIVER_EXPORTED)
        }

        override fun onResume(owner: LifecycleOwner) {
            refresh()
            main.post(poll)
        }
        override fun onPause(owner: LifecycleOwner) { main.removeCallbacks(poll) }
        override fun onDestroy(owner: LifecycleOwner) = close()
    }

    init {
        activity.lifecycle.addObserver(lifecycle)
    }

    fun state(control: String): Boolean? = states[control]

    fun refresh() {
        if (closed || !started) return
        refreshPending.set(true)
        if (!refreshing.compareAndSet(false, true)) return
        worker.execute {
            try {
                do {
                    refreshPending.set(false)
                    if (closed) return@execute
                    val updated = RadioCommands.controls.associateWith {
                        PrivilegedToggle.localState(activity.applicationContext, it)
                    }
                    publish(updated)
                } while (refreshPending.get() && !closed)
            } finally {
                refreshing.set(false)
                if (refreshPending.get()) main.post { refresh() }
            }
        }
    }

    fun toggle(control: String) {
        if (closed || !started || askingBluetooth || actionRunning.get()) return
        if (control !in RadioCommands.controls) return

        if (control == "bluetooth" && Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            askingBluetooth = true
            bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
            return
        }

        performToggle(control)
    }

    private fun performToggle(control: String) {
        if (closed || !actionRunning.compareAndSet(false, true)) return
        worker.execute {
            var needFallback = false
            try {
                if (closed) return@execute
                val current = PrivilegedToggle.localState(activity.applicationContext, control)
                val target = if (current != null) !current else true

                val result = PrivilegedToggle.setWithoutShizuku(activity.applicationContext, control, target)
                if (result.isNotEmpty()) {
                    needFallback = true
                }
                val updated = RadioCommands.controls.associateWith {
                    PrivilegedToggle.localState(activity.applicationContext, it)
                }
                publish(updated)
            } catch (_: Exception) {
                needFallback = true
            } finally {
                actionRunning.set(false)
            }
            if (needFallback && !closed) {
                main.post { openQuickPanelOrSettings(control) }
            }
        }
    }

    fun openQuickPanelOrSettings(control: String) {
        if (closed || activity.isFinishing || activity.isDestroyed) return
        val intent = when (control) {
            "wifi" -> if (Build.VERSION.SDK_INT >= 29) Intent("android.settings.panel.action.INTERNET") else Intent(Settings.ACTION_WIFI_SETTINGS)
            "data" -> if (Build.VERSION.SDK_INT >= 29) Intent("android.settings.panel.action.INTERNET") else Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)
            "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            "location" -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            "hotspot" -> Intent("android.settings.TETHER_SETTINGS")
            "airplane" -> Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
            else -> Intent(Settings.ACTION_WIRELESS_SETTINGS)
        }
        openChosen(intent)
    }

    private fun publish(updated: Map<String, Boolean?>) {
        main.post {
            if (!closed && states != updated) {
                states = updated
                onChanged()
            }
        }
    }

    private fun openChosen(intent: Intent) {
        runCatching {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activity.startActivity(intent)
        }.onFailure {
            runCatching {
                activity.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        }
    }

    fun close() {
        if (closed) return
        closed = true
        activity.lifecycle.removeObserver(lifecycle)
        if (started) {
            runCatching { activity.unregisterReceiver(receiver) }
        }
        bluetoothPermission.unregister()
        worker.shutdown()
        main.removeCallbacksAndMessages(null)
    }

    private companion object {
        val actionRunning = AtomicBoolean(false)
    }
}
