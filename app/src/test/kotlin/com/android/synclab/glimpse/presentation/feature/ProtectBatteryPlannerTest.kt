package com.android.synclab.glimpse.presentation.feature

import com.android.synclab.glimpse.data.model.BatteryChargeStatus
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
    fun onManualEnable_setsEnabledAndRunsControllerCheck() {
        val port = FakeRuntimePort(
            batteries = listOf(controllerBattery(percent = 79, status = BatteryChargeStatus.CHARGING))
        )

        planner.onManualEnable(port)

        assertTrue(port.enabled)
        assertTrue(port.observedAlertedControllerIds.isEmpty())
        assertEquals(1, port.scheduleCount)
        assertEquals(0, port.cancelCount)
        assertEquals(1, port.logCount)
    }

    @Test
    fun onManualDisable_disablesRuntimeClearsAlertsAndCancelsCheck() {
        val port = FakeRuntimePort(
            enabled = true,
            alertedControllerIds = setOf("controller-1")
        )

        planner.onManualDisable(port)

        assertFalse(port.enabled)
        assertTrue(port.observedAlertedControllerIds.isEmpty())
        assertEquals(1, port.cancelCount)
    }

    @Test
    fun onReceiverEvent_disabledClearsAlertsAndCancelsCheck() {
        val port = FakeRuntimePort(
            enabled = false,
            alertedControllerIds = setOf("controller-1")
        )

        planner.onReceiverEvent(port)

        assertTrue(port.observedAlertedControllerIds.isEmpty())
        assertEquals(1, port.cancelCount)
        assertEquals(0, port.scheduleCount)
    }

    @Test
    fun onReceiverEvent_checkAtThresholdPostsControllerAlertOnceAndSchedulesNextCheck() {
        val port = FakeRuntimePort(
            enabled = true,
            batteries = listOf(controllerBattery(percent = 80, status = BatteryChargeStatus.CHARGING))
        )

        planner.onReceiverEvent(port)

        assertEquals(setOf("controller-1"), port.observedAlertedControllerIds)
        assertEquals(1, port.alerts.size)
        assertEquals(80, port.alerts.first().percent)
        assertEquals("Controller One", port.alerts.first().controllerName)
        assertEquals(1, port.scheduleCount)
        assertEquals(0, port.cancelCount)
        assertEquals(1, port.logCount)
    }

    @Test
    fun planBatteryCheck_disabledDoesNotNotifyOrSchedule() {
        val decision = planner.planBatteryCheck(
            enabled = false,
            batteries = listOf(controllerBattery(percent = 90, status = BatteryChargeStatus.CHARGING)),
            alertedControllerIds = setOf("controller-1")
        )

        assertTrue(decision.alerts.isEmpty())
        assertFalse(decision.shouldScheduleNextCheck)
        assertTrue(decision.alertedControllerIds.isEmpty())
    }

    @Test
    fun planBatteryCheck_noControllerSchedulesWithoutAlert() {
        val decision = planner.planBatteryCheck(
            enabled = true,
            batteries = emptyList(),
            alertedControllerIds = setOf("controller-1")
        )

        assertTrue(decision.alerts.isEmpty())
        assertTrue(decision.shouldScheduleNextCheck)
        assertTrue(decision.alertedControllerIds.isEmpty())
    }

    @Test
    fun planBatteryCheck_notChargingResetsAlertSession() {
        val decision = planner.planBatteryCheck(
            enabled = true,
            batteries = listOf(controllerBattery(percent = 80, status = BatteryChargeStatus.NOT_CHARGING)),
            alertedControllerIds = setOf("controller-1")
        )

        assertTrue(decision.alerts.isEmpty())
        assertTrue(decision.shouldScheduleNextCheck)
        assertTrue(decision.alertedControllerIds.isEmpty())
    }

    @Test
    fun planBatteryCheck_unknownStatusDoesNotNotify() {
        val decision = planner.planBatteryCheck(
            enabled = true,
            batteries = listOf(controllerBattery(percent = 80, status = BatteryChargeStatus.UNKNOWN)),
            alertedControllerIds = emptySet()
        )

        assertTrue(decision.alerts.isEmpty())
        assertTrue(decision.alertedControllerIds.isEmpty())
    }

    @Test
    fun planBatteryCheck_chargingBelowThresholdSchedulesWithoutAlert() {
        val decision = planner.planBatteryCheck(
            enabled = true,
            batteries = listOf(controllerBattery(percent = 79, status = BatteryChargeStatus.CHARGING)),
            alertedControllerIds = emptySet()
        )

        assertTrue(decision.alerts.isEmpty())
        assertTrue(decision.shouldScheduleNextCheck)
        assertTrue(decision.alertedControllerIds.isEmpty())
    }

    @Test
    fun planBatteryCheck_chargingAtThresholdNotifiesOnceAndKeepsScheduling() {
        val decision = planner.planBatteryCheck(
            enabled = true,
            batteries = listOf(controllerBattery(percent = 80, status = BatteryChargeStatus.CHARGING)),
            alertedControllerIds = emptySet()
        )

        assertEquals(1, decision.alerts.size)
        assertTrue(decision.shouldScheduleNextCheck)
        assertEquals(setOf("controller-1"), decision.alertedControllerIds)
    }

    @Test
    fun planBatteryCheck_fullAtThresholdNotifiesOnceAndKeepsScheduling() {
        val decision = planner.planBatteryCheck(
            enabled = true,
            batteries = listOf(controllerBattery(percent = 100, status = BatteryChargeStatus.FULL)),
            alertedControllerIds = emptySet()
        )

        assertEquals(1, decision.alerts.size)
        assertTrue(decision.shouldScheduleNextCheck)
        assertEquals(setOf("controller-1"), decision.alertedControllerIds)
    }

    @Test
    fun planBatteryCheck_chargingAtThresholdAfterAlertDoesNotNotifyAgain() {
        val decision = planner.planBatteryCheck(
            enabled = true,
            batteries = listOf(controllerBattery(percent = 95, status = BatteryChargeStatus.CHARGING)),
            alertedControllerIds = setOf("controller-1")
        )

        assertTrue(decision.alerts.isEmpty())
        assertTrue(decision.shouldScheduleNextCheck)
        assertEquals(setOf("controller-1"), decision.alertedControllerIds)
    }

    @Test
    fun planBatteryCheck_belowThresholdAfterAlertResetsSession() {
        val decision = planner.planBatteryCheck(
            enabled = true,
            batteries = listOf(controllerBattery(percent = 79, status = BatteryChargeStatus.CHARGING)),
            alertedControllerIds = setOf("controller-1")
        )

        assertTrue(decision.alerts.isEmpty())
        assertTrue(decision.alertedControllerIds.isEmpty())
    }

    private fun controllerBattery(
        id: String = "controller-1",
        name: String = "Controller One",
        percent: Int?,
        status: BatteryChargeStatus
    ): ControllerBatterySnapshot {
        return ControllerBatterySnapshot(
            controllerId = id,
            controllerName = name,
            percent = percent,
            status = status
        )
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
        alertedControllerIds: Set<String> = emptySet(),
        var batteries: List<ControllerBatterySnapshot> = emptyList()
    ) : ProtectBatteryRuntimePort {
        private var enabledState: Boolean = enabled
        private var alertedControllerIdsState: Set<String> = alertedControllerIds
        val enabled: Boolean
            get() = enabledState
        val observedAlertedControllerIds: Set<String>
            get() = alertedControllerIdsState
        var scheduleCount = 0
        var cancelCount = 0
        var logCount = 0
        val alerts = mutableListOf<ProtectBatteryAlert>()

        override fun isEnabled(): Boolean = enabledState

        override fun setEnabled(enabled: Boolean) {
            enabledState = enabled
        }

        override fun getAlertedControllerIds(): Set<String> = alertedControllerIdsState

        override fun setAlertedControllerIds(controllerIds: Set<String>) {
            alertedControllerIdsState = controllerIds
        }

        override fun readControllerBatteries(): List<ControllerBatterySnapshot> = batteries

        override fun scheduleNextCheck() {
            scheduleCount += 1
        }

        override fun cancelNextCheck() {
            cancelCount += 1
        }

        override fun postThresholdAlert(alert: ProtectBatteryAlert) {
            alerts += alert
        }

        override fun logCheck(
            enabled: Boolean,
            batteries: List<ControllerBatterySnapshot>,
            decision: ProtectBatteryDecision
        ) {
            logCount += 1
        }
    }
}
