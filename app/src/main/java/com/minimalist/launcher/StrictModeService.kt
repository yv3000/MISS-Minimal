package com.minimalist.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class StrictModeService : AccessibilityService() {

    companion object {
        var instance: StrictModeService? = null
        private val SYSTEM_UI_PACKAGES = setOf(
            "com.android.systemui",
            "com.miui.systemui",
            "com.miui.securityadd",
            "com.miui.securitycenter",
            "com.samsung.android.systemui",
            "com.coloros.systemui",
            "com.oplus.systemui",
            "com.vivo.systemui"
        )

        fun isEnabled(context: Context): Boolean {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabledServices.contains(context.packageName)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var overlayBlocking = false
    private var statusBarBlocked = false

    private val shadeBlocker = object : Runnable {
        override fun run() {
            val shouldBlock = shouldBlock()
            syncOverlay(shouldBlock)
            syncStatusBarBlock(shouldBlock)
            if (shouldBlock) {
                checkAndDismissShadeIfVisible()
            }
            if (shouldBlock || PomodoroManager.isActive) handler.postDelayed(this, 500)
        }
    }

    private fun checkAndDismissShadeIfVisible() {
        try {
            val wins = windows ?: return
            for (w in wins) {
                val title = w.title?.toString()?.lowercase() ?: ""
                if (title.contains("notificationshade") || title.contains("notification") || (title.contains("status") && w.isFocused)) {
                    dismissNotificationShade()
                    break
                }
            }
        } catch (_: Exception) {}
    }

    private fun shouldBlock() =
        StrictModeManager.isActive() || PomodoroManager.isWorkSessionActive()

    fun dismissNotificationShade() {
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
            } catch (_: Exception) {}
        }
        try {
            performGlobalAction(GLOBAL_ACTION_BACK)
        } catch (_: Exception) {}
        try {
            @Suppress("DEPRECATION")
            sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
        } catch (_: Exception) {}
        try {
            val statusBar = getSystemService("statusbar")
            val method = statusBar?.javaClass?.getMethod("collapsePanels")
            method?.isAccessible = true
            method?.invoke(statusBar)
        } catch (_: Exception) {}
    }

    private fun isPermissionOrSystemDialog(pkg: String): Boolean {
        val p = pkg.lowercase()
        return p.contains("permissioncontroller") ||
               p.contains("packageinstaller") ||
               p.contains("securitypermission") ||
               p.contains("safecenter")
    }

    private fun isInputMethod(pkg: String): Boolean {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            ?: return false
        return imm.enabledInputMethodList.any { it.packageName == pkg }
    }

    private fun isNotificationPanelEvent(event: AccessibilityEvent): Boolean {
        val cls = event.className?.toString()?.lowercase() ?: ""
        if (cls.contains("navigation")) return false

        // Any window state change from SystemUI during block mode is almost certainly shade or quicksettings
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return true
        }

        if (cls.contains("notification") ||
            cls.contains("shade") ||
            cls.contains("quicksettings") ||
            cls.contains("qs") ||
            cls.contains("panel") ||
            cls.contains("statusbar")) {
            return true
        }

        val text = event.text.joinToString(" ").lowercase()
        val desc = event.contentDescription?.toString()?.lowercase() ?: ""
        val combined = "$text $desc"
        return combined.contains("notification") ||
               combined.contains("quick settings") ||
               combined.contains("clear all") ||
               combined.contains("alerting") ||
               combined.contains("manage")
    }

    /** Set policy_control to block/unblock status bar pull-down. */
    private fun syncStatusBarBlock(shouldBlock: Boolean) {
        if (statusBarBlocked == shouldBlock) return
        statusBarBlocked = shouldBlock
        PrivilegedToggle.setStatusBarExpansion(shouldBlock)
    }

    private fun syncOverlay(shouldBlock: Boolean) {
        if (overlayBlocking == shouldBlock) return
        overlayBlocking = shouldBlock
        if (shouldBlock) TopBarBlockerService.start(this) else TopBarBlockerService.stop(this)
    }

    private val SYSTEM_LAUNCHERS = setOf(
        "com.oppo.launcher",
        "com.oplus.launcher",
        "com.coloros.launcher",
        "com.google.android.apps.nexuslauncher",
        "com.sec.android.app.launcher",
        "com.miui.home",
        "com.mi.android.globallauncher",
        "com.android.launcher3",
        "com.android.launcher",
        "com.huawei.android.launcher",
        "com.bbk.launcher2",
        "com.vivo.upslide",
        "com.transsion.xoslauncher"
    )

    private fun isSystemLauncher(pkg: String): Boolean {
        if (pkg == packageName) return false
        if (SYSTEM_LAUNCHERS.contains(pkg)) return true
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolve = packageManager.resolveActivity(homeIntent, 0)
        return resolve?.activityInfo?.packageName == pkg
    }

    private fun isSidebarOrFloatPanel(pkg: String): Boolean {
        if (PomodoroManager.userSelectedApps.contains(pkg)) return false
        val p = pkg.lowercase()
        return p.contains("securityadd") ||
               p.contains("securitycenter") ||
               p.contains("cocktailbar") ||      // Samsung Edge panel
               p.contains("smartsidebar") ||     // Oppo / OnePlus / Realme
               p.contains("floatassistant") ||   // Realme / ColorOS floating assistant
               p.contains("sidepanel") ||        // Asus / Vivo
               p.contains("smartpanel") ||       // Tecno / Infinix
               p.contains("floatingscreen") ||
               p.contains("sidebar") ||
               p.contains("dockbar") ||          // Huawei / Honor
               p.contains("freeform") ||         // Multi-window / floating
               p.contains("multiwindow") ||
               p.contains("popupview") ||
               p.contains("bubbles") ||          // Chat bubbles
               p.contains("assistantscreen") ||  // ColorOS assistant screen
               p.contains("floating")
    }

    private fun isSystemUi(packageName: String): Boolean {
        if (isSidebarOrFloatPanel(packageName)) return false
        val p = packageName.lowercase()
        return p.contains("systemui") ||
               p.contains("notification") ||
               SYSTEM_UI_PACKAGES.any { packageName == it || packageName.startsWith("$it.") }
    }

    fun startBlocking() {
        handler.removeCallbacks(shadeBlocker)
        handler.post(shadeBlocker)
        PrivilegedToggle.setStatusBarExpansion(true)
        statusBarBlocked = true
    }

    fun stopBlocking() {
        handler.removeCallbacks(shadeBlocker)
        if (shouldBlock()) {
            handler.post(shadeBlocker)
        } else {
            syncOverlay(false)
            if (statusBarBlocked) {
                PrivilegedToggle.setStatusBarExpansion(false)
                statusBarBlocked = false
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        StrictModeManager.serviceRef = this

        // Restore Pomodoro state so allowedPackages survives process restarts
        PomodoroManager.restoreFromPrefs(this)
        StrictModeManager.restoreFromPrefs(this)

        if (shouldBlock()) {
            startBlocking()
        }
    }

    override fun onDestroy() {
        instance = null
        if (StrictModeManager.serviceRef === this) StrictModeManager.serviceRef = null
        handler.removeCallbacks(shadeBlocker)
        if (statusBarBlocked) {
            PrivilegedToggle.setStatusBarExpansion(false)
            statusBarBlocked = false
        }
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val eventPkg = event.packageName?.toString() ?: return
        val shouldBlock = shouldBlock()
        syncOverlay(shouldBlock)
        syncStatusBarBlock(shouldBlock)
        if (!shouldBlock) return

        // 1. In Pomodoro Mode:
        if (PomodoroManager.isWorkSessionActive()) {
            // A. ALWAYS block sidebar and floating panels FIRST!
            if (isSidebarOrFloatPanel(eventPkg)) {
                Log.d("PomBlock", "BLOCK sidebar pkg=$eventPkg")
                dismissNotificationShade()
                performGlobalAction(GLOBAL_ACTION_BACK)
                return
            }

            // B. ALWAYS block notification shade if expanding / visible
            if (isSystemUi(eventPkg)) {
                if (isNotificationPanelEvent(event)) {
                    Log.d("PomBlock", "BLOCK notification shade cls=${event.className}")
                    dismissNotificationShade()
                }
                return
            }

            // C. Permission dialogs, keyboards (IME)
            if (isPermissionOrSystemDialog(eventPkg) || isInputMethod(eventPkg)) {
                return
            }

            // D. Launcher itself, user-allowed apps, or ALWAYS_ALLOWED (telecom/dialer)
            if (PomodoroManager.allowedPackages.contains(eventPkg) || eventPkg == packageName) {
                Log.d("PomBlock", "ALLOWED pkg=$eventPkg eventType=${event.eventType}")
                return
            }

            // E. Transition Window: if an allowed app was launched in the last 2.5 seconds,
            // allow launcher animations / intermediate system transitions without blocking!
            val timeSinceLaunch = android.os.SystemClock.uptimeMillis() - PomodoroManager.lastLaunchTime
            if (timeSinceLaunch < 2500) {
                Log.d("PomBlock", "TRANSITION window pkg=$eventPkg time=$timeSinceLaunch")
                return
            }

            // F. System Home Launcher: user pressed home or swiped home outside transition
            if (isSystemLauncher(eventPkg)) {
                if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                    Log.d("PomBlock", "HOME pressed -> bring focus front")
                    bringFocusActivityToFront("pomodoro")
                }
                return
            }

            // G. Any unallowed 3rd-party application: block immediately!
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                Log.d("PomBlock", "BLOCK unallowed pkg=$eventPkg allowedPkgs=${PomodoroManager.allowedPackages}")
                performGlobalAction(GLOBAL_ACTION_BACK)
                bringFocusActivityToFront("pomodoro")
            }
            return
        }

        // 2. In Strict Mode:
        if (StrictModeManager.isActive()) {
            if (isSidebarOrFloatPanel(eventPkg)) {
                dismissNotificationShade()
                performGlobalAction(GLOBAL_ACTION_BACK)
                bringFocusActivityToFront("strict")
                return
            }
            if (isSystemUi(eventPkg)) {
                if (isNotificationPanelEvent(event)) {
                    dismissNotificationShade()
                }
                return
            }
            if (isPermissionOrSystemDialog(eventPkg) || isInputMethod(eventPkg)) {
                return
            }
            if (eventPkg != packageName && event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                bringFocusActivityToFront("strict")
            }
        }
    }

    private fun bringFocusActivityToFront(tab: String) {
        try {
            val intent = Intent(this, FocusActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
                putExtra("tab", tab)
            }
            val options = android.app.ActivityOptions.makeBasic()
            try {
                val method = android.app.ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                method.invoke(options, 1) // 1 = WINDOWING_MODE_FULLSCREEN
            } catch (_: Exception) {}
            startActivity(intent, options.toBundle())
        } catch (_: Exception) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    override fun onInterrupt() {}
}
