package com.android.synclab.glimpse.infra.protectbattery

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.android.synclab.glimpse.base.contracts.ProtectBatteryCheckScheduler

class AndroidProtectBatteryCheckScheduler(
    context: Context,
    private val checkReceiverClass: Class<out BroadcastReceiver>,
    private val checkAction: String
) : ProtectBatteryCheckScheduler {
    private val appContext = context.applicationContext

    override fun scheduleNextCheck(delayMs: Long) {
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val triggerAtMs = SystemClock.elapsedRealtime() + delayMs
        alarmManager.set(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            triggerAtMs,
            pendingIntent()
        )
    }

    override fun cancelNextCheck() {
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(pendingIntent())
    }

    private fun pendingIntent(): PendingIntent {
        return PendingIntent.getBroadcast(
            appContext,
            CHECK_REQUEST_CODE,
            Intent(appContext, checkReceiverClass).apply {
                action = checkAction
            },
            pendingIntentFlags()
        )
    }

    private fun pendingIntentFlags(): Int {
        val baseFlags = PendingIntent.FLAG_UPDATE_CURRENT
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            baseFlags or PendingIntent.FLAG_IMMUTABLE
        } else {
            baseFlags
        }
    }

    companion object {
        private const val CHECK_REQUEST_CODE = 32011
    }
}
