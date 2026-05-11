package com.android.synclab.glimpse.presentation.model

enum class LiveBatteryOverlayRejectReason {
    MISSING_CONTROLLER_IDENTIFIER
}

data class LiveBatteryOverlayState(
    val profileId: String?,
    val controllerIdentifier: String?,
    val hasOverlayPermission: Boolean,
    val isServiceRunning: Boolean,
    val isOverlayVisible: Boolean
)

sealed interface LiveBatteryOverlayDecision {
    data class Show(
        val controllerIdentifier: String,
        val persistProfileId: String?,
        val selectedEnabled: Boolean = true
    ) : LiveBatteryOverlayDecision

    data class Hide(
        val shouldDispatchHide: Boolean,
        val persistProfileId: String?,
        val selectedEnabled: Boolean = false
    ) : LiveBatteryOverlayDecision

    data class RequestOverlayPermission(
        val selectedEnabled: Boolean?
    ) : LiveBatteryOverlayDecision

    data class Reject(
        val reason: LiveBatteryOverlayRejectReason,
        val selectedEnabled: Boolean?
    ) : LiveBatteryOverlayDecision
}
