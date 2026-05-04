package com.android.synclab.glimpse.presentation.model

import com.android.synclab.glimpse.data.model.BatteryChargeStatus

data class ControllerPageUiModel(
    val uniqueId: String,
    val persistentId: String,
    val descriptor: String?,
    val deviceId: Int?,
    val name: String,
    val vendorId: Int?,
    val productId: Int?,
    val batteryPercent: Int?,
    val batteryStatus: BatteryChargeStatus,
    val isSelected: Boolean = false,
    val isMock: Boolean = false,
    val isPlaceholder: Boolean = false
)
