package com.minimalist.launcher

import android.app.Application

class MissLauncherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PrefsManager.init(this)
        StrictModeManager.restoreFromPrefs(this)
    }

    companion object {
        // true = launcher is in foreground, 
        // block system panel
        var blockNotifPanel = false
    }
}
