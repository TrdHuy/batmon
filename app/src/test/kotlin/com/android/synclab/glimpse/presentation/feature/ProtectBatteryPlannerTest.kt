package com.android.synclab.glimpse.presentation.feature

import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectBatteryPlannerTest {
    private val planner = ProtectBatteryPlanner()

    @Test
    fun planToggle_enableWithNotificationPermissionEnables() {
        val decision = planner.planToggle(
            enabled = true,
            hasNotificationPermission = true
        )

        assertSame(ProtectBatteryToggleDecision.Enable, decision)
    }

    @Test
    fun planToggle_enableWithoutNotificationPermissionRequestsPermission() {
        val decision = planner.planToggle(
            enabled = true,
            hasNotificationPermission = false
        )

        assertSame(ProtectBatteryToggleDecision.RequestNotificationPermission, decision)
    }

    @Test
    fun planToggle_disableDisables() {
        val decision = planner.planToggle(
            enabled = false,
            hasNotificationPermission = false
        )

        assertSame(ProtectBatteryToggleDecision.Disable, decision)
    }

    @Test
    fun planNotificationPermissionResult_grantedWithPendingEnables() {
        val decision = planner.planNotificationPermissionResult(
            granted = true,
            hasPendingEnable = true
        )

        assertSame(ProtectBatteryPermissionDecision.Enable, decision)
    }

    @Test
    fun planNotificationPermissionResult_deniedWithPendingDenies() {
        val decision = planner.planNotificationPermissionResult(
            granted = false,
            hasPendingEnable = true
        )

        assertSame(ProtectBatteryPermissionDecision.Deny, decision)
    }

    @Test
    fun planNotificationPermissionResult_withoutPendingDoesNothing() {
        val decision = planner.planNotificationPermissionResult(
            granted = true,
            hasPendingEnable = false
        )

        assertSame(ProtectBatteryPermissionDecision.None, decision)
    }

    @Test
    fun plan_disabledDoesNotNotifyOrSchedule() {
        val decision = planner.plan(
            enabled = false,
            battery = PhoneBatterySnapshot(percent = 90, isCharging = true),
            alertShownForChargeSession = true
        )

        assertFalse(decision.shouldNotify)
        assertFalse(decision.shouldScheduleNextCheck)
        assertFalse(decision.alertShownForChargeSession)
    }

    @Test
    fun plan_notChargingResetsAlertSession() {
        val decision = planner.plan(
            enabled = true,
            battery = PhoneBatterySnapshot(percent = 80, isCharging = false),
            alertShownForChargeSession = true
        )

        assertFalse(decision.shouldNotify)
        assertFalse(decision.shouldScheduleNextCheck)
        assertFalse(decision.alertShownForChargeSession)
    }

    @Test
    fun plan_chargingBelowThresholdSchedulesWithoutAlert() {
        val decision = planner.plan(
            enabled = true,
            battery = PhoneBatterySnapshot(percent = 79, isCharging = true),
            alertShownForChargeSession = false
        )

        assertFalse(decision.shouldNotify)
        assertTrue(decision.shouldScheduleNextCheck)
        assertFalse(decision.alertShownForChargeSession)
    }

    @Test
    fun plan_chargingAtThresholdNotifiesOnceAndKeepsScheduling() {
        val decision = planner.plan(
            enabled = true,
            battery = PhoneBatterySnapshot(percent = 80, isCharging = true),
            alertShownForChargeSession = false
        )

        assertTrue(decision.shouldNotify)
        assertTrue(decision.shouldScheduleNextCheck)
        assertTrue(decision.alertShownForChargeSession)
    }

    @Test
    fun plan_chargingAtThresholdAfterAlertDoesNotNotifyAgain() {
        val decision = planner.plan(
            enabled = true,
            battery = PhoneBatterySnapshot(percent = 95, isCharging = true),
            alertShownForChargeSession = true
        )

        assertFalse(decision.shouldNotify)
        assertTrue(decision.shouldScheduleNextCheck)
        assertTrue(decision.alertShownForChargeSession)
    }
}
