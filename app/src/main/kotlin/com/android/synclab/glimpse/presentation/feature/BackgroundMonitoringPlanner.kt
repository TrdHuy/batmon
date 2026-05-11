package com.android.synclab.glimpse.presentation.feature

import com.android.synclab.glimpse.presentation.model.PendingBackgroundMonitoringStart

class BackgroundMonitoringPlanner {
    enum class Permission {
        POST_NOTIFICATIONS,
        BLUETOOTH_CONNECT
    }

    enum class RejectReason {
        MISSING_CONTROLLER_IDENTIFIER,
        NO_PENDING_START,
        PENDING_CONTROLLER_CHANGED
    }

    data class State(
        val profileId: String?,
        val controllerIdentifier: String?,
        val isMonitoringEnabled: Boolean,
        val hasNotificationPermission: Boolean,
        val hasBluetoothConnectPermission: Boolean
    )

    sealed interface Decision {
        data class Start(
            val pendingStart: PendingBackgroundMonitoringStart
        ) : Decision

        data class Stop(
            val shouldDispatchStop: Boolean,
            val persistProfileId: String?,
            val selectedEnabled: Boolean = false,
            val clearPending: Boolean = true
        ) : Decision

        data class RequestPermission(
            val permission: Permission,
            val pendingStart: PendingBackgroundMonitoringStart,
            val selectedEnabled: Boolean?
        ) : Decision

        data class Reject(
            val reason: RejectReason,
            val selectedEnabled: Boolean?
        ) : Decision
    }

    data class StartDispatchResult(
        val clearPending: Boolean,
        val selectedEnabled: Boolean?,
        val persistProfileId: String?,
        val persistEnabled: Boolean?
    )

    fun planUserToggle(
        enabled: Boolean,
        state: State,
        reason: String
    ): Decision {
        return if (enabled) {
            planStart(
                state = state,
                reason = reason,
                persistOnSuccess = true
            )
        } else {
            Decision.Stop(
                shouldDispatchStop = state.isMonitoringEnabled,
                persistProfileId = state.profileId.normalized()
            )
        }
    }

    fun planProfilePreference(
        enabled: Boolean,
        state: State,
        reason: String
    ): Decision {
        return if (enabled) {
            planStart(
                state = state,
                reason = reason,
                persistOnSuccess = false
            )
        } else {
            Decision.Stop(
                shouldDispatchStop = state.isMonitoringEnabled,
                persistProfileId = null
            )
        }
    }

    fun planResumePending(
        pendingStart: PendingBackgroundMonitoringStart?,
        currentState: State
    ): Decision {
        val pending = pendingStart
            ?: return Decision.Reject(
                reason = RejectReason.NO_PENDING_START,
                selectedEnabled = null
            )
        val pendingProfileId = pending.profileId.normalized()
        val pendingControllerIdentifier = pending.controllerIdentifier.normalized()
        if (pendingProfileId == null || pendingControllerIdentifier == null) {
            return Decision.Reject(
                reason = RejectReason.MISSING_CONTROLLER_IDENTIFIER,
                selectedEnabled = if (pending.persistOnSuccess) false else null
            )
        }
        if (
            currentState.profileId.normalized() != pendingProfileId ||
            currentState.controllerIdentifier.normalized() != pendingControllerIdentifier
        ) {
            return Decision.Reject(
                reason = RejectReason.PENDING_CONTROLLER_CHANGED,
                selectedEnabled = null
            )
        }
        return when {
            !currentState.hasNotificationPermission -> Decision.RequestPermission(
                permission = Permission.POST_NOTIFICATIONS,
                pendingStart = pending,
                selectedEnabled = if (pending.persistOnSuccess) false else null
            )

            !currentState.hasBluetoothConnectPermission -> Decision.RequestPermission(
                permission = Permission.BLUETOOTH_CONNECT,
                pendingStart = pending,
                selectedEnabled = if (pending.persistOnSuccess) false else null
            )

            else -> Decision.Start(pending)
        }
    }

    fun planStartDispatchResult(
        pendingStart: PendingBackgroundMonitoringStart,
        dispatched: Boolean
    ): StartDispatchResult {
        if (!dispatched) {
            return StartDispatchResult(
                clearPending = true,
                selectedEnabled = if (pendingStart.persistOnSuccess) false else null,
                persistProfileId = null,
                persistEnabled = null
            )
        }

        return StartDispatchResult(
            clearPending = true,
            selectedEnabled = true,
            persistProfileId = pendingStart.profileId
                .normalized()
                ?.takeIf { pendingStart.persistOnSuccess },
            persistEnabled = true.takeIf { pendingStart.persistOnSuccess }
        )
    }

    private fun planStart(
        state: State,
        reason: String,
        persistOnSuccess: Boolean
    ): Decision {
        val profileId = state.profileId.normalized()
        val controllerIdentifier = state.controllerIdentifier.normalized()
        if (profileId == null || controllerIdentifier == null) {
            return Decision.Reject(
                reason = RejectReason.MISSING_CONTROLLER_IDENTIFIER,
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
            !state.hasNotificationPermission -> Decision.RequestPermission(
                permission = Permission.POST_NOTIFICATIONS,
                pendingStart = pendingStart,
                selectedEnabled = if (persistOnSuccess) false else null
            )

            !state.hasBluetoothConnectPermission -> Decision.RequestPermission(
                permission = Permission.BLUETOOTH_CONNECT,
                pendingStart = pendingStart,
                selectedEnabled = if (persistOnSuccess) false else null
            )

            else -> Decision.Start(pendingStart)
        }
    }

    private fun String?.normalized(): String? {
        return this?.trim()?.takeIf { it.isNotEmpty() }
    }
}
