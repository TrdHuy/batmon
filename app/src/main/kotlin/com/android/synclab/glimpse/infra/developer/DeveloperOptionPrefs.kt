package com.android.synclab.glimpse.infra.developer

import android.content.Context
import android.content.Intent

object DeveloperOptionPrefs {
    const val ACTION_DEVELOPER_OPTIONS_CHANGED =
        "com.android.synclab.glimpse.debug.DEVELOPER_OPTIONS_CHANGED"

    private const val PREFS_NAME = "developer_options"
    private const val KEY_PROTECT_BATTERY_TOOLS_ENABLED =
        "protect_battery_tools_enabled"

    fun isProtectBatteryToolsEnabled(context: Context): Boolean {
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PROTECT_BATTERY_TOOLS_ENABLED, false)
    }

    fun setProtectBatteryToolsEnabled(
        context: Context,
        enabled: Boolean
    ) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PROTECT_BATTERY_TOOLS_ENABLED, enabled)
            .apply()
    }

    fun developerOptionsChangedIntent(context: Context): Intent {
        return Intent(ACTION_DEVELOPER_OPTIONS_CHANGED).apply {
            setPackage(context.packageName)
        }
    }
}
