package com.minimalist.launcher

import android.app.usage.UsageEvents
import org.junit.Assert.assertEquals
import org.junit.Test

class SOTManagerTest {
    @Test
    fun countsForegroundAppsOnlyWhileScreenIsInteractive() {
        val events = listOf(
            event(10, UsageEvents.Event.ACTIVITY_RESUMED, "app", "Main"),
            event(30, UsageEvents.Event.SCREEN_NON_INTERACTIVE),
            event(60, UsageEvents.Event.SCREEN_INTERACTIVE),
            event(80, UsageEvents.Event.ACTIVITY_PAUSED, "app", "Main")
        )

        assertEquals(40, SOTManager.calculateAppScreenTime(0, 100, events, "launcher"))
    }

    @Test
    fun overlappingActivitiesAreCountedOnceAndLauncherIsExcluded() {
        val events = listOf(
            event(5, UsageEvents.Event.ACTIVITY_RESUMED, "launcher", "Home"),
            event(10, UsageEvents.Event.ACTIVITY_RESUMED, "app", "One"),
            event(20, UsageEvents.Event.ACTIVITY_RESUMED, "app", "Two"),
            event(30, UsageEvents.Event.ACTIVITY_PAUSED, "app", "One"),
            event(40, UsageEvents.Event.ACTIVITY_STOPPED, "app", "Two")
        )

        assertEquals(30, SOTManager.calculateAppScreenTime(0, 50, events, "launcher"))
    }

    @Test
    fun activityResumedBeforeMidnightSeedsCurrentDay() {
        val events = listOf(
            event(-10, UsageEvents.Event.ACTIVITY_RESUMED, "app", "Main"),
            event(20, UsageEvents.Event.ACTIVITY_PAUSED, "app", "Main")
        )

        assertEquals(20, SOTManager.calculateAppScreenTime(0, 50, events, "launcher"))
    }

    @Test
    fun sameActivityInstancesDoNotCancelEachOther() {
        val events = listOf(
            event(10, UsageEvents.Event.ACTIVITY_RESUMED, "app", "Main"),
            event(20, UsageEvents.Event.ACTIVITY_RESUMED, "app", "Main"),
            event(30, UsageEvents.Event.ACTIVITY_PAUSED, "app", "Main"),
            event(40, UsageEvents.Event.ACTIVITY_STOPPED, "app", "Main")
        )

        assertEquals(30, SOTManager.calculateAppScreenTime(0, 50, events, "launcher"))
    }

    private fun event(timestamp: Long, type: Int, pkg: String = "", cls: String = "") =
        SOTManager.TimedEvent(timestamp, type, pkg, cls)
}
