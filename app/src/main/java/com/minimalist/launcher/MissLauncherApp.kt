package com.minimalist.launcher

import android.app.Application

class MissLauncherApp : Application() {
    companion object {
        // true = launcher is in foreground, 
        // block system panel
        var blockNotifPanel = false
    }
}
