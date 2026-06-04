package com.android.synclab.glimpse.infra.preferences

import android.content.Context

object SharedPreferenceStore {
    fun getBoolean(
        context: Context,
        prefsName: String,
        key: String,
        defaultValue: Boolean
    ): Boolean {
        return prefs(context, prefsName).getBoolean(key, defaultValue)
    }

    fun putBoolean(
        context: Context,
        prefsName: String,
        key: String,
        value: Boolean
    ) {
        prefs(context, prefsName)
            .edit()
            .putBoolean(key, value)
            .apply()
    }

    fun getStringSet(
        context: Context,
        prefsName: String,
        key: String,
        defaultValue: Set<String>
    ): Set<String> {
        return prefs(context, prefsName).getStringSet(key, defaultValue)?.toSet()
            ?: defaultValue
    }

    fun putStringSet(
        context: Context,
        prefsName: String,
        key: String,
        value: Set<String>
    ) {
        prefs(context, prefsName)
            .edit()
            .putStringSet(key, value)
            .apply()
    }

    private fun prefs(context: Context, prefsName: String) =
        context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
}
