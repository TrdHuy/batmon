package com.android.synclab.glimpse.presentation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.android.synclab.glimpse.R
import com.android.synclab.glimpse.di.AppContainer
import com.android.synclab.glimpse.utils.LogCompat

class ProtectBatteryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        LogCompat.d("ProtectBatteryReceiver action=${intent?.action}")
        AppContainer.from(context)
            .provideProtectBatteryPlanner()
            // Fallback notification name only when a controller does not report its own name.
            .onCheckRequested(context.getString(R.string.unknown_controller_name))
    }

    companion object {
        const val ACTION_CHECK =
            "com.android.synclab.glimpse.action.PROTECT_BATTERY_CHECK"
    }
}
