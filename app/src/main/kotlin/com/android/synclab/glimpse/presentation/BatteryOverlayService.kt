package com.android.synclab.glimpse.presentation

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.android.synclab.glimpse.R
import com.android.synclab.glimpse.data.state.MonitoringStateStore
import com.android.synclab.glimpse.di.AppContainer
import com.android.synclab.glimpse.domain.usecase.GetPrimaryGamepadBatteryUseCase
import com.android.synclab.glimpse.infra.notification.MonitoringNotificationController
import com.android.synclab.glimpse.infra.overlay.OverlayWindowController
import com.android.synclab.glimpse.presentation.feature.BatteryTargetResolver
import com.android.synclab.glimpse.utils.LogCompat

class BatteryOverlayService : Service() {
    companion object {
        const val ACTION_START_MONITORING =
            "com.android.synclab.glimpse.action.START_MONITORING"
        const val ACTION_STOP_MONITORING =
            "com.android.synclab.glimpse.action.STOP_MONITORING"
        const val ACTION_SHOW_OVERLAY =
            "com.android.synclab.glimpse.action.SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY =
            "com.android.synclab.glimpse.action.HIDE_OVERLAY"
        const val EXTRA_CONTROLLER_IDENTIFIER =
            "com.android.synclab.glimpse.extra.CONTROLLER_IDENTIFIER"

        private const val NOTIFICATION_ID = 31001
        private const val CHANNEL_ID = "glimpse_monitor_channel_v1"
        private const val UPDATE_INTERVAL_MS = 60_000L

        val isRunning: Boolean
            get() = MonitoringStateStore.isServiceRunning

        val isMonitoringEnabled: Boolean
            get() = MonitoringStateStore.isMonitoringEnabled

        val isOverlayVisible: Boolean
            get() = MonitoringStateStore.isOverlayVisible

        val lastStatusText: String
            get() = MonitoringStateStore.lastStatusText
    }

    private val handler = Handler(Looper.getMainLooper())
    private val batteryTargetResolver = BatteryTargetResolver()

    private lateinit var getPrimaryGamepadBatteryUseCase: GetPrimaryGamepadBatteryUseCase
    private lateinit var overlayWindowController: OverlayWindowController
    private lateinit var notificationController: MonitoringNotificationController

    private var foregroundStarted = false
    private var updateLoopStarted = false
    private var activeMonitoringControllerIdentifier: String? = null
    private var activeOverlayControllerIdentifier: String? = null

