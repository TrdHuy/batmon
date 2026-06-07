package com.android.synclab.glimpse.infra.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock

class AndroidAlarmScheduler(
    context: Context
) {
    private val appContext = context.applicationContext

    fun scheduleElapsedRealtimeWakeupBroadcast(
        receiverClass: Class<out BroadcastReceiver>,
        action: String,
        requestCode: Int,
        delayMs: Long
    ) {
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val triggerAtMs = SystemClock.elapsedRealtime() + delayMs
        alarmManager.set(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            triggerAtMs,
            pendingBroadcastIntent(
                receiverClass = receiverClass,
                action = action,
                requestCode = requestCode
            )
        )
    }

    fun cancelBroadcast(
        receiverClass: Class<out BroadcastReceiver>,
        action: String,
        requestCode: Int
    ) {
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(
            pendingBroadcastIntent(
                receiverClass = receiverClass,
                action = action,
                requestCode = requestCode
            )
        )
    }

    private fun pendingBroadcastIntent(
        receiverClass: Class<out BroadcastReceiver>,
        action: String,
        requestCode: Int
    ): PendingIntent {
        return PendingIntent.getBroadcast(
            appContext,
            requestCode,
            Intent(appContext, receiverClass).apply {
                this.action = action
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
}
