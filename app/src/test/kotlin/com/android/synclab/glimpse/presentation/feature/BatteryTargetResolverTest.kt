package com.android.synclab.glimpse.presentation.feature

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryTargetResolverTest {
    private val resolver = BatteryTargetResolver()

    @Test
    fun resolveTargets_whenBmAndLboTargetDifferent_usesSeparateNotificationTarget() {
        val targets = resolver.resolveTargets(
            state = state(
                isMonitoringEnabled = true,
                isOverlayVisible = true,
                activeMonitoringControllerIdentifier = "controller-bm",
                activeOverlayControllerIdentifier = "controller-lbo"
            )
        )

        assertEquals("controller-lbo", targets.overlayControllerIdentifier)
        assertEquals("controller-bm", targets.notificationControllerIdentifier)
        assertFalse(targets.reuseOverlaySnapshotForNotification)
    }

    @Test
    fun resolveTargets_whenBmOffAndLboOn_reusesOverlaySnapshotForNotification() {
        val targets = resolver.resolveTargets(
            state = state(
                isMonitoringEnabled = false,
                isOverlayVisible = true,
                activeMonitoringControllerIdentifier = "controller-bm",
                activeOverlayControllerIdentifier = "controller-lbo"
            )
        )

        assertEquals("controller-lbo", targets.overlayControllerIdentifier)
        assertEquals("controller-lbo", targets.notificationControllerIdentifier)
        assertTrue(targets.reuseOverlaySnapshotForNotification)
    }

    @Test
    fun resolveTargets_whenBmOnAndLboOff_usesMonitoringTargetOnly() {
        val targets = resolver.resolveTargets(
            state = state(
                isMonitoringEnabled = true,
                isOverlayVisible = false,
                activeMonitoringControllerIdentifier = "controller-bm",
                activeOverlayControllerIdentifier = "controller-lbo"
            )
        )

        assertEquals(null, targets.overlayControllerIdentifier)
        assertEquals("controller-bm", targets.notificationControllerIdentifier)
        assertFalse(targets.reuseOverlaySnapshotForNotification)
    }

    @Test
    fun resolveTargets_whenTargetsMatch_reusesOverlaySnapshot() {
        val targets = resolver.resolveTargets(
            state = state(
                isMonitoringEnabled = true,
                isOverlayVisible = true,
                activeMonitoringControllerIdentifier = "controller-1",
                activeOverlayControllerIdentifier = "controller-1"
            )
        )

        assertEquals("controller-1", targets.overlayControllerIdentifier)
        assertEquals("controller-1", targets.notificationControllerIdentifier)
        assertTrue(targets.reuseOverlaySnapshotForNotification)
    }

    @Test
    fun notificationIconLevelFor_mapsBatteryThresholds() {
        assertEquals(
            BatteryTargetResolver.NotificationIconLevel.UNKNOWN,
            resolver.notificationIconLevelFor(null)
        )
        assertEquals(
            BatteryTargetResolver.NotificationIconLevel.BATTERY_0,
            resolver.notificationIconLevelFor(10)
        )
        assertEquals(
            BatteryTargetResolver.NotificationIconLevel.BATTERY_25,
            resolver.notificationIconLevelFor(35)
        )
        assertEquals(
            BatteryTargetResolver.NotificationIconLevel.BATTERY_50,
            resolver.notificationIconLevelFor(60)
        )
        assertEquals(
            BatteryTargetResolver.NotificationIconLevel.BATTERY_75,
            resolver.notificationIconLevelFor(85)
        )
        assertEquals(
            BatteryTargetResolver.NotificationIconLevel.BATTERY_100,
            resolver.notificationIconLevelFor(86)
        )
    }

    private fun state(
        isMonitoringEnabled: Boolean,
        isOverlayVisible: Boolean,
        activeMonitoringControllerIdentifier: String?,
        activeOverlayControllerIdentifier: String?
    ): BatteryTargetResolver.RuntimeState {
        return BatteryTargetResolver.RuntimeState(
            isMonitoringEnabled = isMonitoringEnabled,
            isOverlayVisible = isOverlayVisible,
            activeMonitoringControllerIdentifier = activeMonitoringControllerIdentifier,
            activeOverlayControllerIdentifier = activeOverlayControllerIdentifier
        )
    }
}
