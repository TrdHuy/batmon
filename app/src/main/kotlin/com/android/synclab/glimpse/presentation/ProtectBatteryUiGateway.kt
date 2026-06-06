package com.android.synclab.glimpse.presentation

import android.Manifest
import androidx.appcompat.app.AppCompatActivity
import com.android.synclab.glimpse.R
import com.android.synclab.glimpse.infra.notification.AppNotificationDispatcher
import com.android.synclab.glimpse.presentation.feature.ProtectBatteryUiPort

class ProtectBatteryUiGateway(
    private val activity: AppCompatActivity,
    private val notificationPermissionRequestCode: Int,
    private val hasPendingPermission: () -> Boolean,
    private val setPendingPermission: (Boolean) -> Unit,
    private val setSelectedEnabled: (Boolean) -> Unit,
    private val renderSettings: () -> Unit,
    private val showToast: (Int) -> Unit
) : ProtectBatteryUiPort {
    override fun hasNotificationPermission(): Boolean {
        return AppNotificationDispatcher.canPostNotifications(activity)
    }

    override fun hasPendingNotificationPermission(): Boolean {
        return hasPendingPermission()
    }

    override fun setPendingNotificationPermission(pending: Boolean) {
        setPendingPermission(pending)
    }

    override fun setSelectedEnabled(enabled: Boolean) {
        setSelectedEnabled.invoke(enabled)
    }

    override fun defaultControllerName(): String {
        return activity.getString(R.string.unknown_controller_name)
    }

    override fun requestNotificationPermission() {
        activity.requestPermissions(
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            notificationPermissionRequestCode
        )
    }

    override fun render() {
        renderSettings()
    }

    override fun showNotificationPermissionRequired() {
        showToast(R.string.toast_protect_battery_notification_permission_required)
    }

    override fun showEnabled() {
        showToast(R.string.toast_protect_battery_enabled)
    }

    override fun showDisabled() {
        showToast(R.string.toast_protect_battery_disabled)
    }
}
