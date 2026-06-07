package com.android.synclab.glimpse.presentation.feature

import com.android.synclab.glimpse.data.model.BatteryChargeStatus
import com.android.synclab.glimpse.data.model.ControllerInfo
import com.android.synclab.glimpse.domain.usecase.ProtectBatteryUseCases

class ProtectBatteryPlanner(
    private val useCases: ProtectBatteryUseCases = ProtectBatteryUseCases(),
    private val thresholdPercent: Int = DEFAULT_THRESHOLD_PERCENT
) {
    fun onToggle(
        enabled: Boolean,
        port: ProtectBatteryUiPort
    ) {
        when {
            enabled && !port.hasNotificationPermission() -> {
                port.setPendingNotificationPermission(true)
                port.setSelectedEnabled(false)
                onManualDisable()
                port.render()
                port.requestNotificationPermission()
                port.showNotificationPermissionRequired()
            }

            enabled -> {
                port.setPendingNotificationPermission(false)
                port.setSelectedEnabled(true)
                onManualEnable(port.defaultControllerName())
                port.showEnabled()
            }

            else -> {
                port.setPendingNotificationPermission(false)
                port.setSelectedEnabled(false)
                onManualDisable()
                port.showDisabled()
            }
        }
    }

    fun onNotificationPermissionResult(
        granted: Boolean,
        port: ProtectBatteryUiPort
    ) {
        if (!port.hasPendingNotificationPermission()) {
            return
        }

        port.setPendingNotificationPermission(false)
        if (granted) {
            port.setSelectedEnabled(true)
            onManualEnable(port.defaultControllerName())
            port.showEnabled()
            port.render()
        } else {
            port.setSelectedEnabled(useCases.isEnabled())
            port.render()
        }
    }

    fun isEnabled(): Boolean {
        return useCases.isEnabled()
    }

    fun onManualEnable(defaultControllerName: String) {
        useCases.setEnabled(true)
        onCheckRequested(defaultControllerName)
    }

    fun onManualDisable() {
        useCases.setEnabled(false)
        useCases.setAlertedControllerIds(emptySet())
        useCases.cancelNextCheck()
    }

    fun onCheckRequested(defaultControllerName: String) {
        val enabled = useCases.isEnabled()
        if (!enabled) {
            useCases.setAlertedControllerIds(emptySet())
            useCases.cancelNextCheck()
            return
        }

        val controllers = if (useCases.isProtectBatteryFakeThresholdDetectionEnabled()) {
            listOf(useCases.getFakeThresholdController())
        } else {
            useCases.getConnectedPs4Controllers(defaultControllerName)
        }
        val batteries = controllers.map(::toControllerBatterySnapshot)
        val decision = planBatteryCheck(
            enabled = true,
            batteries = batteries,
            alertedControllerIds = useCases.getAlertedControllerIds()
        )
        useCases.setAlertedControllerIds(decision.alertedControllerIds)
        decision.alerts.forEach { alert ->
            useCases.postThresholdAlert(
                controllerId = alert.controllerId,
                controllerName = alert.controllerName,
                percent = alert.percent
            )
        }
        if (decision.shouldScheduleNextCheck) {
            useCases.scheduleNextCheck()
        } else {
            useCases.cancelNextCheck()
        }
    }

    fun postDevControllerThresholdAlert(percent: Int = DEFAULT_THRESHOLD_PERCENT) {
        useCases.postDevControllerThresholdAlert(percent)
    }

    fun planBatteryCheck(
        enabled: Boolean,
        batteries: List<ControllerBatterySnapshot>,
        alertedControllerIds: Set<String>
    ): ProtectBatteryDecision {
        if (!enabled) {
            return ProtectBatteryDecision(
                alerts = emptyList(),
                shouldScheduleNextCheck = false,
                alertedControllerIds = emptySet()
            )
        }

        val activeAlertedIds = alertedControllerIds.toMutableSet()
        val alerts = mutableListOf<ProtectBatteryAlert>()
        val connectedIds = batteries.map { it.controllerId }.toSet()
        activeAlertedIds.retainAll(connectedIds)

        batteries.forEach { battery ->
            val shouldTrackAsAlerted = battery.isChargingForProtectBattery() &&
                    battery.percent != null &&
                    battery.percent >= thresholdPercent

            if (!shouldTrackAsAlerted) {
                activeAlertedIds.remove(battery.controllerId)
                return@forEach
            }

            if (!activeAlertedIds.contains(battery.controllerId)) {
                alerts += ProtectBatteryAlert(
                    controllerId = battery.controllerId,
                    controllerName = battery.controllerName,
                    percent = battery.percent
                )
            }
            activeAlertedIds += battery.controllerId
        }

        return ProtectBatteryDecision(
            alerts = alerts,
            shouldScheduleNextCheck = true,
            alertedControllerIds = activeAlertedIds
        )
    }

    private fun ControllerBatterySnapshot.isChargingForProtectBattery(): Boolean {
        return status == BatteryChargeStatus.CHARGING ||
                status == BatteryChargeStatus.FULL
    }

    private fun toControllerBatterySnapshot(controller: ControllerInfo): ControllerBatterySnapshot {
        return ControllerBatterySnapshot(
            controllerId = buildControllerId(controller),
            controllerName = controller.name,
            percent = controller.batteryPercent,
            status = controller.batteryStatus ?: BatteryChargeStatus.UNKNOWN
        )
    }

    private fun buildControllerId(controller: ControllerInfo): String {
        val descriptor = controller.descriptor?.trim().orEmpty()
        if (descriptor.isNotEmpty()) {
            return descriptor
        }
        return "VID:${controller.vendorId}_PID:${controller.productId}_DID:${controller.deviceId}"
    }

    companion object {
        const val DEFAULT_THRESHOLD_PERCENT = 80
    }
}

data class ControllerBatterySnapshot(
    val controllerId: String,
    val controllerName: String,
    val percent: Int?,
    val status: BatteryChargeStatus
)

data class ProtectBatteryAlert(
    val controllerId: String,
    val controllerName: String,
    val percent: Int
)

data class ProtectBatteryDecision(
    val alerts: List<ProtectBatteryAlert>,
    val shouldScheduleNextCheck: Boolean,
    val alertedControllerIds: Set<String>
)

interface ProtectBatteryUiPort {
    fun hasNotificationPermission(): Boolean
    fun hasPendingNotificationPermission(): Boolean
    fun setPendingNotificationPermission(pending: Boolean)
    fun setSelectedEnabled(enabled: Boolean)
    fun defaultControllerName(): String
    fun requestNotificationPermission()
    fun render()
    fun showNotificationPermissionRequired()
    fun showEnabled()
    fun showDisabled()
}
