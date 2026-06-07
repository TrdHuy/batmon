package com.android.synclab.glimpse.domain.usecase

import android.app.Activity
import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.android.synclab.glimpse.base.contracts.GamepadRepository
import com.android.synclab.glimpse.R
import com.android.synclab.glimpse.data.model.ControllerInfo
import com.android.synclab.glimpse.data.model.BatteryChargeStatus
import com.android.synclab.glimpse.domain.manager.DeveloperOptionManager
import com.android.synclab.glimpse.infra.alarm.AndroidAlarmScheduler
import com.android.synclab.glimpse.infra.notification.AppNotificationChannel
import com.android.synclab.glimpse.infra.notification.AppNotificationDispatcher
import com.android.synclab.glimpse.infra.notification.AppNotificationRequest
import com.android.synclab.glimpse.infra.preferences.SharedPreferenceStore
import com.android.synclab.glimpse.utils.LogCompat

open class ProtectBatteryUseCases(
    private val context: Context? = null,
    private val gamepadRepository: GamepadRepository? = null,
    private val developerOptionManager: DeveloperOptionManager? = null,
    private val alarmScheduler: AndroidAlarmScheduler? = null,
    private val checkReceiverClass: Class<out BroadcastReceiver>? = null,
    private val checkAction: String? = null,
    private val contentActivityClass: Class<out Activity>? = null
) {
    open fun isEnabled(): Boolean {
        return SharedPreferenceStore.getBoolean(
            context = requireContext(),
            prefsName = PREFS_NAME,
            key = KEY_ENABLED,
            defaultValue = false
        )
    }

    open fun setEnabled(enabled: Boolean) {
        SharedPreferenceStore.putBoolean(
            context = requireContext(),
            prefsName = PREFS_NAME,
            key = KEY_ENABLED,
            value = enabled
        )
    }

    open fun getAlertedControllerIds(): Set<String> {
        return SharedPreferenceStore.getStringSet(
            context = requireContext(),
            prefsName = PREFS_NAME,
            key = KEY_ALERTED_CONTROLLER_IDS,
            defaultValue = emptySet()
        )
    }

    open fun setAlertedControllerIds(controllerIds: Set<String>) {
        SharedPreferenceStore.putStringSet(
            context = requireContext(),
            prefsName = PREFS_NAME,
            key = KEY_ALERTED_CONTROLLER_IDS,
            value = controllerIds
        )
    }

    open fun getConnectedPs4Controllers(defaultDeviceName: String): List<ControllerInfo> {
        return requireGamepadRepository().getConnectedPs4Controllers(defaultDeviceName)
    }

    open fun isProtectBatteryFakeThresholdDetectionEnabled(): Boolean {
        return developerOptionManager?.isProtectBatteryFakeThresholdDetectionEnabled() ?: false
    }

    open fun setProtectBatteryFakeThresholdDetectionEnabled(enabled: Boolean) {
        developerOptionManager?.setProtectBatteryFakeThresholdDetectionEnabled(enabled)
    }

    open fun getFakeThresholdController(): ControllerInfo {
        return ControllerInfo(
            deviceId = FAKE_CONTROLLER_DEVICE_ID,
            name = FAKE_CONTROLLER_NAME,
            vendorId = 0,
            productId = 0,
            descriptor = FAKE_CONTROLLER_DESCRIPTOR,
            batteryPercent = 80,
            batteryStatus = BatteryChargeStatus.CHARGING
        )
    }

    open fun scheduleNextCheck() {
        requireAlarmScheduler().scheduleElapsedRealtimeWakeupBroadcast(
            receiverClass = requireCheckReceiverClass(),
            action = requireCheckAction(),
            requestCode = CHECK_REQUEST_CODE,
            delayMs = CHECK_INTERVAL_MS
        )
    }

    open fun cancelNextCheck() {
        requireAlarmScheduler().cancelBroadcast(
            receiverClass = requireCheckReceiverClass(),
            action = requireCheckAction(),
            requestCode = CHECK_REQUEST_CODE
        )
    }

    open fun postThresholdAlert(
        controllerId: String,
        controllerName: String,
        percent: Int
    ) {
        val appContext = requireContext()
        AppNotificationDispatcher.notify(
            context = appContext,
            request = buildThresholdAlertRequest(
                context = appContext,
                controllerName = controllerName,
                percent = percent
            )
        )
        LogCompat.i(
            "ProtectBattery alert posted " +
                    "controller=${maskIdentifier(controllerId)} percent=$percent"
        )
    }

    open fun postDevControllerThresholdAlert(percent: Int) {
        val appContext = requireContext()
        val controllerName = appContext.getString(R.string.unknown_controller_name)
        AppNotificationDispatcher.notify(
            context = appContext,
            request = buildThresholdAlertRequest(
                context = appContext,
                controllerName = controllerName,
                percent = percent
            )
        )
        LogCompat.i("ProtectBattery dev controller alert posted percent=$percent")
    }

    private fun requireGamepadRepository(): GamepadRepository {
        return requireNotNull(gamepadRepository) {
            "GamepadRepository is required for production ProtectBatteryUseCases"
        }
    }

    private fun requireContext(): Context {
        return requireNotNull(context) {
            "Context is required for production ProtectBatteryUseCases"
        }.applicationContext
    }

    private fun requireAlarmScheduler(): AndroidAlarmScheduler {
        return requireNotNull(alarmScheduler) {
            "AndroidAlarmScheduler is required for production ProtectBatteryUseCases"
        }
    }

    private fun requireCheckReceiverClass(): Class<out BroadcastReceiver> {
        return requireNotNull(checkReceiverClass) {
            "Check receiver class is required for production ProtectBatteryUseCases"
        }
    }

    private fun requireCheckAction(): String {
        return requireNotNull(checkAction) {
            "Check action is required for production ProtectBatteryUseCases"
        }
    }

    private fun requireContentActivityClass(): Class<out Activity> {
        return requireNotNull(contentActivityClass) {
            "Content activity class is required for production ProtectBatteryUseCases"
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
                intent = Intent(context, requireContentActivityClass())
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
        private const val PREFS_NAME = "protect_battery"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_ALERTED_CONTROLLER_IDS = "alerted_controller_ids"
        private const val CHANNEL_ID = "protect_battery_alerts_v1"
        private const val NOTIFICATION_ID = 32001
        private const val CONTENT_REQUEST_CODE = 32002
        private const val CHECK_REQUEST_CODE = 32011
        private const val CHECK_INTERVAL_MS = 60_000L
        private const val FAKE_CONTROLLER_DEVICE_ID = -32001
        private const val FAKE_CONTROLLER_NAME = "Protect Battery Dev Controller"
        private const val FAKE_CONTROLLER_DESCRIPTOR =
            "protect_battery_dev_fake_controller"
    }
}
