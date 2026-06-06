package com.android.synclab.glimpse.presentation.feature

import com.android.synclab.glimpse.data.model.BatteryChargeStatus
import com.android.synclab.glimpse.data.model.ControllerInfo
import com.android.synclab.glimpse.domain.usecase.ProtectBatteryUseCases
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectBatteryPlannerTest {
    private val planner = ProtectBatteryPlanner()

    @Test
    fun onToggle_enableWithNotificationPermissionEnablesRuntime() {
        val useCases = FakeProtectBatteryUseCases()
        val planner = ProtectBatteryPlanner(useCases)
        val port = FakeUiPort(hasNotificationPermission = true)

        planner.onToggle(enabled = true, port = port)

        assertFalse(port.pendingNotificationPermission)
        assertTrue(port.selectedEnabled)
        assertTrue(useCases.enabled)
        assertEquals(1, port.enabledMessages)
        assertEquals(0, port.renderCount)
        assertFalse(port.notificationPermissionRequested)
    }

    @Test
    fun onToggle_enableWithoutNotificationPermissionRequestsPermission() {
        val useCases = FakeProtectBatteryUseCases(enabled = true)
        val planner = ProtectBatteryPlanner(useCases)
        val port = FakeUiPort(
            hasNotificationPermission = false,
            selectedEnabled = true
        )

        planner.onToggle(enabled = true, port = port)

        assertTrue(port.pendingNotificationPermission)
        assertFalse(port.selectedEnabled)
        assertFalse(useCases.enabled)
        assertTrue(port.notificationPermissionRequested)
        assertEquals(1, port.permissionRequiredMessages)
        assertEquals(1, port.renderCount)
    }

    @Test
    fun onToggle_disableDisablesRuntime() {
        val useCases = FakeProtectBatteryUseCases(
            enabled = true,
            alertedControllerIds = setOf("controller-1")
        )
        val planner = ProtectBatteryPlanner(useCases)
        val port = FakeUiPort(
            hasNotificationPermission = false,
            selectedEnabled = true,
            pendingNotificationPermission = true
        )

        planner.onToggle(enabled = false, port = port)

        assertFalse(port.pendingNotificationPermission)
        assertFalse(port.selectedEnabled)
        assertFalse(useCases.enabled)
        assertTrue(useCases.observedAlertedControllerIds.isEmpty())
        assertEquals(1, useCases.cancelCount)
        assertEquals(1, port.disabledMessages)
    }

    @Test
    fun onNotificationPermissionResult_grantedWithPendingEnablesRuntime() {
        val useCases = FakeProtectBatteryUseCases()
        val planner = ProtectBatteryPlanner(useCases)
        val port = FakeUiPort(
            hasNotificationPermission = true,
            pendingNotificationPermission = true
        )

        planner.onNotificationPermissionResult(granted = true, port = port)

        assertFalse(port.pendingNotificationPermission)
        assertTrue(port.selectedEnabled)
        assertTrue(useCases.enabled)
        assertEquals(1, port.enabledMessages)
        assertEquals(1, port.renderCount)
    }

    @Test
    fun onNotificationPermissionResult_deniedWithPendingRestoresRuntimeState() {
        val useCases = FakeProtectBatteryUseCases(enabled = true)
        val planner = ProtectBatteryPlanner(useCases)
        val port = FakeUiPort(
            hasNotificationPermission = false,
            selectedEnabled = false,
            pendingNotificationPermission = true
        )

        planner.onNotificationPermissionResult(granted = false, port = port)

        assertFalse(port.pendingNotificationPermission)
        assertTrue(port.selectedEnabled)
        assertEquals(1, port.renderCount)
        assertEquals(0, port.enabledMessages)
    }

    @Test
    fun onNotificationPermissionResult_withoutPendingDoesNothing() {
        val useCases = FakeProtectBatteryUseCases()
        val planner = ProtectBatteryPlanner(useCases)
        val port = FakeUiPort(
            hasNotificationPermission = true,
            pendingNotificationPermission = false
        )

        planner.onNotificationPermissionResult(granted = true, port = port)

        assertFalse(port.pendingNotificationPermission)
        assertFalse(port.selectedEnabled)
        assertFalse(useCases.enabled)
        assertEquals(0, port.renderCount)
    }

    @Test
    fun onManualEnable_setsEnabledAndRunsControllerCheck() {
        val useCases = FakeProtectBatteryUseCases(
            controllers = listOf(controller(percent = 79, status = BatteryChargeStatus.CHARGING))
        )
        val planner = ProtectBatteryPlanner(useCases)

        planner.onManualEnable(DEFAULT_CONTROLLER_NAME)

        assertTrue(useCases.enabled)
        assertTrue(useCases.observedAlertedControllerIds.isEmpty())
        assertEquals(1, useCases.scheduleCount)
        assertEquals(0, useCases.cancelCount)
    }

    @Test
    fun onManualDisable_disablesRuntimeClearsAlertsAndCancelsCheck() {
        val useCases = FakeProtectBatteryUseCases(
            enabled = true,
            alertedControllerIds = setOf("controller-1")
        )
        val planner = ProtectBatteryPlanner(useCases)

        planner.onManualDisable()

        assertFalse(useCases.enabled)
        assertTrue(useCases.observedAlertedControllerIds.isEmpty())
        assertEquals(1, useCases.cancelCount)
    }

    @Test
    fun onCheckRequested_disabledClearsAlertsAndCancelsCheck() {
        val useCases = FakeProtectBatteryUseCases(
            enabled = false,
            alertedControllerIds = setOf("controller-1")
        )
        val planner = ProtectBatteryPlanner(useCases)

        planner.onCheckRequested(DEFAULT_CONTROLLER_NAME)

        assertTrue(useCases.observedAlertedControllerIds.isEmpty())
        assertEquals(1, useCases.cancelCount)
        assertEquals(0, useCases.scheduleCount)
    }

    @Test
    fun onCheckRequested_checkAtThresholdPostsControllerAlertOnceAndSchedulesNextCheck() {
        val useCases = FakeProtectBatteryUseCases(
            enabled = true,
            controllers = listOf(controller(percent = 80, status = BatteryChargeStatus.CHARGING))
        )
        val planner = ProtectBatteryPlanner(useCases)

        planner.onCheckRequested(DEFAULT_CONTROLLER_NAME)

        assertEquals(setOf("controller-1"), useCases.observedAlertedControllerIds)
        assertEquals(1, useCases.alerts.size)
        assertEquals(80, useCases.alerts.first().percent)
        assertEquals("Controller One", useCases.alerts.first().controllerName)
        assertEquals(1, useCases.scheduleCount)
        assertEquals(0, useCases.cancelCount)
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

    private fun controller(
        id: String = "controller-1",
        name: String = "Controller One",
        percent: Int?,
        status: BatteryChargeStatus,
        descriptor: String? = id
    ): ControllerInfo {
        return ControllerInfo(
            deviceId = 1,
            name = name,
            vendorId = 1356,
            productId = 2508,
            descriptor = descriptor,
            batteryPercent = percent,
            batteryStatus = status
        )
    }

    private class FakeUiPort(
        private val hasNotificationPermission: Boolean,
        selectedEnabled: Boolean = false,
        pendingNotificationPermission: Boolean = false
    ) : ProtectBatteryUiPort {
        private var selectedEnabledState: Boolean = selectedEnabled
        private var pendingNotificationPermissionState: Boolean = pendingNotificationPermission
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

        override fun defaultControllerName(): String = DEFAULT_CONTROLLER_NAME

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

    private class FakeProtectBatteryUseCases(
        enabled: Boolean = false,
        alertedControllerIds: Set<String> = emptySet(),
        var controllers: List<ControllerInfo> = emptyList()
    ) : ProtectBatteryUseCases() {
        private var enabledState: Boolean = enabled
        private var alertedControllerIdsState: Set<String> = alertedControllerIds
        val enabled: Boolean
            get() = enabledState
        val observedAlertedControllerIds: Set<String>
            get() = alertedControllerIdsState
        var scheduleCount = 0
        var cancelCount = 0
        val alerts = mutableListOf<ProtectBatteryAlert>()

        override fun isEnabled(): Boolean = enabledState

        override fun setEnabled(enabled: Boolean) {
            enabledState = enabled
        }

        override fun getAlertedControllerIds(): Set<String> = alertedControllerIdsState

        override fun setAlertedControllerIds(controllerIds: Set<String>) {
            alertedControllerIdsState = controllerIds
        }

        override fun getConnectedPs4Controllers(defaultDeviceName: String): List<ControllerInfo> {
            return controllers
        }

        override fun scheduleNextCheck() {
            scheduleCount += 1
        }

        override fun cancelNextCheck() {
            cancelCount += 1
        }

        override fun postThresholdAlert(
            controllerId: String,
            controllerName: String,
            percent: Int
        ) {
            alerts += ProtectBatteryAlert(
                controllerId = controllerId,
                controllerName = controllerName,
                percent = percent
            )
        }
    }

    companion object {
        private const val DEFAULT_CONTROLLER_NAME = "Unknown Controller"
    }
}
