package com.android.synclab.glimpse.presentation.model

data class PendingBackgroundMonitoringStart(
    val profileId: String?,
    val controllerIdentifier: String?,
    val reason: String,
    val persistOnSuccess: Boolean
)
