package com.android.synclab.glimpse.base.contracts

interface MonitoringStateProvider {
    val isServiceRunning: Boolean
    val isMonitoringEnabled: Boolean
    val isOverlayVisible: Boolean
    val lastStatusText: String
}
