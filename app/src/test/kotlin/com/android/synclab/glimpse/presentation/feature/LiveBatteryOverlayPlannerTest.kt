package com.android.synclab.glimpse.presentation.feature

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveBatteryOverlayPlannerTest {
    private val planner = LiveBatteryOverlayPlanner()

    @Test
    fun planUserToggle_onWithPermissionAndController_showsAndPersists() {
        val decision = planner.planUserToggle(
            enabled = true,
            state = state()
        )

        val show = decision as LiveBatteryOverlayPlanner.Decision.Show
        assertEquals("controller-1", show.controllerIdentifier)
        assertEquals("profile-1", show.persistProfileId)
        assertTrue(show.selectedEnabled)
    }

    @Test
    fun planUserToggle_onWithoutOverlayPermission_requestsPermissionWithoutPersisting() {
        val decision = planner.planUserToggle(
            enabled = true,
            state = state(hasOverlayPermission = false)
        )

        val request = decision as LiveBatteryOverlayPlanner.Decision.RequestOverlayPermission
        assertEquals(false, request.selectedEnabled)
    }

    @Test
    fun planUserToggle_onWithoutController_rejectsAndRollsBackSelection() {
        val decision = planner.planUserToggle(
            enabled = true,
            state = state(controllerIdentifier = null)
        )

        val reject = decision as LiveBatteryOverlayPlanner.Decision.Reject
        assertEquals(
            LiveBatteryOverlayPlanner.RejectReason.MISSING_CONTROLLER_IDENTIFIER,
            reject.reason
        )
        assertEquals(false, reject.selectedEnabled)
    }

    @Test
    fun planProfilePreference_onShowsRuntimeWithoutPersisting() {
        val decision = planner.planProfilePreference(
            enabled = true,
            state = state()
        )

        val show = decision as LiveBatteryOverlayPlanner.Decision.Show
        assertEquals("controller-1", show.controllerIdentifier)
        assertNull(show.persistProfileId)
        assertTrue(show.selectedEnabled)
    }

    @Test
    fun planProfilePreference_offHidesRuntimeWithoutPersisting() {
        val decision = planner.planProfilePreference(
            enabled = false,
            state = state(isServiceRunning = true, isOverlayVisible = true)
        )

        val hide = decision as LiveBatteryOverlayPlanner.Decision.Hide
        assertTrue(hide.shouldDispatchHide)
        assertNull(hide.persistProfileId)
        assertFalse(hide.selectedEnabled)
    }

    @Test
    fun planUserToggle_offWhileIdleSkipsHideButPersistsFalse() {
        val decision = planner.planUserToggle(
            enabled = false,
            state = state(isServiceRunning = false, isOverlayVisible = false)
        )

        val hide = decision as LiveBatteryOverlayPlanner.Decision.Hide
        assertFalse(hide.shouldDispatchHide)
        assertEquals("profile-1", hide.persistProfileId)
    }

    @Test
    fun planUserToggle_offWhileOverlayActiveHidesAndPersistsFalse() {
        val decision = planner.planUserToggle(
            enabled = false,
            state = state(isServiceRunning = true, isOverlayVisible = true)
        )

        val hide = decision as LiveBatteryOverlayPlanner.Decision.Hide
        assertTrue(hide.shouldDispatchHide)
        assertEquals("profile-1", hide.persistProfileId)
    }

    private fun state(
        profileId: String? = "profile-1",
        controllerIdentifier: String? = "controller-1",
        hasOverlayPermission: Boolean = true,
        isServiceRunning: Boolean = false,
        isOverlayVisible: Boolean = false
    ): LiveBatteryOverlayPlanner.State {
        return LiveBatteryOverlayPlanner.State(
            profileId = profileId,
            controllerIdentifier = controllerIdentifier,
            hasOverlayPermission = hasOverlayPermission,
            isServiceRunning = isServiceRunning,
            isOverlayVisible = isOverlayVisible
        )
    }
}
