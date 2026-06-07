package com.android.synclab.glimpse.base.contracts

interface ProtectBatteryPreferenceStore {
    fun getBoolean(
        prefsName: String,
        key: String,
        defaultValue: Boolean
    ): Boolean

    fun putBoolean(
        prefsName: String,
        key: String,
        value: Boolean
    )

    fun getStringSet(
        prefsName: String,
        key: String,
        defaultValue: Set<String>
    ): Set<String>

    fun putStringSet(
        prefsName: String,
        key: String,
        value: Set<String>
    )
}
