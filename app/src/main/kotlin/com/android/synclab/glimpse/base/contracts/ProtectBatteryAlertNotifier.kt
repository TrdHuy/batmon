package com.android.synclab.glimpse.base.contracts

interface ProtectBatteryAlertNotifier {
    fun postThresholdAlert(
        controllerId: String,
        controllerName: String,
        percent: Int
    )

    fun postDevControllerThresholdAlert(percent: Int)
}
