package com.android.synclab.glimpse.data.state

import com.android.synclab.glimpse.base.contracts.MonitoringStateProvider

object MonitoringStateStore : MonitoringStateProvider {
    @Volatile
    override var isServiceRunning: Boolean = false

    @Volatile
    override var isMonitoringEnabled: Boolean = false

    @Volatile
    override var isOverlayVisible: Boolean = false

    @Volatile
    override var lastStatusText: String = ""
}
