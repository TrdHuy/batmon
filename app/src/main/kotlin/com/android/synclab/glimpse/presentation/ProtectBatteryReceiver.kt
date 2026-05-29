package com.android.synclab.glimpse.presentation

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import com.android.synclab.glimpse.infra.protectbattery.ProtectBatteryNotifier
import com.android.synclab.glimpse.infra.protectbattery.ProtectBatteryPreferences
import com.android.synclab.glimpse.presentation.feature.PhoneBatterySnapshot
import com.android.synclab.glimpse.presentation.feature.ProtectBatteryPlanner
import com.android.synclab.glimpse.utils.LogCompat

class ProtectBatteryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        LogCompat.d("ProtectBatteryReceiver action=$action")

        if (action == Intent.ACTION_POWER_DISCONNECTED) {
            ProtectBatteryPreferences.setAlertShownForChargeSession(context, false)
            cancelNextCheck(context)
            return
        }

        runCheck(context)
    }

    companion object {
        const val ACTION_CHECK =
            "com.android.synclab.glimpse.action.PROTECT_BATTERY_CHECK"
        private const val CHECK_REQUEST_CODE = 32011
        private const val CHECK_INTERVAL_MS = 60_000L

        fun enable(context: Context) {
            ProtectBatteryPreferences.setEnabled(context, true)
            runCheck(context)
        }

        fun disable(context: Context) {
            ProtectBatteryPreferences.setEnabled(context, false)
            ProtectBatteryPreferences.setAlertShownForChargeSession(context, false)
            cancelNextCheck(context)
        }

        fun runCheck(context: Context) {
            val appContext = context.applicationContext
            val enabled = ProtectBatteryPreferences.isEnabled(appContext)
            if (!enabled) {
                cancelNextCheck(appContext)
                return
            }

            val snapshot = readPhoneBatterySnapshot(appContext)
            val alertShown = ProtectBatteryPreferences.isAlertShownForChargeSession(appContext)
            val decision = ProtectBatteryPlanner().plan(
                enabled = true,
                battery = snapshot,
                alertShownForChargeSession = alertShown
            )

            ProtectBatteryPreferences.setAlertShownForChargeSession(
                appContext,
                decision.alertShownForChargeSession
            )
            if (decision.shouldNotify && snapshot != null) {
                ProtectBatteryNotifier.showThresholdAlert(appContext, snapshot.percent)
            }
            if (decision.shouldScheduleNextCheck) {
                scheduleNextCheck(appContext)
            } else {
                cancelNextCheck(appContext)
            }

            LogCompat.d(
                "ProtectBattery check enabled=$enabled " +
                        "percent=${snapshot?.percent} charging=${snapshot?.isCharging} " +
                        "notify=${decision.shouldNotify} schedule=${decision.shouldScheduleNextCheck}"
            )
        }

        private fun readPhoneBatterySnapshot(context: Context): PhoneBatterySnapshot? {
            val batteryIntent = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            ) ?: return null
            val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level < 0 || scale <= 0) {
                return null
            }
            val percent = ((level * 100f) / scale).toInt().coerceIn(0, 100)
            val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val plugged = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL ||
                    plugged != 0
            return PhoneBatterySnapshot(
                percent = percent,
                isCharging = isCharging
            )
        }

        private fun scheduleNextCheck(context: Context) {
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            val triggerAtMs = SystemClock.elapsedRealtime() + CHECK_INTERVAL_MS
            alarmManager.set(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAtMs,
                pendingIntent(context)
            )
        }

        private fun cancelNextCheck(context: Context) {
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            alarmManager.cancel(pendingIntent(context))
        }

        private fun pendingIntent(context: Context): PendingIntent {
            return PendingIntent.getBroadcast(
                context,
                CHECK_REQUEST_CODE,
                Intent(context, ProtectBatteryReceiver::class.java).apply {
                    action = ACTION_CHECK
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
}
