package com.android.synclab.glimpse.presentation.feature

class BatteryTargetResolver {
    data class RuntimeState(
        val isMonitoringEnabled: Boolean,
        val isOverlayVisible: Boolean,
        val activeMonitoringControllerIdentifier: String?,
        val activeOverlayControllerIdentifier: String?
    )

    data class Targets(
        val overlayControllerIdentifier: String?,
        val notificationControllerIdentifier: String?,
        val reuseOverlaySnapshotForNotification: Boolean
    )

    enum class NotificationIconLevel {
        UNKNOWN,
        BATTERY_0,
        BATTERY_25,
        BATTERY_50,
        BATTERY_75,
        BATTERY_100
    }

    fun resolveTargets(state: RuntimeState): Targets {
        val overlayTargetIdentifier = state.activeOverlayControllerIdentifier
            ?.takeIf { state.isOverlayVisible }
        val monitoringTargetIdentifier = state.activeMonitoringControllerIdentifier
            ?.takeIf { state.isMonitoringEnabled }
        val notificationTargetIdentifier = if (state.isMonitoringEnabled) {
            monitoringTargetIdentifier
        } else {
            overlayTargetIdentifier
        }

        return Targets(
            overlayControllerIdentifier = overlayTargetIdentifier,
            notificationControllerIdentifier = notificationTargetIdentifier,
            reuseOverlaySnapshotForNotification = !state.isMonitoringEnabled ||
                    monitoringTargetIdentifier == overlayTargetIdentifier
        )
    }

    fun notificationIconLevelFor(percent: Int?): NotificationIconLevel {
        if (percent == null) {
            return NotificationIconLevel.UNKNOWN
        }

        return when {
            percent <= 10 -> NotificationIconLevel.BATTERY_0
            percent <= 35 -> NotificationIconLevel.BATTERY_25
            percent <= 60 -> NotificationIconLevel.BATTERY_50
            percent <= 85 -> NotificationIconLevel.BATTERY_75
            else -> NotificationIconLevel.BATTERY_100
        }
    }
}
