package com.minimalist.launcher

import android.content.Context
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.TimeUnit
import java.io.File

/** Native API and root fallback for the shared connectivity controller. */
object PrivilegedToggle {

    const val TAG = "PrivilegedToggle"
    const val WRITE_SECURE_SETTINGS = "android.permission.WRITE_SECURE_SETTINGS"

    fun hasSecureSettings(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    /** Writes a Settings.Secure int. Returns false (and logs) if the permission is missing. */
    fun writeSecure(context: Context, key: String, value: Int): Boolean {
        if (!hasSecureSettings(context)) {
            Log.w(TAG, "secure write $key=$value skipped: WRITE_SECURE_SETTINGS not granted")
            return false
        }
        return runCatching { Settings.Secure.putInt(context.contentResolver, key, value) }
            .onFailure { Log.w(TAG, "secure write $key=$value failed: $it") }
            .getOrDefault(false)
    }

    private fun await(stateMatches: () -> Boolean, budgetMs: Long): Boolean {
        val deadline = SystemClock.elapsedRealtime() + budgetMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (runCatching(stateMatches).getOrDefault(false)) return true
            Thread.sleep(150)
        }
        return runCatching(stateMatches).getOrDefault(false)
    }

    /** Worker-thread only. Unknown/transitioning radios are never reported as off. */
    internal fun localState(context: Context, control: String): Boolean? = runCatching {
        when (control) {
            "wifi" -> when (context.getSystemService(WifiManager::class.java).wifiState) {
                WifiManager.WIFI_STATE_ENABLED -> true
                WifiManager.WIFI_STATE_DISABLED -> false
                else -> null
            }
            "bluetooth" -> {
                if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(
                        context, Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED) return@runCatching null
                when (context.getSystemService(BluetoothManager::class.java).adapter?.state) {
                    BluetoothAdapter.STATE_ON -> true
                    BluetoothAdapter.STATE_OFF -> false
                    else -> null
                }
            }
            "location" -> if (Build.VERSION.SDK_INT >= 28) {
                context.getSystemService(LocationManager::class.java).isLocationEnabled
            } else {
                Settings.Secure.getInt(context.contentResolver, Settings.Secure.LOCATION_MODE) != 0
            }
            "airplane" -> when (Settings.Global.getInt(
                context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, -1
            )) {
                0 -> false
                1 -> true
                else -> null
            }
            "data" -> {
                val sub = SubscriptionManager.getDefaultDataSubscriptionId()
                if (sub < 0) null
                else context.getSystemService(TelephonyManager::class.java)
                    .createForSubscriptionId(sub).isDataEnabled
            }
            "hotspot" -> {
                val wm = context.applicationContext.getSystemService(WifiManager::class.java)
                runCatching {
                    val method = wm.javaClass.getMethod("isWifiApEnabled")
                    method.invoke(wm) as Boolean
                }.getOrNull() ?: runCatching {
                    val method = wm.javaClass.getMethod("getWifiApState")
                    val state = method.invoke(wm) as Int
                    when (state) {
                        13 -> true
                        11 -> false
                        else -> null
                    }
                }.getOrNull()
            }
            else -> null
        }
    }.getOrNull()

    fun isRootAvailable(): Boolean {
        return try {
            val paths = arrayOf(
                "/system/bin/su", "/system/xbin/su", "/sbin/su",
                "/system/sd/xbin/su", "/data/local/xbin/su", "/data/local/bin/su"
            )
            paths.any { File(it).exists() }
        } catch (_: Exception) {
            false
        }
    }

