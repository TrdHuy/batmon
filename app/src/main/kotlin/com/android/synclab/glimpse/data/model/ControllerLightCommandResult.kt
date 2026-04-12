package com.android.synclab.glimpse.data.model

enum class ControllerLightCommandStatus {
    SUCCESS,
    UNSUPPORTED_API,
    NO_CONTROLLER,
    NO_LIGHT,
    PERMISSION_DENIED,
    FAILED
}

data class ControllerLightCommandResult(
    val status: ControllerLightCommandStatus,
    val targetDeviceId: Int? = null,
    val targetLightId: Int? = null,
    val colorHex: String? = null,
    val detail: String? = null
)
