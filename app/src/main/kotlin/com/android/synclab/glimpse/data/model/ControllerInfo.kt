package com.android.synclab.glimpse.data.model

data class ControllerInfo(
    val deviceId: Int,
    val name: String,
    val vendorId: Int,
    val productId: Int,
    val descriptor: String?,
    val batteryPercent: Int?,
    val batteryStatus: BatteryChargeStatus?
)
