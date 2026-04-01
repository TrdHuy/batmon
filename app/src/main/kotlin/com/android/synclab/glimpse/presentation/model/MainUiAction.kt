package com.android.synclab.glimpse.presentation.model

sealed interface MainUiAction {
    data class RefreshControllerInfo(val unknownDeviceName: String) : MainUiAction
    object SyncServiceState : MainUiAction
}
