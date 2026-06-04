package com.android.synclab.glimpse.presentation

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
import com.android.synclab.glimpse.data.model.BatteryChargeStatus
import com.android.synclab.glimpse.data.model.ControllerInfo
import com.android.synclab.glimpse.di.AppContainer
import com.android.synclab.glimpse.infra.notification.AppNotificationChannel
import com.android.synclab.glimpse.infra.notification.AppNotificationDispatcher
import com.android.synclab.glimpse.infra.notification.AppNotificationRequest
import com.android.synclab.glimpse.infra.preferences.SharedPreferenceStore
import com.android.synclab.glimpse.presentation.feature.ControllerBatterySnapshot
import com.android.synclab.glimpse.presentation.feature.ProtectBatteryAlert
import com.android.synclab.glimpse.presentation.feature.ProtectBatteryDecision
import com.android.synclab.glimpse.presentation.feature.ProtectBatteryPlanner
import com.android.synclab.glimpse.presentation.feature.ProtectBatteryRuntimePort
import com.android.synclab.glimpse.utils.LogCompat

class ProtectBatteryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        LogCompat.d("ProtectBatteryReceiver action=${intent?.action}")
        ProtectBatteryPlanner().onReceiverEvent(runtimePort(context))
    }

    companion object {
        const val ACTION_CHECK =
            "com.android.synclab.glimpse.action.PROTECT_BATTERY_CHECK"
        private const val PREFS_NAME = "protect_battery"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_ALERTED_CONTROLLER_IDS = "alerted_controller_ids"
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

        fun postDevControllerThresholdAlert(
            context: Context,
            percent: Int = ProtectBatteryPlanner.DEFAULT_THRESHOLD_PERCENT
        ) {
            val appContext = context.applicationContext
            val controllerName = appContext.getString(R.string.unknown_controller_name)
            AppNotificationDispatcher.notify(
                context = appContext,
                request = buildThresholdAlertRequest(appContext, controllerName, percent)
            )
            LogCompat.i("ProtectBattery dev controller alert posted percent=$percent")
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

                override fun getAlertedControllerIds(): Set<String> {
                    return SharedPreferenceStore.getStringSet(
                        context = appContext,
                        prefsName = PREFS_NAME,
                        key = KEY_ALERTED_CONTROLLER_IDS,
                        defaultValue = emptySet()
                    )
                }

                override fun setAlertedControllerIds(controllerIds: Set<String>) {
                    SharedPreferenceStore.putStringSet(
                        context = appContext,
                        prefsName = PREFS_NAME,
                        key = KEY_ALERTED_CONTROLLER_IDS,
                        value = controllerIds
                    )
                }

                override fun readControllerBatteries(): List<ControllerBatterySnapshot> {
                    return readControllerBatterySnapshots(appContext)
                }

                override fun scheduleNextCheck() {
                    scheduleNextCheck(appContext)
                }

                override fun cancelNextCheck() {
                    cancelNextCheck(appContext)
                }

                override fun postThresholdAlert(alert: ProtectBatteryAlert) {
                    AppNotificationDispatcher.notify(
                        context = appContext,
                        request = buildThresholdAlertRequest(
                            context = appContext,
                            controllerName = alert.controllerName,
                            percent = alert.percent
                        )
                    )
                    LogCompat.i(
                        "ProtectBattery alert posted " +
                                "controller=${maskIdentifier(alert.controllerId)} percent=${alert.percent}"
                    )
                }

                override fun logCheck(
                    enabled: Boolean,
                    batteries: List<ControllerBatterySnapshot>,
                    decision: ProtectBatteryDecision
                ) {
                    LogCompat.d(
                        "ProtectBattery check enabled=$enabled " +
                                "controllers=${batteries.size} " +
                                "alerts=${decision.alerts.size} " +
                                "tracked=${decision.alertedControllerIds.size} " +
                                "schedule=${decision.shouldScheduleNextCheck}"
                    )
                }
            }
        }

        private fun buildThresholdAlertRequest(
            context: Context,
            controllerName: String,
            percent: Int
        ): AppNotificationRequest {
            val text = context.getString(
                R.string.protect_battery_notification_text,
                controllerName,
                percent
            )
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

        private fun readControllerBatterySnapshots(context: Context): List<ControllerBatterySnapshot> {
            val controllers = AppContainer.from(context)
                .provideConnectedPs4ControllersUseCase()
                .invoke(context.getString(R.string.unknown_controller_name))
            return controllers.map { controller ->
                ControllerBatterySnapshot(
                    controllerId = buildControllerId(controller),
                    controllerName = controller.name,
                    percent = controller.batteryPercent,
                    status = controller.batteryStatus ?: BatteryChargeStatus.UNKNOWN
                )
            }
        }

        private fun buildControllerId(controller: ControllerInfo): String {
            val descriptor = controller.descriptor?.trim().orEmpty()
            if (descriptor.isNotEmpty()) {
                return descriptor
            }
            return "VID:${controller.vendorId}_PID:${controller.productId}_DID:${controller.deviceId}"
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

        private fun maskIdentifier(identifier: String): String {
            if (identifier.length <= 8) {
                return "***"
            }
            return "${identifier.take(4)}***${identifier.takeLast(4)}"
        }
    }
}
