package com.android.synclab.glimpse.presentation.feature

import com.android.synclab.glimpse.presentation.model.LiveBatteryOverlayDecision
import com.android.synclab.glimpse.presentation.model.LiveBatteryOverlayRejectReason
import com.android.synclab.glimpse.presentation.model.LiveBatteryOverlayState

class LiveBatteryOverlayPlanner :
    ControllerToggleFeaturePlanner<LiveBatteryOverlayState, LiveBatteryOverlayDecision>() {
    override fun planUserToggle(
        enabled: Boolean,
        state: LiveBatteryOverlayState,
        reason: String
    ): LiveBatteryOverlayDecision {
        return if (enabled) {
            planShow(
                state = state,
                persistOnSuccess = true,
                rollbackSelectionOnFailure = true
            )
        } else {
            LiveBatteryOverlayDecision.Hide(
                shouldDispatchHide = state.isServiceRunning || state.isOverlayVisible,
                persistProfileId = state.profileId.normalizedIdentifier()
            )
        }
    }

    override fun planProfilePreference(
        enabled: Boolean,
        state: LiveBatteryOverlayState,
        reason: String
    ): LiveBatteryOverlayDecision {
        return if (enabled) {
            planShow(
                state = state,
                persistOnSuccess = false,
                rollbackSelectionOnFailure = false
            )
        } else {
            LiveBatteryOverlayDecision.Hide(
                shouldDispatchHide = state.isServiceRunning || state.isOverlayVisible,
                persistProfileId = null
            )
        }
    }

    private fun planShow(
        state: LiveBatteryOverlayState,
        persistOnSuccess: Boolean,
        rollbackSelectionOnFailure: Boolean
    ): LiveBatteryOverlayDecision {
        if (!state.hasOverlayPermission) {
            return LiveBatteryOverlayDecision.RequestOverlayPermission(
                selectedEnabled = false.takeIf { rollbackSelectionOnFailure }
            )
        }

        val controllerIdentifier = state.controllerIdentifier.normalizedIdentifier()
            ?: return LiveBatteryOverlayDecision.Reject(
                reason = LiveBatteryOverlayRejectReason.MISSING_CONTROLLER_IDENTIFIER,
                selectedEnabled = false.takeIf { rollbackSelectionOnFailure }
            )

        return LiveBatteryOverlayDecision.Show(
            controllerIdentifier = controllerIdentifier,
            persistProfileId = state.profileId.normalizedIdentifier().takeIf { persistOnSuccess },
            rollbackSelectionOnDispatchFailure = rollbackSelectionOnFailure
        )
    }
}
