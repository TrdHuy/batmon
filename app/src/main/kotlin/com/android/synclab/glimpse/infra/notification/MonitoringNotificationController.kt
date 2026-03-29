package com.android.synclab.glimpse.infra.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import com.android.synclab.glimpse.R
import com.android.synclab.glimpse.presentation.BatteryOverlayService
import com.android.synclab.glimpse.presentation.MainActivity
import com.android.synclab.glimpse.utils.LogCompat

class MonitoringNotificationController(
    private val service: Service,
    private val channelId: String,
    private val stopAction: String
) {
    private val notificationManager: NotificationManager? =
        service.getSystemService(NotificationManager::class.java)

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channel = NotificationChannel(
            channelId,
            service.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = service.getString(R.string.notification_channel_description)
        }
        notificationManager?.createNotificationChannel(channel)
        LogCompat.d("Notification channel ensured")
    }

    fun build(contentText: String, iconRes: Int): Notification {
        val openAppIntent = PendingIntent.getActivity(
            service,
            1,
            Intent(service, MainActivity::class.java),
            pendingIntentFlags()
        )

        val stopIntent = PendingIntent.getService(
            service,
            2,
            Intent(service, BatteryOverlayService::class.java).apply {
                action = stopAction
            },
            pendingIntentFlags()
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(service, channelId)
        } else {
            Notification.Builder(service)
        }

        val configuredBuilder = builder
            .setSmallIcon(iconRes)
            .setContentTitle(service.getString(R.string.notification_title))
            .setContentText(contentText)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(openAppIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                service.getString(R.string.notification_action_stop),
                stopIntent
            )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            configuredBuilder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            configuredBuilder.setPriority(Notification.PRIORITY_DEFAULT)
        }

        return configuredBuilder.build()
    }

    fun notify(notificationId: Int, notification: Notification) {
        notificationManager?.notify(notificationId, notification)
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
