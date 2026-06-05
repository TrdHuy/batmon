package com.android.synclab.glimpse.infra.developer

import android.content.Context
import com.android.synclab.glimpse.base.contracts.DeveloperOptionSource

class AndroidDeveloperOptionSource(
    context: Context,
    override val isDebuggableApp: Boolean
) : DeveloperOptionSource {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun isMockControllerPagesEnabled(): Boolean {
        return getBoolean(KEY_MOCK_CONTROLLER_PAGES_ENABLED)
    }

    override fun setMockControllerPagesEnabled(enabled: Boolean) {
        putBoolean(KEY_MOCK_CONTROLLER_PAGES_ENABLED, enabled)
    }

    override fun isProtectBatteryToolsEnabled(): Boolean {
        return getBoolean(KEY_PROTECT_BATTERY_TOOLS_ENABLED)
    }

    override fun setProtectBatteryToolsEnabled(enabled: Boolean) {
        putBoolean(KEY_PROTECT_BATTERY_TOOLS_ENABLED, enabled)
    }

    private fun getBoolean(key: String): Boolean {
        if (!isDebuggableApp) {
            return false
        }
        return prefs.getBoolean(key, false)
    }

    private fun putBoolean(
        key: String,
        enabled: Boolean
    ) {
        if (!isDebuggableApp) {
            return
        }
        prefs.edit()
            .putBoolean(key, enabled)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "developer_options"
        private const val KEY_MOCK_CONTROLLER_PAGES_ENABLED =
            "mock_controller_pages_enabled"
        private const val KEY_PROTECT_BATTERY_TOOLS_ENABLED =
            "protect_battery_tools_enabled"
    }
}
