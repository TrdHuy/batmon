package com.android.synclab.glimpse.infra.protectbattery

import android.app.Activity
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.android.synclab.glimpse.R
import com.android.synclab.glimpse.infra.notification.AppNotificationChannel
import com.android.synclab.glimpse.infra.notification.AppNotificationDispatcher
import com.android.synclab.glimpse.infra.notification.AppNotificationRequest
import com.android.synclab.glimpse.infra.preferences.SharedPreferenceStore
import com.android.synclab.glimpse.utils.LogCompat

class AndroidProtectBatteryGateway(
    context: Context,
    private val checkReceiverClass: Class<out BroadcastReceiver>,
    private val contentActivityClass: Class<out Activity>,
    private val checkAction: String
) {
    private val appContext = context.applicationContext

    fun isEnabled(): Boolean {
        return SharedPreferenceStore.getBoolean(
            context = appContext,
            prefsName = PREFS_NAME,
            key = KEY_ENABLED,
            defaultValue = false
        )
    }

    fun setEnabled(enabled: Boolean) {
        SharedPreferenceStore.putBoolean(
            context = appContext,
            prefsName = PREFS_NAME,
            key = KEY_ENABLED,
            value = enabled
        )
    }

    fun getAlertedControllerIds(): Set<String> {
        return SharedPreferenceStore.getStringSet(
            context = appContext,
            prefsName = PREFS_NAME,
            key = KEY_ALERTED_CONTROLLER_IDS,
            defaultValue = emptySet()
        )
    }

    fun setAlertedControllerIds(controllerIds: Set<String>) {
        SharedPreferenceStore.putStringSet(
            context = appContext,
            prefsName = PREFS_NAME,
            key = KEY_ALERTED_CONTROLLER_IDS,
            value = controllerIds
        )
    }

    fun scheduleNextCheck() {
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val triggerAtMs = SystemClock.elapsedRealtime() + CHECK_INTERVAL_MS
        alarmManager.set(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            triggerAtMs,
            pendingIntent()
        )
    }

    fun cancelNextCheck() {
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(pendingIntent())
    }

    fun postThresholdAlert(
        controllerId: String,
        controllerName: String,
        percent: Int
    ) {
        AppNotificationDispatcher.notify(
            context = appContext,
            request = buildThresholdAlertRequest(controllerName, percent)
        )
        LogCompat.i(
            "ProtectBattery alert posted " +
                    "controller=${maskIdentifier(controllerId)} percent=$percent"
        )
    }

    fun postDevControllerThresholdAlert(percent: Int) {
        val controllerName = appContext.getString(R.string.unknown_controller_name)
        AppNotificationDispatcher.notify(
            context = appContext,
            request = buildThresholdAlertRequest(controllerName, percent)
        )
        LogCompat.i("ProtectBattery dev controller alert posted percent=$percent")
    }

    private fun buildThresholdAlertRequest(
        controllerName: String,
        percent: Int
    ): AppNotificationRequest {
        val text = appContext.getString(
            R.string.protect_battery_notification_text,
            controllerName,
            percent
        )
        return AppNotificationRequest(
            notificationId = NOTIFICATION_ID,
            channel = AppNotificationChannel(
                id = CHANNEL_ID,
                name = appContext.getString(R.string.protect_battery_channel_name),
                description = appContext.getString(R.string.protect_battery_channel_description),
                importance = NotificationManager.IMPORTANCE_HIGH,
                enableVibration = true
            ),
            smallIconRes = R.drawable.ic_ui_protect_battery,
            title = appContext.getString(R.string.protect_battery_notification_title),
            text = text,
            bigText = text,
            contentIntent = AppNotificationDispatcher.activityPendingIntent(
                context = appContext,
                requestCode = CONTENT_REQUEST_CODE,
                intent = Intent(appContext, contentActivityClass)
            ),
            autoCancel = true,
            category = Notification.CATEGORY_ALARM,
            defaults = Notification.DEFAULT_SOUND or Notification.DEFAULT_VIBRATE,
            priority = Notification.PRIORITY_HIGH
        )
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

    private fun maskIdentifier(identifier: String): String {
        if (identifier.length <= 8) {
            return "***"
        }
        return "${identifier.take(4)}***${identifier.takeLast(4)}"
    }

    companion object {
        private const val PREFS_NAME = "protect_battery"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_ALERTED_CONTROLLER_IDS = "alerted_controller_ids"
        private const val CHANNEL_ID = "protect_battery_alerts_v1"
        private const val NOTIFICATION_ID = 32001
        private const val CONTENT_REQUEST_CODE = 32002
        private const val CHECK_REQUEST_CODE = 32011
        private const val CHECK_INTERVAL_MS = 60_000L
    }
}
