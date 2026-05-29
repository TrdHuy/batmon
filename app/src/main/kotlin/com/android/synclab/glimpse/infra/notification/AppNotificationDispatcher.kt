package com.android.synclab.glimpse.infra.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

object AppNotificationDispatcher {
    fun canPostNotifications(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
    }

    fun notify(
        context: Context,
        request: AppNotificationRequest
    ) {
        if (!canPostNotifications(context)) {
            return
        }

        val notificationManager = context.getSystemService(NotificationManager::class.java)
            ?: return
        ensureChannel(notificationManager, request.channel)

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, request.channel.id)
        } else {
            Notification.Builder(context)
        }

        val notification = builder
            .setSmallIcon(request.smallIconRes)
            .setContentTitle(request.title)
            .setContentText(request.text)
            .setContentIntent(request.contentIntent)
            .setAutoCancel(request.autoCancel)
            .setCategory(request.category)
            .setDefaults(request.defaults)
            .setPriority(request.priority)
            .apply {
                request.bigText?.let {
                    setStyle(Notification.BigTextStyle().bigText(it))
                }
            }
            .build()

        notificationManager.notify(request.notificationId, notification)
    }

    fun activityPendingIntent(
        context: Context,
        requestCode: Int,
        intent: Intent
    ): PendingIntent {
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            pendingIntentFlags()
        )
    }

    private fun ensureChannel(
        notificationManager: NotificationManager,
        channel: AppNotificationChannel
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val notificationChannel = NotificationChannel(
            channel.id,
            channel.name,
            channel.importance
        ).apply {
            description = channel.description
            enableVibration(channel.enableVibration)
        }
        notificationManager.createNotificationChannel(notificationChannel)
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

data class AppNotificationChannel(
    val id: String,
    val name: String,
    val description: String,
    val importance: Int,
    val enableVibration: Boolean
)

data class AppNotificationRequest(
    val notificationId: Int,
    val channel: AppNotificationChannel,
    val smallIconRes: Int,
    val title: String,
    val text: String,
    val bigText: String?,
    val contentIntent: PendingIntent?,
    val autoCancel: Boolean,
    val category: String,
    val defaults: Int,
    val priority: Int
)
