package com.minimalist.launcher

import android.os.Handler
import android.os.Looper
import java.util.concurrent.TimeUnit

object PrivilegedToggle {
    fun runRoot(command: String, stateMatches: () -> Boolean, result: (Boolean) -> Unit) {
        Thread {
            val accepted = runCatching {
                val process = ProcessBuilder("su", "-c", command)
                    .redirectErrorStream(true)
                    .start()
                if (!process.waitFor(8, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    false
                } else process.exitValue() == 0
            }.getOrDefault(false)

            var changed = false
            if (accepted) {
                for (attempt in 0 until 20) {
                    if (runCatching(stateMatches).getOrDefault(false)) {
                        changed = true
                        break
                    }
                    Thread.sleep(300)
                }
            }
            Handler(Looper.getMainLooper()).post { result(changed) }
        }.start()
    }
}
