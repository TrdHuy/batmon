package com.android.synclab.glimpse.domain.model

data class ControllerInfo(
    val deviceId: Int,
    val name: String,
    val vendorId: Int,
    val productId: Int,
    val batteryPercent: Int?,
    val batteryStatus: BatteryChargeStatus?
)
