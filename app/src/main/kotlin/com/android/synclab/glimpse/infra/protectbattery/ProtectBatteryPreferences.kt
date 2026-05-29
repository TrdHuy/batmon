package com.android.synclab.glimpse.infra.protectbattery

import android.content.Context

object ProtectBatteryPreferences {
    private const val PREFS_NAME = "protect_battery"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_ALERT_SHOWN_FOR_CHARGE_SESSION = "alert_shown_for_charge_session"

    fun isEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_ENABLED, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    fun isAlertShownForChargeSession(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_ALERT_SHOWN_FOR_CHARGE_SESSION, false)
    }

    fun setAlertShownForChargeSession(context: Context, shown: Boolean) {
        prefs(context)
            .edit()
            .putBoolean(KEY_ALERT_SHOWN_FOR_CHARGE_SESSION, shown)
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
