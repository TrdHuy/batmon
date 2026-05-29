package com.android.synclab.glimpse.presentation.feature

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectBatteryPlannerTest {
    private val planner = ProtectBatteryPlanner()

    @Test
    fun onToggle_enableWithNotificationPermissionEnablesRuntime() {
        val port = FakeUiPort(hasNotificationPermission = true)

        planner.onToggle(enabled = true, port = port)

        assertFalse(port.pendingNotificationPermission)
        assertTrue(port.selectedEnabled)
        assertTrue(port.runtimeEnabled)
        assertEquals(1, port.enabledMessages)
        assertEquals(0, port.renderCount)
        assertFalse(port.notificationPermissionRequested)
    }

    @Test
    fun onToggle_enableWithoutNotificationPermissionRequestsPermission() {
        val port = FakeUiPort(
            hasNotificationPermission = false,
            runtimeEnabled = true
        )

        planner.onToggle(enabled = true, port = port)

        assertTrue(port.pendingNotificationPermission)
        assertFalse(port.selectedEnabled)
        assertFalse(port.runtimeEnabled)
        assertTrue(port.notificationPermissionRequested)
        assertEquals(1, port.permissionRequiredMessages)
        assertEquals(1, port.renderCount)
    }

    @Test
    fun onToggle_disableDisablesRuntime() {
        val port = FakeUiPort(
            hasNotificationPermission = false,
            runtimeEnabled = true,
            selectedEnabled = true,
            pendingNotificationPermission = true
        )

        planner.onToggle(enabled = false, port = port)

        assertFalse(port.pendingNotificationPermission)
        assertFalse(port.selectedEnabled)
        assertFalse(port.runtimeEnabled)
        assertEquals(1, port.disabledMessages)
    }

    @Test
    fun onNotificationPermissionResult_grantedWithPendingEnablesRuntime() {
        val port = FakeUiPort(
            hasNotificationPermission = true,
            pendingNotificationPermission = true
        )

        planner.onNotificationPermissionResult(granted = true, port = port)

        assertFalse(port.pendingNotificationPermission)
        assertTrue(port.selectedEnabled)
        assertTrue(port.runtimeEnabled)
        assertEquals(1, port.enabledMessages)
        assertEquals(1, port.renderCount)
    }

    @Test
    fun onNotificationPermissionResult_deniedWithPendingRestoresRuntimeState() {
        val port = FakeUiPort(
            hasNotificationPermission = false,
            runtimeEnabled = true,
            selectedEnabled = false,
            pendingNotificationPermission = true
        )

        planner.onNotificationPermissionResult(granted = false, port = port)

        assertFalse(port.pendingNotificationPermission)
        assertTrue(port.selectedEnabled)
        assertTrue(port.runtimeEnabled)
        assertEquals(1, port.renderCount)
        assertEquals(0, port.enabledMessages)
    }

    @Test
    fun onNotificationPermissionResult_withoutPendingDoesNothing() {
        val port = FakeUiPort(
            hasNotificationPermission = true,
            pendingNotificationPermission = false
        )

        planner.onNotificationPermissionResult(granted = true, port = port)

        assertFalse(port.pendingNotificationPermission)
        assertFalse(port.selectedEnabled)
        assertFalse(port.runtimeEnabled)
        assertEquals(0, port.renderCount)
    }

    @Test
    fun onManualEnable_setsEnabledAndRunsBatteryCheck() {
        val port = FakeRuntimePort(
            battery = PhoneBatterySnapshot(percent = 79, isCharging = true)
        )

        planner.onManualEnable(port)

        assertTrue(port.enabled)
        assertFalse(port.alertShownForChargeSession)
        assertEquals(1, port.scheduleCount)
        assertEquals(0, port.cancelCount)
        assertEquals(1, port.logCount)
    }

    @Test
    fun onManualDisable_disablesRuntimeAndCancelsCheck() {
        val port = FakeRuntimePort(
            enabled = true,
            alertShownForChargeSession = true
        )

        planner.onManualDisable(port)

        assertFalse(port.enabled)
        assertFalse(port.alertShownForChargeSession)
        assertEquals(1, port.cancelCount)
    }

    @Test
    fun onReceiverEvent_powerDisconnectedResetsAlertAndCancelsCheck() {
        val port = FakeRuntimePort(
            enabled = true,
            alertShownForChargeSession = true
        )

        planner.onReceiverEvent(
            event = ProtectBatteryReceiverEvent.PowerDisconnected,
            port = port
        )

        assertFalse(port.alertShownForChargeSession)
        assertEquals(1, port.cancelCount)
        assertEquals(0, port.scheduleCount)
    }

    @Test
    fun onReceiverEvent_checkAtThresholdPostsAlertOnceAndSchedulesNextCheck() {
        val port = FakeRuntimePort(
            enabled = true,
            battery = PhoneBatterySnapshot(percent = 80, isCharging = true)
        )

        planner.onReceiverEvent(
            event = ProtectBatteryReceiverEvent.Check,
            port = port
        )

        assertTrue(port.alertShownForChargeSession)
        assertEquals(80, port.lastAlertPercent)
        assertEquals(1, port.scheduleCount)
        assertEquals(0, port.cancelCount)
        assertEquals(1, port.logCount)
    }

    @Test
    fun planBatteryCheck_disabledDoesNotNotifyOrSchedule() {
        val decision = planner.planBatteryCheck(
            enabled = false,
            battery = PhoneBatterySnapshot(percent = 90, isCharging = true),
            alertShownForChargeSession = true
        )

        assertFalse(decision.shouldNotify)
        assertFalse(decision.shouldScheduleNextCheck)
        assertFalse(decision.alertShownForChargeSession)
    }

    @Test
    fun planBatteryCheck_notChargingResetsAlertSession() {
        val decision = planner.planBatteryCheck(
            enabled = true,
            battery = PhoneBatterySnapshot(percent = 80, isCharging = false),
            alertShownForChargeSession = true
        )

        assertFalse(decision.shouldNotify)
        assertFalse(decision.shouldScheduleNextCheck)
        assertFalse(decision.alertShownForChargeSession)
    }

    @Test
    fun planBatteryCheck_chargingBelowThresholdSchedulesWithoutAlert() {
        val decision = planner.planBatteryCheck(
            enabled = true,
            battery = PhoneBatterySnapshot(percent = 79, isCharging = true),
            alertShownForChargeSession = false
        )

        assertFalse(decision.shouldNotify)
        assertTrue(decision.shouldScheduleNextCheck)
        assertFalse(decision.alertShownForChargeSession)
    }

    @Test
    fun planBatteryCheck_chargingAtThresholdNotifiesOnceAndKeepsScheduling() {
        val decision = planner.planBatteryCheck(
            enabled = true,
            battery = PhoneBatterySnapshot(percent = 80, isCharging = true),
            alertShownForChargeSession = false
        )

        assertTrue(decision.shouldNotify)
        assertTrue(decision.shouldScheduleNextCheck)
        assertTrue(decision.alertShownForChargeSession)
    }

    @Test
    fun planBatteryCheck_chargingAtThresholdAfterAlertDoesNotNotifyAgain() {
        val decision = planner.planBatteryCheck(
            enabled = true,
            battery = PhoneBatterySnapshot(percent = 95, isCharging = true),
            alertShownForChargeSession = true
        )

        assertFalse(decision.shouldNotify)
        assertTrue(decision.shouldScheduleNextCheck)
        assertTrue(decision.alertShownForChargeSession)
    }

    private class FakeUiPort(
        private val hasNotificationPermission: Boolean,
        runtimeEnabled: Boolean = false,
        selectedEnabled: Boolean = false,
        pendingNotificationPermission: Boolean = false
    ) : ProtectBatteryUiPort {
        private var runtimeEnabledState: Boolean = runtimeEnabled
        private var selectedEnabledState: Boolean = selectedEnabled
        private var pendingNotificationPermissionState: Boolean = pendingNotificationPermission
        val runtimeEnabled: Boolean
            get() = runtimeEnabledState
        val selectedEnabled: Boolean
            get() = selectedEnabledState
        val pendingNotificationPermission: Boolean
            get() = pendingNotificationPermissionState
        var notificationPermissionRequested = false
        var renderCount = 0
        var permissionRequiredMessages = 0
        var enabledMessages = 0
        var disabledMessages = 0

        override fun hasNotificationPermission(): Boolean = hasNotificationPermission

        override fun hasPendingNotificationPermission(): Boolean = pendingNotificationPermissionState

        override fun setPendingNotificationPermission(pending: Boolean) {
            pendingNotificationPermissionState = pending
        }

        override fun setSelectedEnabled(enabled: Boolean) {
            selectedEnabledState = enabled
        }

        override fun isRuntimeEnabled(): Boolean = runtimeEnabledState

        override fun enableRuntime() {
            runtimeEnabledState = true
        }

        override fun disableRuntime() {
            runtimeEnabledState = false
        }

        override fun requestNotificationPermission() {
            notificationPermissionRequested = true
        }

        override fun render() {
            renderCount += 1
        }

        override fun showNotificationPermissionRequired() {
            permissionRequiredMessages += 1
        }

        override fun showEnabled() {
            enabledMessages += 1
        }

        override fun showDisabled() {
            disabledMessages += 1
        }
    }

    private class FakeRuntimePort(
        enabled: Boolean = false,
        alertShownForChargeSession: Boolean = false,
        var battery: PhoneBatterySnapshot? = null
    ) : ProtectBatteryRuntimePort {
        private var enabledState: Boolean = enabled
        private var alertShownForChargeSessionState: Boolean = alertShownForChargeSession
        val enabled: Boolean
            get() = enabledState
        val alertShownForChargeSession: Boolean
            get() = alertShownForChargeSessionState
        var scheduleCount = 0
        var cancelCount = 0
        var logCount = 0
        var lastAlertPercent: Int? = null

        override fun isEnabled(): Boolean = enabledState

        override fun setEnabled(enabled: Boolean) {
            enabledState = enabled
        }

        override fun isAlertShownForChargeSession(): Boolean = alertShownForChargeSessionState

        override fun setAlertShownForChargeSession(shown: Boolean) {
            alertShownForChargeSessionState = shown
        }

        override fun readPhoneBattery(): PhoneBatterySnapshot? = battery

        override fun scheduleNextCheck() {
            scheduleCount += 1
        }

        override fun cancelNextCheck() {
            cancelCount += 1
        }

        override fun postThresholdAlert(percent: Int) {
            lastAlertPercent = percent
        }

        override fun logCheck(
            enabled: Boolean,
            battery: PhoneBatterySnapshot?,
            decision: ProtectBatteryDecision
        ) {
            logCount += 1
        }
    }
}
