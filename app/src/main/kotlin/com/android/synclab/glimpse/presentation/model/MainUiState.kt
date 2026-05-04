package com.android.synclab.glimpse.presentation.model

import com.android.synclab.glimpse.data.model.BatteryChargeStatus

data class MainUiState(
    val connectionState: ConnectionState = ConnectionState.LOADING,
    val batteryPercent: Int? = null,
    val batteryStatus: BatteryChargeStatus = BatteryChargeStatus.UNKNOWN,
    val controllerUniqueId: String? = null,
    val controllerPersistentId: String? = null,
    val controllerDescriptor: String? = null,
    val controllerName: String? = null,
    val controllerPages: List<ControllerPageUiModel> = emptyList(),
    val selectedControllerUniqueId: String? = null,
    val isServiceRunning: Boolean = false,
    val isMonitoringEnabled: Boolean = false,
    val isOverlayVisible: Boolean = false
) {
    enum class ConnectionState {
        LOADING,
        INPUT_MANAGER_UNAVAILABLE,
        DISCONNECTED,
        CONNECTED
    }
}