    private val updateRunnable = object : Runnable {
        override fun run() {
            updateBatteryState()
            if (!MonitoringStateStore.isMonitoringEnabled && !MonitoringStateStore.isOverlayVisible) {
                LogCompat.i("Service idle (no monitoring, no overlay), stopping self")
                stopSelf()
                return
            }
            handler.postDelayed(this, UPDATE_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        LogCompat.i("onCreate")

        val appContainer = AppContainer.from(applicationContext)
        getPrimaryGamepadBatteryUseCase = appContainer.providePrimaryGamepadBatteryUseCase()

        overlayWindowController = appContainer.provideOverlayWindowController(this)
        notificationController = appContainer.provideMonitoringNotificationController(
            service = this,
            channelId = CHANNEL_ID,
            stopAction = ACTION_STOP_MONITORING
        )
        notificationController.ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_MONITORING
        LogCompat.i("onStartCommand action=$action startId=$startId")

        if (action == ACTION_STOP_MONITORING) {
            MonitoringStateStore.isMonitoringEnabled = false
            activeMonitoringControllerIdentifier = null
            LogCompat.i("Received stop monitoring action")
            LogCompat.dDebug {
                "UI_VERIFY BM Service action=stop target=none monitoring=false"
            }
            if (foregroundStarted) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                foregroundStarted = false
            }
            if (!MonitoringStateStore.isOverlayVisible) {
                stopSelf()
                return START_NOT_STICKY
            }
            if (!updateLoopStarted) {
                updateLoopStarted = true
                handler.post(updateRunnable)
            } else {
                updateBatteryState()
            }
            return START_STICKY
        }

        MonitoringStateStore.isServiceRunning = true

        when (action) {
            ACTION_HIDE_OVERLAY -> {
                overlayWindowController.hide()
                MonitoringStateStore.isOverlayVisible = overlayWindowController.isVisible()
                activeOverlayControllerIdentifier = null
                LogCompat.dDebug {
                    "UI_VERIFY OverlayService action=hide target=none " +
                            "visible=${MonitoringStateStore.isOverlayVisible}"
                }
            }

            ACTION_SHOW_OVERLAY -> {
                activeOverlayControllerIdentifier = readControllerIdentifier(intent)
                MonitoringStateStore.isOverlayVisible = overlayWindowController.show()
                LogCompat.dDebug {
                    "UI_VERIFY OverlayService action=show " +
                            "target=${activeOverlayControllerIdentifier?.let(::maskIdentifier) ?: "primary"} " +
                            "visible=${MonitoringStateStore.isOverlayVisible}"
                }
            }

            ACTION_START_MONITORING -> {
                activeMonitoringControllerIdentifier = readControllerIdentifier(intent)
                MonitoringStateStore.isMonitoringEnabled = true
                LogCompat.i(
                    "Monitoring enabled target=" +
                            (activeMonitoringControllerIdentifier?.let(::maskIdentifier) ?: "primary")
                )
                LogCompat.dDebug {
                    "UI_VERIFY BM Service action=start " +
                            "target=${activeMonitoringControllerIdentifier?.let(::maskIdentifier) ?: "primary"} " +
                            "monitoring=${MonitoringStateStore.isMonitoringEnabled}"
                }
                if (
                    !ensureForegroundStarted(
                        getString(R.string.notification_initial_text),
                        R.drawable.ic_battery_unknown
                    )
                ) {
                    LogCompat.e("Cannot start foreground service due to missing prerequisites")
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }

        if (!MonitoringStateStore.isMonitoringEnabled && !MonitoringStateStore.isOverlayVisible) {
            LogCompat.i("No monitoring and no overlay after action=$action, stopping service")
            stopSelf()
            return START_NOT_STICKY
        }

        if (!updateLoopStarted) {
            updateLoopStarted = true
            handler.post(updateRunnable)
        } else {
            updateBatteryState()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        LogCompat.i("onDestroy")
        handler.removeCallbacks(updateRunnable)
        updateLoopStarted = false

        overlayWindowController.hide()

        if (foregroundStarted) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foregroundStarted = false
        }

        MonitoringStateStore.isServiceRunning = false
        MonitoringStateStore.isMonitoringEnabled = false
        MonitoringStateStore.isOverlayVisible = false
        MonitoringStateStore.lastStatusText = ""
        activeMonitoringControllerIdentifier = null
        activeOverlayControllerIdentifier = null

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun ensureForegroundStarted(contentText: String, iconRes: Int): Boolean {
        val notification = notificationController.build(contentText, iconRes)

        return try {
            if (!foregroundStarted) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                foregroundStarted = true
                LogCompat.i("Foreground started")
            } else {
                notificationController.notify(NOTIFICATION_ID, notification)
            }
            true
        } catch (securityException: SecurityException) {
            LogCompat.e("Security exception while starting foreground", securityException)
            false
        } catch (throwable: Throwable) {
            LogCompat.e("Unexpected exception while starting foreground", throwable)
            false
        }
    }

    private fun updateBatteryState() {
        val targets = batteryTargetResolver.resolveTargets(
            BatteryTargetResolver.RuntimeState(
                isMonitoringEnabled = MonitoringStateStore.isMonitoringEnabled,
                isOverlayVisible = MonitoringStateStore.isOverlayVisible,
                activeMonitoringControllerIdentifier = activeMonitoringControllerIdentifier,
                activeOverlayControllerIdentifier = activeOverlayControllerIdentifier
            )
        )
        val overlaySnapshot = getPrimaryGamepadBatteryUseCase(
            defaultControllerName = getString(R.string.unknown_controller_name),
            controllerIdentifier = targets.overlayControllerIdentifier
        )
        val notificationSnapshot = if (targets.reuseOverlaySnapshotForNotification) {
            overlaySnapshot
        } else {
            getPrimaryGamepadBatteryUseCase(
                defaultControllerName = getString(R.string.unknown_controller_name),
                controllerIdentifier = targets.notificationControllerIdentifier
            )
        }

        val overlayText = when {
            overlaySnapshot.controllerName == null -> getString(R.string.overlay_no_gamepad)
            overlaySnapshot.batteryPercent == null -> getString(R.string.overlay_battery_unavailable)
            else -> getString(R.string.overlay_battery_percent, overlaySnapshot.batteryPercent)
        }
        overlayWindowController.updateText(overlayText)

        val notificationText = when {
            notificationSnapshot.controllerName == null -> getString(R.string.notification_no_gamepad)
            notificationSnapshot.batteryPercent == null -> getString(
                R.string.notification_battery_unavailable,
                notificationSnapshot.controllerName
            )

            else -> getString(
                R.string.notification_battery_percent,
                notificationSnapshot.controllerName,
                notificationSnapshot.batteryPercent
            )
        }

        val iconRes = pickNotificationIcon(notificationSnapshot.batteryPercent)
        MonitoringStateStore.lastStatusText = notificationText
        LogCompat.d(
            "updateBatteryState target=${targets.overlayControllerIdentifier?.let(::maskIdentifier) ?: "primary"} " +
                    "controller=${overlaySnapshot.controllerName} percent=${overlaySnapshot.batteryPercent} " +
                    "monitoringTarget=${targets.notificationControllerIdentifier?.let(::maskIdentifier) ?: "primary"} " +
                    "monitoringController=${notificationSnapshot.controllerName} " +
                    "monitoringPercent=${notificationSnapshot.batteryPercent}"
        )
        LogCompat.dDebug {
            "UI_VERIFY OverlayService battery " +
                    "target=${targets.overlayControllerIdentifier?.let(::maskIdentifier) ?: "primary"} " +
                    "controller=${overlaySnapshot.controllerName ?: "none"} " +
                    "percent=${overlaySnapshot.batteryPercent} " +
                    "overlayVisible=${MonitoringStateStore.isOverlayVisible}"
        }
        LogCompat.dDebug {
            "UI_VERIFY BM battery " +
                    "target=${targets.notificationControllerIdentifier?.let(::maskIdentifier) ?: "primary"} " +
                    "controller=${notificationSnapshot.controllerName ?: "none"} " +
                    "percent=${notificationSnapshot.batteryPercent} " +
                    "monitoring=${MonitoringStateStore.isMonitoringEnabled}"
        }

        if (MonitoringStateStore.isMonitoringEnabled) {
            ensureForegroundStarted(notificationText, iconRes)
        } else if (foregroundStarted) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foregroundStarted = false
            LogCompat.i("Foreground removed because monitoring is disabled")
        }
    }

    private fun pickNotificationIcon(percent: Int?): Int {
        return when (batteryTargetResolver.notificationIconLevelFor(percent)) {
            BatteryTargetResolver.NotificationIconLevel.UNKNOWN -> R.drawable.ic_battery_unknown
            BatteryTargetResolver.NotificationIconLevel.BATTERY_0 -> R.drawable.ic_battery_0
            BatteryTargetResolver.NotificationIconLevel.BATTERY_25 -> R.drawable.ic_battery_25
            BatteryTargetResolver.NotificationIconLevel.BATTERY_50 -> R.drawable.ic_battery_50
            BatteryTargetResolver.NotificationIconLevel.BATTERY_75 -> R.drawable.ic_battery_75
            BatteryTargetResolver.NotificationIconLevel.BATTERY_100 -> R.drawable.ic_battery_100
        }
    }

    private fun readControllerIdentifier(intent: Intent?): String? {
        return intent
            ?.getStringExtra(EXTRA_CONTROLLER_IDENTIFIER)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun maskIdentifier(raw: String): String {
        return if (raw.length <= 12) raw else "${raw.take(6)}...${raw.takeLast(6)}"
    }
}
