package com.android.synclab.glimpse.presentation

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import com.android.synclab.glimpse.R
import com.android.synclab.glimpse.infra.notification.AppNotificationChannel
import com.android.synclab.glimpse.infra.notification.AppNotificationDispatcher
import com.android.synclab.glimpse.infra.notification.AppNotificationRequest
import com.android.synclab.glimpse.infra.preferences.SharedPreferenceStore
import com.android.synclab.glimpse.presentation.feature.PhoneBatterySnapshot
import com.android.synclab.glimpse.presentation.feature.ProtectBatteryPlanner
import com.android.synclab.glimpse.presentation.feature.ProtectBatteryDecision
import com.android.synclab.glimpse.presentation.feature.ProtectBatteryReceiverEvent
import com.android.synclab.glimpse.presentation.feature.ProtectBatteryRuntimePort
import com.android.synclab.glimpse.utils.LogCompat

class ProtectBatteryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        LogCompat.d("ProtectBatteryReceiver action=$action")

        if (action == Intent.ACTION_POWER_DISCONNECTED) {
            ProtectBatteryPlanner().onReceiverEvent(
                event = ProtectBatteryReceiverEvent.PowerDisconnected,
                port = runtimePort(context)
            )
            return
        }

        ProtectBatteryPlanner().onReceiverEvent(
            event = ProtectBatteryReceiverEvent.Check,
            port = runtimePort(context)
        )
    }

    companion object {
        const val ACTION_CHECK =
            "com.android.synclab.glimpse.action.PROTECT_BATTERY_CHECK"
        private const val PREFS_NAME = "protect_battery"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_ALERT_SHOWN_FOR_CHARGE_SESSION = "alert_shown_for_charge_session"
        private const val CHANNEL_ID = "protect_battery_alerts_v1"
        private const val NOTIFICATION_ID = 32001
        private const val CONTENT_REQUEST_CODE = 32002
        private const val CHECK_REQUEST_CODE = 32011
        private const val CHECK_INTERVAL_MS = 60_000L

        fun isEnabled(context: Context): Boolean {
            return SharedPreferenceStore.getBoolean(
                context = context,
                prefsName = PREFS_NAME,
                key = KEY_ENABLED,
                defaultValue = false
            )
        }

        fun enable(context: Context) {
            ProtectBatteryPlanner().onManualEnable(runtimePort(context))
        }

        fun disable(context: Context) {
            ProtectBatteryPlanner().onManualDisable(runtimePort(context))
        }

        private fun runtimePort(context: Context): ProtectBatteryRuntimePort {
            val appContext = context.applicationContext
            return object : ProtectBatteryRuntimePort {
                override fun isEnabled(): Boolean {
                    return SharedPreferenceStore.getBoolean(
                        context = appContext,
                        prefsName = PREFS_NAME,
                        key = KEY_ENABLED,
                        defaultValue = false
                    )
                }

                override fun setEnabled(enabled: Boolean) {
                    SharedPreferenceStore.putBoolean(
                        context = appContext,
                        prefsName = PREFS_NAME,
                        key = KEY_ENABLED,
                        value = enabled
                    )
                }

                override fun isAlertShownForChargeSession(): Boolean {
                    return SharedPreferenceStore.getBoolean(
                        context = appContext,
                        prefsName = PREFS_NAME,
                        key = KEY_ALERT_SHOWN_FOR_CHARGE_SESSION,
                        defaultValue = false
                    )
                }

                override fun setAlertShownForChargeSession(shown: Boolean) {
                    SharedPreferenceStore.putBoolean(
                        context = appContext,
                        prefsName = PREFS_NAME,
                        key = KEY_ALERT_SHOWN_FOR_CHARGE_SESSION,
                        value = shown
                    )
                }

                override fun readPhoneBattery(): PhoneBatterySnapshot? {
                    return readPhoneBatterySnapshot(appContext)
                }

                override fun scheduleNextCheck() {
                    scheduleNextCheck(appContext)
                }

                override fun cancelNextCheck() {
                    cancelNextCheck(appContext)
                }

                override fun postThresholdAlert(percent: Int) {
                    AppNotificationDispatcher.notify(
                        context = appContext,
                        request = buildThresholdAlertRequest(appContext, percent)
                    )
                    LogCompat.i("ProtectBattery alert posted percent=$percent")
                }

                override fun logCheck(
                    enabled: Boolean,
                    battery: PhoneBatterySnapshot?,
                    decision: ProtectBatteryDecision
                ) {
                    LogCompat.d(
                        "ProtectBattery check enabled=$enabled " +
                                "percent=${battery?.percent} charging=${battery?.isCharging} " +
                                "notify=${decision.shouldNotify} " +
                                "schedule=${decision.shouldScheduleNextCheck}"
                    )
                }
            }
        }

        private fun buildThresholdAlertRequest(
            context: Context,
            percent: Int
        ): AppNotificationRequest {
            val text = context.getString(R.string.protect_battery_notification_text, percent)
            return AppNotificationRequest(
                notificationId = NOTIFICATION_ID,
                channel = AppNotificationChannel(
                    id = CHANNEL_ID,
                    name = context.getString(R.string.protect_battery_channel_name),
                    description = context.getString(R.string.protect_battery_channel_description),
                    importance = NotificationManager.IMPORTANCE_HIGH,
                    enableVibration = true
                ),
                smallIconRes = R.drawable.ic_ui_protect_battery,
                title = context.getString(R.string.protect_battery_notification_title),
                text = text,
                bigText = text,
                contentIntent = AppNotificationDispatcher.activityPendingIntent(
                    context = context,
                    requestCode = CONTENT_REQUEST_CODE,
                    intent = Intent(context, MainActivity::class.java)
                ),
                autoCancel = true,
                category = Notification.CATEGORY_ALARM,
                defaults = Notification.DEFAULT_SOUND or Notification.DEFAULT_VIBRATE,
                priority = Notification.PRIORITY_HIGH
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
