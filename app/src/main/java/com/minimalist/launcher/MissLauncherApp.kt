package com.minimalist.launcher

import android.app.Activity
import android.app.Application
import android.os.Bundle

class MissLauncherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PrefsManager.init(this)
        StrictModeManager.restoreFromPrefs(this)
        registerFontHook()
    }

    /**
     * Single place that applies the global "Font Size" to every activity as it resumes,
     * so any new screen/TextView added later is covered without extra wiring.
     * AppFont itself skips Home / Quick Settings / Focus.
     */
    private fun registerFontHook() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) = AppFont.applyToActivity(activity)
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    companion object {
        // true = launcher is in foreground, 
        // block system panel
        var blockNotifPanel = false
    }
}
