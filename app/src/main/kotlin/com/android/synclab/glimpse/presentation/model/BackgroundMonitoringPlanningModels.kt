package com.android.synclab.glimpse.presentation.model

enum class BackgroundMonitoringPermission {
    POST_NOTIFICATIONS,
    BLUETOOTH_CONNECT
}

enum class BackgroundMonitoringRejectReason {
    MISSING_CONTROLLER_IDENTIFIER,
    NO_PENDING_START,
    PENDING_CONTROLLER_CHANGED
}

data class BackgroundMonitoringState(
    val profileId: String?,
    val controllerIdentifier: String?,
    val isMonitoringEnabled: Boolean,
    val hasNotificationPermission: Boolean,
    val hasBluetoothConnectPermission: Boolean
)

sealed interface BackgroundMonitoringDecision {
    data class Start(
        val pendingStart: PendingBackgroundMonitoringStart
    ) : BackgroundMonitoringDecision

    data class Stop(
        val shouldDispatchStop: Boolean,
        val persistProfileId: String?,
        val selectedEnabled: Boolean = false,
        val clearPending: Boolean = true
    ) : BackgroundMonitoringDecision

    data class RequestPermission(
        val permission: BackgroundMonitoringPermission,
        val pendingStart: PendingBackgroundMonitoringStart,
        val selectedEnabled: Boolean?
    ) : BackgroundMonitoringDecision

    data class Reject(
        val reason: BackgroundMonitoringRejectReason,
        val selectedEnabled: Boolean?
    ) : BackgroundMonitoringDecision
}

data class BackgroundMonitoringStartDispatchResult(
    val clearPending: Boolean,
    val selectedEnabled: Boolean?,
    val persistProfileId: String?,
    val persistEnabled: Boolean?
)
