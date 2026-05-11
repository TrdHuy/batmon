package com.android.synclab.glimpse

import com.android.synclab.glimpse.data.model.ControllerProfile
import org.junit.Assert.assertFalse
import org.junit.Test

class UnitTestFoundationTest {
    @Test
    fun controllerProfileDefaults_disableOptionalFeatures() {
        val profile = ControllerProfile(
            id = "controller-id",
            deviceName = "Wireless Controller",
            lightbarColor = 0x112233
        )

        assertFalse(profile.backgroundMonitoringEnabled)
        assertFalse(profile.liveBatteryOverlayEnabled)
    }
}
