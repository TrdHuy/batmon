package com.android.synclab.glimpse.presentation.model

data class EventChangeParam(
    val state: MainUiState,
    val eventType: EventType,
    val source: Source = Source.SYSTEM,
    val note: String? = null,
    val timestampMs: Long = System.currentTimeMillis()
) {
    enum class EventType {
        VIEW_ATTACHED,
        CONTROLLER_INFO_UPDATED,
        SERVICE_STATE_SYNCED
    }

    enum class Source {
        VIEW,
        SYSTEM
    }
}
