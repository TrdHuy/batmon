package com.android.synclab.glimpse.infra.protectbattery

import android.content.Context
import com.android.synclab.glimpse.base.contracts.ProtectBatteryPreferenceStore
import com.android.synclab.glimpse.infra.preferences.SharedPreferenceStore

class AndroidProtectBatteryPreferenceStore(
    context: Context
) : ProtectBatteryPreferenceStore {
    private val appContext = context.applicationContext

    override fun getBoolean(
        prefsName: String,
        key: String,
        defaultValue: Boolean
    ): Boolean {
        return SharedPreferenceStore.getBoolean(
            context = appContext,
            prefsName = prefsName,
            key = key,
            defaultValue = defaultValue
        )
    }

    override fun putBoolean(
        prefsName: String,
        key: String,
        value: Boolean
    ) {
        SharedPreferenceStore.putBoolean(
            context = appContext,
            prefsName = prefsName,
            key = key,
            value = value
        )
    }

    override fun getStringSet(
        prefsName: String,
        key: String,
        defaultValue: Set<String>
    ): Set<String> {
        return SharedPreferenceStore.getStringSet(
            context = appContext,
            prefsName = prefsName,
            key = key,
            defaultValue = defaultValue
        )
    }

    override fun putStringSet(
        prefsName: String,
        key: String,
        value: Set<String>
    ) {
        SharedPreferenceStore.putStringSet(
            context = appContext,
            prefsName = prefsName,
            key = key,
            value = value
        )
    }
}
