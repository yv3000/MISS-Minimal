package com.minimalist.launcher

import android.app.usage.UsageEvents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SOTManagerTest {

    private val countAll: (String) -> Boolean = { it != "launcher" }

    private fun total(start: Long, end: Long, events: List<SOTManager.TimedEvent>) =
        SOTManager.computeAppUsage(start, end, events, countAll).values.sum()

    @Test
    fun countsForegroundAppsOnlyWhileScreenIsInteractive() {
        val events = listOf(
            event(10, UsageEvents.Event.ACTIVITY_RESUMED, "app"),
            event(30, UsageEvents.Event.SCREEN_NON_INTERACTIVE),
            event(60, UsageEvents.Event.SCREEN_INTERACTIVE),
            event(80, UsageEvents.Event.ACTIVITY_PAUSED, "app")
        )

        // 10..30 = 20 counted, 30..60 screen off, 60..80 = 20 counted.
        assertEquals(40, total(0, 100, events))
    }

    @Test
    fun onlyTheTopAppEarnsTimeAndLauncherIsExcluded() {
        val events = listOf(
            event(5, UsageEvents.Event.ACTIVITY_RESUMED, "launcher"),
            event(10, UsageEvents.Event.ACTIVITY_RESUMED, "one"),
            event(20, UsageEvents.Event.ACTIVITY_RESUMED, "two"),
            event(30, UsageEvents.Event.ACTIVITY_PAUSED, "one"),
            event(40, UsageEvents.Event.ACTIVITY_STOPPED, "two")
        )

        val usage = SOTManager.computeAppUsage(0, 50, events, countAll)
        assertEquals(10L, usage["one"]!!)   // 10..20
        assertEquals(20L, usage["two"]!!)   // 20..40, late PAUSED of "one" must not cut it
        assertFalse(usage.containsKey("launcher"))
        assertEquals(30L, usage.values.sum())
    }

    @Test
    fun activityResumedBeforeMidnightSeedsCurrentDay() {
        val events = listOf(
            event(-10, UsageEvents.Event.ACTIVITY_RESUMED, "app"),
            event(20, UsageEvents.Event.ACTIVITY_PAUSED, "app")
        )

        assertEquals(20, total(0, 50, events))
    }

    @Test
    fun gapBetweenAppsIsNotCounted() {
        val events = listOf(
            event(10, UsageEvents.Event.ACTIVITY_RESUMED, "one"),
            event(20, UsageEvents.Event.ACTIVITY_PAUSED, "one"),
            event(35, UsageEvents.Event.ACTIVITY_RESUMED, "two"),
            event(45, UsageEvents.Event.ACTIVITY_PAUSED, "two")
        )

        // This is the old bug: wall-clock 10..45 = 35, real app time = 10 + 10 = 20.
        assertEquals(20, total(0, 50, events))
    }

    @Test
    fun staleResumedEntryDoesNotInflateTotal() {
        val events = listOf(
            event(10, UsageEvents.Event.ACTIVITY_RESUMED, "one"),
            // "one" never sends PAUSED; launcher comes to the front instead.
            event(20, UsageEvents.Event.ACTIVITY_RESUMED, "launcher")
        )

        assertEquals(10, total(0, 100, events))
    }

    @Test
    fun runningAppIsCountedUpToNow() {
        val events = listOf(event(10, UsageEvents.Event.ACTIVITY_RESUMED, "app"))
        assertEquals(40, total(0, 50, events))
    }

    @Test
    fun shutdownStopsCounting() {
        val events = listOf(
            event(10, UsageEvents.Event.ACTIVITY_RESUMED, "app"),
            event(20, UsageEvents.Event.DEVICE_SHUTDOWN)
        )

        assertEquals(10, total(0, 100, events))
    }

    @Test
    fun lateCloseOfAnotherActivityInSamePackageDoesNotEndCurrentActivity() {
        val events = listOf(
            event(10, UsageEvents.Event.ACTIVITY_RESUMED, "app", "First"),
            event(20, UsageEvents.Event.ACTIVITY_RESUMED, "app", "Second"),
            event(30, UsageEvents.Event.ACTIVITY_PAUSED, "app", "First"),
            event(40, UsageEvents.Event.ACTIVITY_STOPPED, "app", "First"),
            event(50, UsageEvents.Event.ACTIVITY_PAUSED, "app", "Second")
        )

        assertEquals(40, total(0, 100, events))
    }

    @Test
    fun resumeDoesNotOverrideKnownScreenOff() {
        val events = listOf(
            event(10, UsageEvents.Event.ACTIVITY_RESUMED, "one"),
            event(20, UsageEvents.Event.SCREEN_NON_INTERACTIVE),
            event(30, UsageEvents.Event.ACTIVITY_RESUMED, "two"),
            event(60, UsageEvents.Event.SCREEN_INTERACTIVE),
            event(80, UsageEvents.Event.ACTIVITY_STOPPED, "two")
        )

        val usage = SOTManager.computeAppUsage(0, 100, events, countAll)
        assertEquals(mapOf("one" to 10L, "two" to 20L), usage)
    }

    @Test
    fun keyguardExcludesLockedTimeWithoutDiscardingCurrentActivity() {
        val events = listOf(
            event(10, UsageEvents.Event.ACTIVITY_RESUMED, "app"),
            event(20, UsageEvents.Event.KEYGUARD_SHOWN),
            event(30, UsageEvents.Event.ACTIVITY_RESUMED, "app"),
            event(40, UsageEvents.Event.SCREEN_INTERACTIVE),
            event(60, UsageEvents.Event.KEYGUARD_HIDDEN),
            event(80, UsageEvents.Event.ACTIVITY_PAUSED, "app")
        )

        assertEquals(30, total(0, 100, events))
    }

    @Test
    fun unlockingDoesNotOverrideScreenOffOrRestorePausedActivity() {
        val events = listOf(
            event(10, UsageEvents.Event.ACTIVITY_RESUMED, "app"),
            event(20, UsageEvents.Event.KEYGUARD_SHOWN),
            event(30, UsageEvents.Event.SCREEN_NON_INTERACTIVE),
            event(40, UsageEvents.Event.KEYGUARD_HIDDEN),
            event(50, UsageEvents.Event.ACTIVITY_PAUSED, "app"),
            event(60, UsageEvents.Event.SCREEN_INTERACTIVE)
        )

        assertEquals(10, total(0, 100, events))
    }

    @Test
    fun screenAndKeyguardStateCarryAcrossMidnight() {
        val events = listOf(
            event(-30, UsageEvents.Event.ACTIVITY_RESUMED, "app"),
            event(-20, UsageEvents.Event.KEYGUARD_SHOWN),
            event(-10, UsageEvents.Event.SCREEN_NON_INTERACTIVE),
            event(5, UsageEvents.Event.ACTIVITY_RESUMED, "app"),
            event(10, UsageEvents.Event.SCREEN_INTERACTIVE),
            event(20, UsageEvents.Event.KEYGUARD_HIDDEN),
            event(40, UsageEvents.Event.ACTIVITY_PAUSED, "app")
        )

        assertEquals(20, total(0, 100, events))
    }

    @Test
    fun startupWithoutScreenEventsUsesActivityFallbackAndClearsOldState() {
        val events = listOf(
            event(10, UsageEvents.Event.ACTIVITY_RESUMED, "before"),
            event(20, UsageEvents.Event.KEYGUARD_SHOWN),
            event(25, UsageEvents.Event.DEVICE_SHUTDOWN),
            event(30, UsageEvents.Event.ACTIVITY_RESUMED, "shutdownNoise"),
            event(50, UsageEvents.Event.DEVICE_STARTUP),
            event(70, UsageEvents.Event.ACTIVITY_RESUMED, "after"),
            event(90, UsageEvents.Event.ACTIVITY_PAUSED, "after")
        )

        assertEquals(
            mapOf("before" to 10L, "after" to 20L),
            SOTManager.computeAppUsage(0, 100, events, countAll)
        )
    }

    @Test
    fun startupDoesNotCreditAnUnclosedActivityAcrossRebootAndMidnight() {
        val events = listOf(
            event(-10, UsageEvents.Event.ACTIVITY_RESUMED, "before"),
            event(20, UsageEvents.Event.DEVICE_STARTUP),
            event(30, UsageEvents.Event.ACTIVITY_RESUMED, "after"),
            event(40, UsageEvents.Event.ACTIVITY_PAUSED, "after")
        )

        assertEquals(mapOf("after" to 10L), SOTManager.computeAppUsage(0, 100, events, countAll))
    }

    @Test
    fun startupDoesNotOverrideSubsequentExplicitScreenOff() {
        val events = listOf(
            event(10, UsageEvents.Event.DEVICE_STARTUP),
            event(20, UsageEvents.Event.SCREEN_NON_INTERACTIVE),
            event(30, UsageEvents.Event.ACTIVITY_RESUMED, "app"),
            event(40, UsageEvents.Event.SCREEN_INTERACTIVE)
        )

        assertEquals(10, total(0, 50, events))
    }

    @Test
    fun packageFilterExcludesLauncherSystemAndNonLaunchable() {
        val launchable = setOf("com.whatsapp", "com.minimalist.launcher")
        assertTrue(SOTManager.isCountedPackage("com.whatsapp", "com.minimalist.launcher", launchable))
        assertFalse(SOTManager.isCountedPackage("com.minimalist.launcher", "com.minimalist.launcher", launchable))
        assertFalse(SOTManager.isCountedPackage("com.android.systemui", "com.minimalist.launcher", launchable))
        assertFalse(SOTManager.isCountedPackage("android", "com.minimalist.launcher", launchable))
        assertFalse(SOTManager.isCountedPackage("com.some.service", "com.minimalist.launcher", launchable))
        assertFalse(SOTManager.isCountedPackage("", "com.minimalist.launcher", launchable))
        // Package manager query failed -> don't drop everything.
        assertTrue(SOTManager.isCountedPackage("com.some.service", "com.minimalist.launcher", emptySet()))
    }

    @Test
    fun formatsDurations() {
        assertEquals("0m", SOTManager.format(0))
        assertEquals("59m", SOTManager.format(59 * 60_000L))
        assertEquals("1h 0m", SOTManager.format(60 * 60_000L))
        assertEquals("6h 19m", SOTManager.format((6 * 60 + 19) * 60_000L))
    }

    private fun event(timestamp: Long, type: Int, pkg: String = "", className: String? = null) =
        SOTManager.TimedEvent(timestamp, type, pkg, className)
}
