package com.minimalist.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
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
                dismissNotificationShade()
                checkAndBlockFloatingWindows()
            }
            if (shouldBlock || PomodoroManager.isActive) handler.postDelayed(this, 750)
        }
    }

    private fun shouldBlock() =
        StrictModeManager.isActive() || PomodoroManager.isWorkSessionActive()

    private fun dismissNotificationShade() {
        performGlobalAction(GLOBAL_ACTION_BACK)
        if (Build.VERSION.SDK_INT >= 31) {
            performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        }
        @Suppress("DEPRECATION")
        sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
        try {
            val statusBar = getSystemService("statusbar")
            statusBar.javaClass.getMethod("collapsePanels").invoke(statusBar)
        } catch (_: Exception) {}
    }

    private fun isPermissionOrSystemDialog(pkg: String): Boolean {
        val p = pkg.lowercase()
        return p.contains("permissioncontroller") ||
               p.contains("packageinstaller") ||
               p.contains("securitypermission")
    }

    private fun checkAndBlockFloatingWindows() {
        if (!shouldBlock()) return
        val currentWindows = runCatching { windows }.getOrNull() ?: return
        for (w in currentWindows) {
            if (w.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION) {
                val bounds = android.graphics.Rect()
                w.getBoundsInScreen(bounds)
                val dm = resources.displayMetrics
                val isFullScreen = bounds.width() >= dm.widthPixels && bounds.height() >= (dm.heightPixels - 120)
                if (!isFullScreen && bounds.width() > 60 && bounds.height() > 60) {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    return
                }
            }
        }
    }

    /** Use Shizuku to set policy_control to block/unblock status bar pull-down. */
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

    private fun isSidebarOrFloatPanel(pkg: String): Boolean {
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
               p.contains("floating")
    }

    private fun isSystemUi(packageName: String): Boolean {
        if (isSidebarOrFloatPanel(packageName)) return false
        return packageName.contains("systemui", ignoreCase = true) ||
               packageName.contains("notification", ignoreCase = true) ||
               SYSTEM_UI_PACKAGES.any { packageName == it || packageName.startsWith("$it.") }
    }

    fun startBlocking() {
        handler.removeCallbacks(shadeBlocker)
        handler.post(shadeBlocker)
        // Immediately block status bar expansion
        PrivilegedToggle.setStatusBarExpansion(true)
        statusBarBlocked = true
    }

    fun stopBlocking() {
        handler.removeCallbacks(shadeBlocker)
        if (shouldBlock()) {
            handler.post(shadeBlocker)
        } else {
            syncOverlay(false)
            // Unblock status bar
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

        if (shouldBlock()) {
            startBlocking()
        }
    }

    override fun onDestroy() {
        instance = null
        if (StrictModeManager.serviceRef === this) StrictModeManager.serviceRef = null
        handler.removeCallbacks(shadeBlocker)
        // Always unblock status bar on destroy
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

        if (isPermissionOrSystemDialog(eventPkg)) {
            return
        }

        // Block notification shade / system panels
        if (isSystemUi(eventPkg)) {
            dismissNotificationShade()
        }

        // Block floating windows / side panels
        if (isSidebarOrFloatPanel(eventPkg)) {
            dismissNotificationShade()
            performGlobalAction(GLOBAL_ACTION_BACK)
            PrivilegedToggle.forceStop(eventPkg)
            bringFocusActivityToFront(if (PomodoroManager.isWorkSessionActive()) "pomodoro" else "strict")
            return
        }

        checkAndBlockFloatingWindows()

        if (PomodoroManager.isWorkSessionActive()) {
            val allowed = PomodoroManager.allowedPackages
            if (!allowed.contains(eventPkg) && !isSystemUi(eventPkg) && eventPkg != packageName) {
                performGlobalAction(GLOBAL_ACTION_HOME)
                performGlobalAction(GLOBAL_ACTION_BACK)
                PrivilegedToggle.forceStop(eventPkg)
                bringFocusActivityToFront("pomodoro")
            }
            return
        }

        if (StrictModeManager.isActive()) {
            if (!isSystemUi(eventPkg) && eventPkg != packageName) {
                performGlobalAction(GLOBAL_ACTION_HOME)
                performGlobalAction(GLOBAL_ACTION_BACK)
                PrivilegedToggle.forceStop(eventPkg)
                bringFocusActivityToFront("strict")
            }
        }
    }

    private fun bringFocusActivityToFront(tab: String) {
        try {
            val intent = Intent(this, FocusActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
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
