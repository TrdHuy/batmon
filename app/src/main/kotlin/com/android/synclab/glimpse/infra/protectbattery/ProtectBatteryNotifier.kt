package com.android.synclab.glimpse.infra.protectbattery

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.android.synclab.glimpse.R
import com.android.synclab.glimpse.presentation.MainActivity
import com.android.synclab.glimpse.utils.LogCompat

object ProtectBatteryNotifier {
    private const val CHANNEL_ID = "protect_battery_alerts_v1"
    private const val NOTIFICATION_ID = 32001
    private const val CONTENT_REQUEST_CODE = 32002

    fun canPostNotifications(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
    }

    fun showThresholdAlert(context: Context, percent: Int) {
        if (!canPostNotifications(context)) {
            LogCompat.w("ProtectBattery notification skipped because POST_NOTIFICATIONS is missing")
            return
        }

        val notificationManager = context.getSystemService(NotificationManager::class.java)
            ?: return
        ensureChannel(context, notificationManager)

        val contentIntent = PendingIntent.getActivity(
            context,
            CONTENT_REQUEST_CODE,
            Intent(context, MainActivity::class.java),
            pendingIntentFlags()
        )

        val title = context.getString(R.string.protect_battery_notification_title)
        val text = context.getString(R.string.protect_battery_notification_text, percent)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            Notification.Builder(context)
        }

        val notification = builder
            .setSmallIcon(R.drawable.ic_ui_protect_battery)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .setDefaults(Notification.DEFAULT_SOUND or Notification.DEFAULT_VIBRATE)
            .setPriority(Notification.PRIORITY_HIGH)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
        LogCompat.i("ProtectBattery alert posted percent=$percent")
    }

    private fun ensureChannel(
        context: Context,
        notificationManager: NotificationManager
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.protect_battery_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.protect_battery_channel_description)
            enableVibration(true)
        }
        notificationManager.createNotificationChannel(channel)
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
