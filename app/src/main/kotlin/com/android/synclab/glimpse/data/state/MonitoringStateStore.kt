package com.android.synclab.glimpse.data.state

object MonitoringStateStore {
    @Volatile
    var isServiceRunning: Boolean = false

    @Volatile
    var isMonitoringEnabled: Boolean = false

    @Volatile
    var isOverlayVisible: Boolean = false

    @Volatile
    var lastStatusText: String = ""
}
