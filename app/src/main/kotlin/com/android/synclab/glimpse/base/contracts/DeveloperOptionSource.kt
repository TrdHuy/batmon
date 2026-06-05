package com.android.synclab.glimpse.base.contracts

interface DeveloperOptionSource {
    val isDebuggableApp: Boolean

    fun isMockControllerPagesEnabled(): Boolean

    fun setMockControllerPagesEnabled(enabled: Boolean)

    fun isProtectBatteryToolsEnabled(): Boolean

    fun setProtectBatteryToolsEnabled(enabled: Boolean)
}
