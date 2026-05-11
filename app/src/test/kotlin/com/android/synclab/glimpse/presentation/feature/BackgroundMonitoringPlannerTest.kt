package com.android.synclab.glimpse.presentation.feature

import com.android.synclab.glimpse.presentation.model.BackgroundMonitoringDecision
import com.android.synclab.glimpse.presentation.model.BackgroundMonitoringPermission
import com.android.synclab.glimpse.presentation.model.BackgroundMonitoringRejectReason
import com.android.synclab.glimpse.presentation.model.BackgroundMonitoringState
import com.android.synclab.glimpse.presentation.model.PendingBackgroundMonitoringStart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundMonitoringPlannerTest {
    private val planner = BackgroundMonitoringPlanner()

    @Test
    fun planUserToggle_onWithValidController_startsAndPersistsAfterSuccess() {
        val decision = planner.planUserToggle(
            enabled = true,
            state = state(),
            reason = "fixed_settings"
        )

        val start = decision as BackgroundMonitoringDecision.Start
        assertEquals("profile-1", start.pendingStart.profileId)
        assertEquals("controller-1", start.pendingStart.controllerIdentifier)
        assertTrue(start.pendingStart.persistOnSuccess)

        val result = planner.planStartDispatchResult(start.pendingStart, dispatched = true)
        assertTrue(result.clearPending)
        assertEquals(true, result.selectedEnabled)
        assertEquals("profile-1", result.persistProfileId)
        assertEquals(true, result.persistEnabled)
    }

    @Test
    fun planUserToggle_onWithMissingController_rejectsAndRollsBackSelection() {
        val decision = planner.planUserToggle(
            enabled = true,
            state = state(controllerIdentifier = null),
            reason = "fixed_settings"
        )

        val reject = decision as BackgroundMonitoringDecision.Reject
        assertEquals(
            BackgroundMonitoringRejectReason.MISSING_CONTROLLER_IDENTIFIER,
            reject.reason
        )
        assertEquals(false, reject.selectedEnabled)
    }

    @Test
    fun planUserToggle_onWithoutNotificationPermission_requestsPermissionWithoutPersisting() {
        val decision = planner.planUserToggle(
            enabled = true,
            state = state(hasNotificationPermission = false),
            reason = "fixed_settings"
        )

        val request = decision as BackgroundMonitoringDecision.RequestPermission
        assertEquals(BackgroundMonitoringPermission.POST_NOTIFICATIONS, request.permission)
        assertTrue(request.pendingStart.persistOnSuccess)
        assertEquals(false, request.selectedEnabled)
    }

    @Test
    fun planProfilePreference_onStartsRuntimeWithoutPersisting() {
        val decision = planner.planProfilePreference(
            enabled = true,
            state = state(),
            reason = "profile_loaded"
        )

        val start = decision as BackgroundMonitoringDecision.Start
        assertFalse(start.pendingStart.persistOnSuccess)

        val result = planner.planStartDispatchResult(start.pendingStart, dispatched = true)
        assertEquals(true, result.selectedEnabled)
        assertNull(result.persistProfileId)
        assertNull(result.persistEnabled)
    }

    @Test
    fun planUserToggle_offStopsRuntimeAndPersistsFalse() {
        val decision = planner.planUserToggle(
            enabled = false,
            state = state(isMonitoringEnabled = true),
            reason = "fixed_settings"
        )

        val stop = decision as BackgroundMonitoringDecision.Stop
        assertTrue(stop.shouldDispatchStop)
        assertEquals("profile-1", stop.persistProfileId)
        assertFalse(stop.selectedEnabled)
        assertTrue(stop.clearPending)
    }

    @Test
    fun planProfilePreference_offStopsRuntimeWithoutPersisting() {
        val decision = planner.planProfilePreference(
            enabled = false,
            state = state(isMonitoringEnabled = true),
            reason = "profile_loaded"
        )

        val stop = decision as BackgroundMonitoringDecision.Stop
        assertTrue(stop.shouldDispatchStop)
        assertNull(stop.persistProfileId)
    }

    @Test
    fun planResumePending_sameControllerStartsPendingRequest() {
        val pending = pendingStart()
        val decision = planner.planResumePending(
            pendingStart = pending,
            currentState = state()
        )

        val start = decision as BackgroundMonitoringDecision.Start
        assertEquals(pending, start.pendingStart)
    }

    @Test
    fun planResumePending_afterControllerChangedRejects() {
        val decision = planner.planResumePending(
            pendingStart = pendingStart(),
            currentState = state(controllerIdentifier = "controller-2")
        )

        val reject = decision as BackgroundMonitoringDecision.Reject
        assertEquals(
            BackgroundMonitoringRejectReason.PENDING_CONTROLLER_CHANGED,
            reject.reason
        )
        assertNull(reject.selectedEnabled)
    }

    @Test
    fun planStartDispatchResult_failedUserStartRollsBackWithoutPersisting() {
        val result = planner.planStartDispatchResult(
            pendingStart = pendingStart(persistOnSuccess = true),
            dispatched = false
        )

        assertTrue(result.clearPending)
        assertEquals(false, result.selectedEnabled)
        assertNull(result.persistProfileId)
        assertNull(result.persistEnabled)
    }

    private fun state(
        profileId: String? = "profile-1",
        controllerIdentifier: String? = "controller-1",
        isMonitoringEnabled: Boolean = false,
        hasNotificationPermission: Boolean = true,
        hasBluetoothConnectPermission: Boolean = true
    ): BackgroundMonitoringState {
        return BackgroundMonitoringState(
            profileId = profileId,
            controllerIdentifier = controllerIdentifier,
            isMonitoringEnabled = isMonitoringEnabled,
            hasNotificationPermission = hasNotificationPermission,
            hasBluetoothConnectPermission = hasBluetoothConnectPermission
        )
    }

    private fun pendingStart(
        persistOnSuccess: Boolean = true
    ): PendingBackgroundMonitoringStart {
        return PendingBackgroundMonitoringStart(
            profileId = "profile-1",
            controllerIdentifier = "controller-1",
            reason = "fixed_settings",
            persistOnSuccess = persistOnSuccess
        )
    }
}
