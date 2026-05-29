package com.android.synclab.glimpse.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.android.synclab.glimpse.infra.developer.DeveloperOptionPrefs
import com.android.synclab.glimpse.utils.LogCompat

class DebugDeveloperOptionsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_SET_PROTECT_BATTERY_TOOLS) {
            return
        }
        if (!intent.hasExtra(EXTRA_ENABLED)) {
            LogCompat.w("DebugDeveloperOptionsReceiver missing extra=$EXTRA_ENABLED")
            return
        }

        val enabled = intent.getBooleanExtra(EXTRA_ENABLED, false)
        DeveloperOptionPrefs.setProtectBatteryToolsEnabled(
            context = context,
            enabled = enabled
        )
        context.sendBroadcast(DeveloperOptionPrefs.developerOptionsChangedIntent(context))
        LogCompat.i("Debug protect battery tools enabled=$enabled")
    }

    companion object {
        const val ACTION_SET_PROTECT_BATTERY_TOOLS =
            "com.android.synclab.glimpse.debug.SET_PROTECT_BATTERY_TOOLS"
        private const val EXTRA_ENABLED = "enabled"
    }
}
