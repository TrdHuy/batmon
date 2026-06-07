package com.android.synclab.glimpse.domain.manager

import com.android.synclab.glimpse.base.contracts.DeveloperOptionSource
import com.android.synclab.glimpse.utils.LogCompat

class DeveloperOptionManager(
    private val source: DeveloperOptionSource
) {
    private val developerModeEnabled: Boolean by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        source.isDebuggableApp
    }

    fun isMockControllerPagesEnabled(): Boolean {
        val enabled = developerModeEnabled && source.isMockControllerPagesEnabled()
        LogCompat.dDebug {
            "UI_VERIFY DevOptions mock read " +
                    "enabled=$enabled thread=${Thread.currentThread().name}"
        }
        return enabled
    }

    fun setMockControllerPagesEnabled(enabled: Boolean) {
        if (!developerModeEnabled) {
            return
        }
        source.setMockControllerPagesEnabled(enabled)
        LogCompat.dDebug {
            "UI_VERIFY DevOptions mock write " +
                    "enabled=$enabled thread=${Thread.currentThread().name}"
        }
    }

    fun isProtectBatteryFakeThresholdDetectionEnabled(): Boolean {
        val enabled = developerModeEnabled &&
                source.isProtectBatteryFakeThresholdDetectionEnabled()
        LogCompat.dDebug {
            "UI_VERIFY DevOptions protectBatteryFakeDetection read " +
                    "enabled=$enabled thread=${Thread.currentThread().name}"
        }
        return enabled
    }

    fun setProtectBatteryFakeThresholdDetectionEnabled(enabled: Boolean) {
        if (!developerModeEnabled) {
            return
        }
        source.setProtectBatteryFakeThresholdDetectionEnabled(enabled)
        LogCompat.dDebug {
            "UI_VERIFY DevOptions protectBatteryFakeDetection write " +
                    "enabled=$enabled thread=${Thread.currentThread().name}"
        }
    }
}
