package com.android.synclab.glimpse.presentation.feature

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
        onReceiverEvent(ProtectBatteryReceiverEvent.Check, port)
    }

    fun onManualDisable(port: ProtectBatteryRuntimePort) {
        port.setEnabled(false)
        port.setAlertShownForChargeSession(false)
        port.cancelNextCheck()
    }

    fun onReceiverEvent(
        event: ProtectBatteryReceiverEvent,
        port: ProtectBatteryRuntimePort
    ) {
        when (event) {
            ProtectBatteryReceiverEvent.PowerDisconnected -> {
                port.setAlertShownForChargeSession(false)
                port.cancelNextCheck()
            }

            ProtectBatteryReceiverEvent.Check -> runBatteryCheck(port)
        }
    }

    private fun runBatteryCheck(port: ProtectBatteryRuntimePort) {
        val enabled = port.isEnabled()
        if (!enabled) {
            port.cancelNextCheck()
            return
        }

        val battery = port.readPhoneBattery()
        val decision = planBatteryCheck(
            enabled = true,
            battery = battery,
            alertShownForChargeSession = port.isAlertShownForChargeSession()
        )
        port.setAlertShownForChargeSession(decision.alertShownForChargeSession)
        if (decision.shouldNotify && battery != null) {
            port.postThresholdAlert(battery.percent)
        }
        if (decision.shouldScheduleNextCheck) {
            port.scheduleNextCheck()
        } else {
            port.cancelNextCheck()
        }
        port.logCheck(enabled, battery, decision)
    }

    fun planBatteryCheck(
        enabled: Boolean,
        battery: PhoneBatterySnapshot?,
        alertShownForChargeSession: Boolean
    ): ProtectBatteryDecision {
        if (!enabled) {
            return ProtectBatteryDecision(
                shouldNotify = false,
                shouldScheduleNextCheck = false,
                alertShownForChargeSession = false
            )
        }

        if (battery == null || !battery.isCharging) {
            return ProtectBatteryDecision(
                shouldNotify = false,
                shouldScheduleNextCheck = false,
                alertShownForChargeSession = false
            )
        }

        val reachedThreshold = battery.percent >= thresholdPercent
        return ProtectBatteryDecision(
            shouldNotify = reachedThreshold && !alertShownForChargeSession,
            shouldScheduleNextCheck = true,
            alertShownForChargeSession = reachedThreshold
        )
    }

    companion object {
        const val DEFAULT_THRESHOLD_PERCENT = 80
    }
}

data class PhoneBatterySnapshot(
    val percent: Int,
    val isCharging: Boolean
)

data class ProtectBatteryDecision(
    val shouldNotify: Boolean,
    val shouldScheduleNextCheck: Boolean,
    val alertShownForChargeSession: Boolean
)

enum class ProtectBatteryReceiverEvent {
    Check,
    PowerDisconnected
}

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
    fun isAlertShownForChargeSession(): Boolean
    fun setAlertShownForChargeSession(shown: Boolean)
    fun readPhoneBattery(): PhoneBatterySnapshot?
    fun scheduleNextCheck()
    fun cancelNextCheck()
    fun postThresholdAlert(percent: Int)
    fun logCheck(
        enabled: Boolean,
        battery: PhoneBatterySnapshot?,
        decision: ProtectBatteryDecision
    )
}
