package com.android.synclab.glimpse.data.model

data class ControllerProfile(
    val id: String,
    val deviceName: String,
    val lightbarColor: Int,
    val liveBatteryOverlayEnabled: Boolean = false,
    val backgroundMonitoringEnabled: Boolean = false
)
