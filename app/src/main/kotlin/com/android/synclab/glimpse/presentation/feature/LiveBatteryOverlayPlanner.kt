package com.android.synclab.glimpse.presentation.feature

class LiveBatteryOverlayPlanner {
    enum class RejectReason {
        MISSING_CONTROLLER_IDENTIFIER
    }

    data class State(
        val profileId: String?,
        val controllerIdentifier: String?,
        val hasOverlayPermission: Boolean,
        val isServiceRunning: Boolean,
        val isOverlayVisible: Boolean
    )

    sealed interface Decision {
        data class Show(
            val controllerIdentifier: String,
            val persistProfileId: String?,
            val selectedEnabled: Boolean = true
        ) : Decision

        data class Hide(
            val shouldDispatchHide: Boolean,
            val persistProfileId: String?,
            val selectedEnabled: Boolean = false
        ) : Decision

        data class RequestOverlayPermission(
            val selectedEnabled: Boolean?
        ) : Decision

        data class Reject(
            val reason: RejectReason,
            val selectedEnabled: Boolean?
        ) : Decision
    }

    fun planUserToggle(
        enabled: Boolean,
        state: State
    ): Decision {
        return if (enabled) {
            planShow(
                state = state,
                persistOnSuccess = true,
                rollbackSelectionOnFailure = true
            )
        } else {
            Decision.Hide(
                shouldDispatchHide = state.isServiceRunning || state.isOverlayVisible,
                persistProfileId = state.profileId.normalized()
            )
        }
    }

    fun planProfilePreference(
        enabled: Boolean,
        state: State
    ): Decision {
        return if (enabled) {
            planShow(
                state = state,
                persistOnSuccess = false,
                rollbackSelectionOnFailure = false
            )
        } else {
            Decision.Hide(
                shouldDispatchHide = state.isServiceRunning || state.isOverlayVisible,
                persistProfileId = null
            )
        }
    }

    private fun planShow(
        state: State,
        persistOnSuccess: Boolean,
        rollbackSelectionOnFailure: Boolean
    ): Decision {
        if (!state.hasOverlayPermission) {
            return Decision.RequestOverlayPermission(
                selectedEnabled = false.takeIf { rollbackSelectionOnFailure }
            )
        }

        val controllerIdentifier = state.controllerIdentifier.normalized()
            ?: return Decision.Reject(
                reason = RejectReason.MISSING_CONTROLLER_IDENTIFIER,
                selectedEnabled = false.takeIf { rollbackSelectionOnFailure }
            )

        return Decision.Show(
            controllerIdentifier = controllerIdentifier,
            persistProfileId = state.profileId.normalized().takeIf { persistOnSuccess }
        )
    }

    private fun String?.normalized(): String? {
        return this?.trim()?.takeIf { it.isNotEmpty() }
    }
}