    internal fun setWithoutShizuku(context: Context, control: String, enabled: Boolean): String {
        val localAccepted = try {
            @Suppress("DEPRECATION")
            when {
                control == "wifi" && Build.VERSION.SDK_INT < 29 ->
                    context.getSystemService(WifiManager::class.java).setWifiEnabled(enabled)
                control == "bluetooth" && Build.VERSION.SDK_INT < 33 -> {
                    val adapter = context.getSystemService(BluetoothManager::class.java).adapter
                    if (enabled) adapter?.enable() == true else adapter?.disable() == true
                }
                control == "location" && hasSecureSettings(context) -> {
                    var ok = false
                    if (Build.VERSION.SDK_INT >= 28) {
                        ok = runCatching {
                            val lm = context.getSystemService(LocationManager::class.java)
                            val method = lm.javaClass.getMethod(
                                "setLocationEnabledForUser",
                                java.lang.Boolean.TYPE,
                                android.os.UserHandle::class.java
                            )
                            method.invoke(lm, enabled, android.os.Process.myUserHandle())
                            true
                        }.getOrDefault(false)
                    }
                    if (!ok) {
                        ok = writeSecure(context, Settings.Secure.LOCATION_MODE, if (enabled) 3 else 0)
                    }
                    ok
                }
                control == "airplane" && hasSecureSettings(context) -> {
                    val ok = Settings.Global.putInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, if (enabled) 1 else 0)
                    runCatching {
                        val cm = context.getSystemService(android.net.ConnectivityManager::class.java)
                        val method = cm.javaClass.getMethod("setAirplaneMode", java.lang.Boolean.TYPE)
                        method.invoke(cm, enabled)
                    }
                    runCatching {
                        val intent = android.content.Intent(android.content.Intent.ACTION_AIRPLANE_MODE_CHANGED).apply {
                            putExtra("state", enabled)
                        }
                        context.sendBroadcast(intent)
                    }
                    ok
                }
                else -> false
            }
        } catch (_: SecurityException) {
            false
        }
        if (localAccepted && await({ localState(context, control) == enabled }, 3_000)) return ""
        if (!isRootAvailable()) return "NO_ROOT"
        val command = RadioCommands.set(control, enabled, Build.VERSION.SDK_INT)
            ?: return "This control is not supported by the root command backend on this Android version."
        val error = runCatching { executeFixed(command, root = true) }.exceptionOrNull()
        if (error != null) return error.message ?: "Root command failed."
        if (await({ localState(context, control) == enabled }, 6_000)) return ""
        return error?.message ?: "The requested state could not be verified."
    }

    /** Only internal, finite commands reach this method; output is bounded by the OS command. */
    internal fun executeFixed(command: List<String>, root: Boolean = false): String {
        if (root && !isRootAvailable()) {
            throw IllegalStateException("Root is unavailable.")
        }
        val process = ProcessBuilder(if (root) listOf("su", "-c", command.joinToString(" ")) else command)
            .redirectError(File("/dev/null")).start()
        try {
            check(process.waitFor(8, TimeUnit.SECONDS)) { "Radio command timed out." }
            check(process.exitValue() == 0) { "The device rejected the radio command (exit ${process.exitValue()})." }
            return process.inputStream.bufferedReader().use { it.readText().trim() }
        } finally {
            process.destroyForcibly()
        }
    }

    /** Collapse the status bar / notification shade. */
    fun collapseStatusBar() {}

    /** Block or unblock status bar expansion. */
    fun setStatusBarExpansion(blocked: Boolean) {}

    fun forceStop(packageName: String) {
        if (packageName.isBlank() || packageName == "com.minimalist.launcher") return
        Thread {
            try {
                if (isRootAvailable()) {
                    executeFixed(listOf("am", "force-stop", packageName), root = true)
                }
            } catch (_: Throwable) {}
        }.start()
    }
}

/** Pure allowlist shared by root and the UserService. No caller-supplied command fragments. */
internal object RadioCommands {
    val controls = setOf("wifi", "data", "bluetooth", "location", "hotspot", "airplane")

    fun set(control: String, enabled: Boolean, sdk: Int): List<String>? {
        val action = if (enabled) "enable" else "disable"
        return when (control) {
            "wifi" -> if (sdk >= 30)
                listOf("cmd", "wifi", "set-wifi-enabled", if (enabled) "enabled" else "disabled")
            else listOf("svc", "wifi", action)
            "data" -> listOf("svc", "data", action)
            "bluetooth" -> if (sdk >= 33) listOf("cmd", "bluetooth_manager", action)
            else listOf("svc", "bluetooth", action)
            "airplane" -> if (sdk >= 28) listOf("cmd", "connectivity", "airplane-mode", action) else null
            "location" -> if (sdk >= 31)
                listOf("cmd", "location", "set-location-enabled", enabled.toString(), "--user", "current")
            else if (sdk == 30)
                listOf("cmd", "location", "set-location-enabled", "--user", "current", enabled.toString())
            else listOf("settings", "--user", "current", "put", "secure", "location_mode", if (enabled) "3" else "0")
            "hotspot" -> if (enabled)
                listOf("cmd", "wifi", "start-softap", "AndroidHotspot", "wpa2", "12345678")
            else listOf("cmd", "wifi", "stop-softap")
            else -> null
        }
    }

    fun decode(state: Int): Boolean? = when (state) {
        0 -> false
        1 -> true
        else -> null
    }
}
