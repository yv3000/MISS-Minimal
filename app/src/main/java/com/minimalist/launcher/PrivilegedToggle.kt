package com.minimalist.launcher

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.TimeUnit

/**
 * Toggle helper for things a normal app is not supposed to change.
 *
 * Order tried, per toggle:
 *   1. public SDK API (where one still exists for third-party apps)
 *   2. Settings.Global / Settings.Secure write — needs WRITE_SECURE_SETTINGS, granted over adb
 *   3. root shell (`su`)
 *   4. caller's fallback (slim system Panel, never the full Settings app when a Panel exists)
 *
 * Every failure is logged with a reason so a dead path is visible in logcat instead of silently
 * falling through to a Settings screen.
 */
object PrivilegedToggle {

    const val TAG = "PrivilegedToggle"
    const val WRITE_SECURE_SETTINGS = "android.permission.WRITE_SECURE_SETTINGS"

    const val ADB_GRANT_COMMAND =
        "adb shell pm grant com.minimalist.launcher android.permission.WRITE_SECURE_SETTINGS"

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

    /** Writes a Settings.Global int. Returns false (and logs) if the permission is missing. */
    fun writeGlobal(context: Context, key: String, value: Int): Boolean {
        if (!hasSecureSettings(context)) {
            Log.w(TAG, "global write $key=$value skipped: WRITE_SECURE_SETTINGS not granted")
            return false
        }
        return runCatching { Settings.Global.putInt(context.contentResolver, key, value) }
            .onFailure { Log.w(TAG, "global write $key=$value failed: $it") }
            .getOrDefault(false)
    }

    /**
     * Runs [attempts] in order on a background thread, polling [stateMatches] after each one.
     * Calls [result] on the main thread with the label of whatever worked, or null if nothing did.
     */
    fun chain(
        label: String,
        attempts: List<Attempt>,
        stateMatches: () -> Boolean,
        result: (String?) -> Unit
    ) {
        Thread {
            var winner: String? = null
            for (attempt in attempts) {
                val accepted = runCatching(attempt.action)
                    .onFailure { Log.w(TAG, "$label/${attempt.name} threw: $it") }
                    .getOrDefault(false)
                if (!accepted) {
                    Log.d(TAG, "$label/${attempt.name} not accepted")
                    continue
                }
                if (await(stateMatches, attempt.waitMs)) {
                    winner = attempt.name
                    break
                }
                Log.w(TAG, "$label/${attempt.name} accepted but state did not change")
            }
            if (winner == null) Log.w(TAG, "$label: no method worked, using fallback")
            else Log.i(TAG, "$label: changed via $winner")
            Handler(Looper.getMainLooper()).post { result(winner) }
        }.start()
    }

    private fun await(stateMatches: () -> Boolean, budgetMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + budgetMs
        while (System.currentTimeMillis() < deadline) {
            if (runCatching(stateMatches).getOrDefault(false)) return true
            Thread.sleep(150)
        }
        return runCatching(stateMatches).getOrDefault(false)
    }

    fun root(command: String) = Attempt("root:$command", 6_000) {
        val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        if (!process.waitFor(8, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            false
        } else process.exitValue() == 0
    }

    data class Attempt(val name: String, val waitMs: Long = 2_500, val action: () -> Boolean)

    /** Kept for callers that only ever wanted the root path. */
    fun runRoot(command: String, stateMatches: () -> Boolean, result: (Boolean) -> Unit) =
        chain(command, listOf(root(command)), stateMatches) { result(it != null) }
}
