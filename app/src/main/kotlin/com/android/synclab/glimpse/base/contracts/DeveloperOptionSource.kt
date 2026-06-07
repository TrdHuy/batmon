package com.android.synclab.glimpse.base.contracts

interface DeveloperOptionSource {
    val isDebuggableApp: Boolean

    fun isMockControllerPagesEnabled(): Boolean

    fun setMockControllerPagesEnabled(enabled: Boolean)

    fun isProtectBatteryFakeThresholdDetectionEnabled(): Boolean

    fun setProtectBatteryFakeThresholdDetectionEnabled(enabled: Boolean)
}
