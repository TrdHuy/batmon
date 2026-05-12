package com.android.synclab.glimpse.presentation.feature

import com.android.synclab.glimpse.presentation.model.BackgroundMonitoringDecision
import com.android.synclab.glimpse.presentation.model.BackgroundMonitoringPermission
import com.android.synclab.glimpse.presentation.model.BackgroundMonitoringRejectReason
import com.android.synclab.glimpse.presentation.model.BackgroundMonitoringStartDispatchResult
import com.android.synclab.glimpse.presentation.model.BackgroundMonitoringState
import com.android.synclab.glimpse.presentation.model.PendingBackgroundMonitoringStart

class BackgroundMonitoringPlanner :
    ControllerToggleFeaturePlanner<BackgroundMonitoringState, BackgroundMonitoringDecision>() {
    override fun planUserToggle(
        enabled: Boolean,
        state: BackgroundMonitoringState,
        reason: String
    ): BackgroundMonitoringDecision {
        return if (enabled) {
            planStart(
                state = state,
                reason = reason,
                persistOnSuccess = true
            )
        } else {
            BackgroundMonitoringDecision.Stop(
                shouldDispatchStop = state.isMonitoringEnabled,
                persistProfileId = state.profileId.normalizedIdentifier()
            )
        }
    }

    override fun planProfilePreference(
        enabled: Boolean,
        state: BackgroundMonitoringState,
        reason: String
    ): BackgroundMonitoringDecision {
        return if (enabled) {
            planStart(
                state = state,
                reason = reason,
                persistOnSuccess = false
            )
        } else {
            BackgroundMonitoringDecision.Stop(
                shouldDispatchStop = state.isMonitoringEnabled,
                persistProfileId = null
            )
        }
    }

    fun planResumePending(
        pendingStart: PendingBackgroundMonitoringStart?,
        currentState: BackgroundMonitoringState
    ): BackgroundMonitoringDecision {
        val pending = pendingStart
            ?: return BackgroundMonitoringDecision.Reject(
                reason = BackgroundMonitoringRejectReason.NO_PENDING_START,
                selectedEnabled = null
            )
        val pendingProfileId = pending.profileId.normalizedIdentifier()
        val pendingControllerIdentifier = pending.controllerIdentifier.normalizedIdentifier()
        if (pendingProfileId == null || pendingControllerIdentifier == null) {
            return BackgroundMonitoringDecision.Reject(
                reason = BackgroundMonitoringRejectReason.MISSING_CONTROLLER_IDENTIFIER,
                selectedEnabled = if (pending.persistOnSuccess) false else null
            )
        }
        if (
            currentState.profileId.normalizedIdentifier() != pendingProfileId ||
            currentState.controllerIdentifier.normalizedIdentifier() != pendingControllerIdentifier
        ) {
            return BackgroundMonitoringDecision.Reject(
                reason = BackgroundMonitoringRejectReason.PENDING_CONTROLLER_CHANGED,
                selectedEnabled = null
            )
        }
        return when {
            !currentState.hasNotificationPermission -> BackgroundMonitoringDecision.RequestPermission(
                permission = BackgroundMonitoringPermission.POST_NOTIFICATIONS,
                pendingStart = pending,
                selectedEnabled = if (pending.persistOnSuccess) false else null
            )

            !currentState.hasBluetoothConnectPermission -> BackgroundMonitoringDecision.RequestPermission(
                permission = BackgroundMonitoringPermission.BLUETOOTH_CONNECT,
                pendingStart = pending,
                selectedEnabled = if (pending.persistOnSuccess) false else null
            )

            else -> BackgroundMonitoringDecision.Start(pending)
        }
    }

    fun planStartDispatchResult(
        pendingStart: PendingBackgroundMonitoringStart,
        dispatched: Boolean
    ): BackgroundMonitoringStartDispatchResult {
        if (!dispatched) {
            return BackgroundMonitoringStartDispatchResult(
                clearPending = true,
                selectedEnabled = if (pendingStart.persistOnSuccess) false else null,
                persistProfileId = null,
                persistEnabled = null
            )
        }

        return BackgroundMonitoringStartDispatchResult(
            clearPending = true,
            selectedEnabled = true,
            persistProfileId = pendingStart.profileId
                .normalizedIdentifier()
                ?.takeIf { pendingStart.persistOnSuccess },
            persistEnabled = true.takeIf { pendingStart.persistOnSuccess }
        )
    }

    private fun planStart(
        state: BackgroundMonitoringState,
        reason: String,
        persistOnSuccess: Boolean
    ): BackgroundMonitoringDecision {
        val profileId = state.profileId.normalizedIdentifier()
        val controllerIdentifier = state.controllerIdentifier.normalizedIdentifier()
        if (profileId == null || controllerIdentifier == null) {
            return BackgroundMonitoringDecision.Reject(
                reason = BackgroundMonitoringRejectReason.MISSING_CONTROLLER_IDENTIFIER,
                selectedEnabled = if (persistOnSuccess) false else null
            )
        }

        val pendingStart = PendingBackgroundMonitoringStart(
            profileId = profileId,
            controllerIdentifier = controllerIdentifier,
            reason = reason,
            persistOnSuccess = persistOnSuccess
        )
        return when {
            !state.hasNotificationPermission -> BackgroundMonitoringDecision.RequestPermission(
                permission = BackgroundMonitoringPermission.POST_NOTIFICATIONS,
                pendingStart = pendingStart,
                selectedEnabled = if (persistOnSuccess) false else null
            )

            !state.hasBluetoothConnectPermission -> BackgroundMonitoringDecision.RequestPermission(
                permission = BackgroundMonitoringPermission.BLUETOOTH_CONNECT,
                pendingStart = pendingStart,
                selectedEnabled = if (persistOnSuccess) false else null
            )

            else -> BackgroundMonitoringDecision.Start(pendingStart)
        }
    }
}
