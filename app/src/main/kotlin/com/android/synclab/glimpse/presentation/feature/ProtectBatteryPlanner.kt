package com.android.synclab.glimpse.presentation.feature

class ProtectBatteryPlanner(
    private val thresholdPercent: Int = DEFAULT_THRESHOLD_PERCENT
) {
    fun planToggle(
        enabled: Boolean,
        hasNotificationPermission: Boolean
    ): ProtectBatteryToggleDecision {
        return when {
            enabled && !hasNotificationPermission -> ProtectBatteryToggleDecision.RequestNotificationPermission
            enabled -> ProtectBatteryToggleDecision.Enable
            else -> ProtectBatteryToggleDecision.Disable
        }
    }

    fun planNotificationPermissionResult(
        granted: Boolean,
        hasPendingEnable: Boolean
    ): ProtectBatteryPermissionDecision {
        return when {
            !hasPendingEnable -> ProtectBatteryPermissionDecision.None
            granted -> ProtectBatteryPermissionDecision.Enable
            else -> ProtectBatteryPermissionDecision.Deny
        }
    }

    fun plan(
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

sealed interface ProtectBatteryToggleDecision {
    data object Enable : ProtectBatteryToggleDecision
    data object Disable : ProtectBatteryToggleDecision
    data object RequestNotificationPermission : ProtectBatteryToggleDecision
}

sealed interface ProtectBatteryPermissionDecision {
    data object Enable : ProtectBatteryPermissionDecision
    data object Deny : ProtectBatteryPermissionDecision
    data object None : ProtectBatteryPermissionDecision
}
