package com.android.synclab.glimpse.presentation.feature

import com.android.synclab.glimpse.data.model.BatteryChargeStatus

class ProtectBatteryPlanner(
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
                port.disableRuntime()
                port.render()
                port.requestNotificationPermission()
                port.showNotificationPermissionRequired()
            }

            enabled -> {
                port.setPendingNotificationPermission(false)
                port.setSelectedEnabled(true)
                port.enableRuntime()
                port.showEnabled()
            }

            else -> {
                port.setPendingNotificationPermission(false)
                port.setSelectedEnabled(false)
                port.disableRuntime()
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
            port.enableRuntime()
            port.showEnabled()
            port.render()
        } else {
            port.setSelectedEnabled(port.isRuntimeEnabled())
            port.render()
        }
    }

    fun onManualEnable(port: ProtectBatteryRuntimePort) {
        port.setEnabled(true)
        onReceiverEvent(port)
    }

    fun onManualDisable(port: ProtectBatteryRuntimePort) {
        port.setEnabled(false)
        port.setAlertedControllerIds(emptySet())
        port.cancelNextCheck()
    }

    fun onReceiverEvent(port: ProtectBatteryRuntimePort) {
        val enabled = port.isEnabled()
        if (!enabled) {
            port.setAlertedControllerIds(emptySet())
            port.cancelNextCheck()
            return
        }

        val batteries = port.readControllerBatteries()
        val decision = planBatteryCheck(
            enabled = true,
            batteries = batteries,
            alertedControllerIds = port.getAlertedControllerIds()
        )
        port.setAlertedControllerIds(decision.alertedControllerIds)
        decision.alerts.forEach { alert ->
            port.postThresholdAlert(alert)
        }
        if (decision.shouldScheduleNextCheck) {
            port.scheduleNextCheck()
        } else {
            port.cancelNextCheck()
        }
        port.logCheck(enabled, batteries, decision)
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
    fun isRuntimeEnabled(): Boolean
    fun enableRuntime()
    fun disableRuntime()
    fun requestNotificationPermission()
    fun render()
    fun showNotificationPermissionRequired()
    fun showEnabled()
    fun showDisabled()
}

interface ProtectBatteryRuntimePort {
    fun isEnabled(): Boolean
    fun setEnabled(enabled: Boolean)
    fun getAlertedControllerIds(): Set<String>
    fun setAlertedControllerIds(controllerIds: Set<String>)
    fun readControllerBatteries(): List<ControllerBatterySnapshot>
    fun scheduleNextCheck()
    fun cancelNextCheck()
    fun postThresholdAlert(alert: ProtectBatteryAlert)
    fun logCheck(
        enabled: Boolean,
        batteries: List<ControllerBatterySnapshot>,
        decision: ProtectBatteryDecision
    )
}
