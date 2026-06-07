package com.android.synclab.glimpse.infra.protectbattery

import android.app.Activity
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import com.android.synclab.glimpse.R
import com.android.synclab.glimpse.base.contracts.ProtectBatteryAlertNotifier
import com.android.synclab.glimpse.infra.notification.AppNotificationChannel
import com.android.synclab.glimpse.infra.notification.AppNotificationDispatcher
import com.android.synclab.glimpse.infra.notification.AppNotificationRequest
import com.android.synclab.glimpse.utils.LogCompat

class AndroidProtectBatteryAlertNotifier(
    context: Context,
    private val contentActivityClass: Class<out Activity>
) : ProtectBatteryAlertNotifier {
    private val appContext = context.applicationContext

    override fun postThresholdAlert(
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

    override fun postDevControllerThresholdAlert(percent: Int) {
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

    private fun maskIdentifier(identifier: String): String {
        if (identifier.length <= 8) {
            return "***"
        }
        return "${identifier.take(4)}***${identifier.takeLast(4)}"
    }

    companion object {
        private const val CHANNEL_ID = "protect_battery_alerts_v1"
        private const val NOTIFICATION_ID = 32001
        private const val CONTENT_REQUEST_CODE = 32002
    }
}
