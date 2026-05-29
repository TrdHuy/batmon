package com.android.synclab.glimpse.presentation.feature

class ProtectBatteryPlanner(
    private val thresholdPercent: Int = DEFAULT_THRESHOLD_PERCENT
) {
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
