package com.example.batmondemo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.BatteryState
import android.hardware.input.InputManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.InputDevice
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.example.batmondemo.utils.LogCompat
import kotlin.math.roundToInt

class BatteryOverlayService : Service() {
    companion object {
        const val ACTION_START_MONITORING = "com.example.batmondemo.action.START_MONITORING"
        const val ACTION_STOP_MONITORING = "com.example.batmondemo.action.STOP_MONITORING"
        const val ACTION_SHOW_OVERLAY = "com.example.batmondemo.action.SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "com.example.batmondemo.action.HIDE_OVERLAY"

        private const val NOTIFICATION_ID = 31001
        private const val CHANNEL_ID = "batmon_monitor_channel_v2"
        private const val UPDATE_INTERVAL_MS = 60_000L

        @Volatile
        var isRunning: Boolean = false

        @Volatile
        var isMonitoringEnabled: Boolean = false

        @Volatile
        var isOverlayVisible: Boolean = false

        @Volatile
        var lastStatusText: String = ""
    }

    private val handler = Handler(Looper.getMainLooper())

    private var inputManager: InputManager? = null
    private var windowManager: WindowManager? = null
    private var notificationManager: NotificationManager? = null

    private var overlayView: View? = null
    private var overlayTextView: TextView? = null

    private var foregroundStarted = false
    private var updateLoopStarted = false

    private val updateRunnable = object : Runnable {
        override fun run() {
            updateBatteryState()
            if (!isMonitoringEnabled && !isOverlayVisible) {
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
        inputManager = getSystemService(InputManager::class.java)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannelIfNeeded()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_MONITORING
        LogCompat.i("onStartCommand action=$action startId=$startId")

        if (action == ACTION_STOP_MONITORING) {
            isMonitoringEnabled = false
            LogCompat.i("Received stop monitoring action")
            if (foregroundStarted) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                foregroundStarted = false
            }
            if (!isOverlayVisible) {
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
        isRunning = true

        when (action) {
            ACTION_HIDE_OVERLAY -> removeOverlayView()
            ACTION_SHOW_OVERLAY -> ensureOverlayView()
            ACTION_START_MONITORING -> {
                isMonitoringEnabled = true
                LogCompat.i("Monitoring enabled")
                if (!ensureForegroundStarted(
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

        if (!isMonitoringEnabled && !isOverlayVisible) {
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

        removeOverlayView()

        if (foregroundStarted) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foregroundStarted = false
        }

        isRunning = false
        isMonitoringEnabled = false
        isOverlayVisible = false
        lastStatusText = ""

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun ensureForegroundStarted(contentText: String, iconRes: Int): Boolean {
        val notification = buildNotification(contentText, iconRes)

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
                notificationManager?.notify(NOTIFICATION_ID, notification)
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
        val snapshot = readConnectedGamepadBattery()
        val overlayText = when {
            snapshot.controllerName == null -> getString(R.string.overlay_no_gamepad)
            snapshot.batteryPercent == null -> getString(R.string.overlay_battery_unavailable)
            else -> getString(R.string.overlay_battery_percent, snapshot.batteryPercent)
        }
        overlayTextView?.text = overlayText

        val notificationText = when {
            snapshot.controllerName == null -> getString(R.string.notification_no_gamepad)
            snapshot.batteryPercent == null -> getString(
                R.string.notification_battery_unavailable,
                snapshot.controllerName
            )

            else -> getString(
                R.string.notification_battery_percent,
                snapshot.controllerName,
                snapshot.batteryPercent
            )
        }

        val iconRes = pickNotificationIcon(snapshot.batteryPercent)
        lastStatusText = notificationText
        LogCompat.d("updateBatteryState controller=${snapshot.controllerName} percent=${snapshot.batteryPercent}")

        if (isMonitoringEnabled) {
            ensureForegroundStarted(notificationText, iconRes)
        } else if (foregroundStarted) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foregroundStarted = false
            LogCompat.i("Foreground removed because monitoring is disabled")
        }
    }

    private fun readConnectedGamepadBattery(): GamepadBatterySnapshot {
        val manager = inputManager
        val deviceIds = manager?.inputDeviceIds ?: InputDevice.getDeviceIds()

        var fallbackControllerName: String? = null

        for (deviceId in deviceIds) {
            val device = manager?.getInputDevice(deviceId) ?: InputDevice.getDevice(deviceId) ?: continue
            if (!isGamepad(device)) {
                continue
            }

            val controllerName = device.name ?: getString(R.string.unknown_controller_name)
            if (fallbackControllerName == null) {
                fallbackControllerName = controllerName
            }

            val batteryPercent = readBatteryPercent(device)
            if (batteryPercent != null) {
                return GamepadBatterySnapshot(controllerName, batteryPercent)
            }
        }

        if (fallbackControllerName != null) {
            return GamepadBatterySnapshot(fallbackControllerName, null)
        }

        return GamepadBatterySnapshot(null, null)
    }

    private fun readBatteryPercent(device: InputDevice): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return null
        }

        return try {
            val batteryState: BatteryState = device.batteryState ?: return null
            if (!batteryState.isPresent) {
                return null
            }

            val capacity = batteryState.capacity
            if (capacity.isNaN() || capacity < 0f) {
                return null
            }

            val normalized = if (capacity > 1.0f) capacity else capacity * 100f
            normalized.roundToInt().coerceIn(0, 100)
        } catch (exception: Exception) {
            LogCompat.w("Failed to read gamepad battery state", exception)
            null
        }
    }

    private fun isGamepad(device: InputDevice): Boolean {
        return device.supportsSource(InputDevice.SOURCE_GAMEPAD) ||
                device.supportsSource(InputDevice.SOURCE_JOYSTICK)
    }

    private fun ensureOverlayView() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            LogCompat.w("Overlay not supported below Android O")
            isOverlayVisible = false
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            LogCompat.w("Overlay permission missing, cannot show overlay")
            isOverlayVisible = false
            return
        }

        if (overlayView != null) {
            isOverlayVisible = true
            return
        }

        val view = LayoutInflater.from(this).inflate(R.layout.overlay_battery, null)
        val textView = view.findViewById<TextView>(R.id.overlayBatteryText)

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dpToPx(12)
            y = dpToPx(72)
        }

        try {
            windowManager?.addView(view, params)
            overlayView = view
            overlayTextView = textView
            isOverlayVisible = true
            LogCompat.i("Overlay attached")
        } catch (exception: Exception) {
            LogCompat.w("Failed to attach overlay view", exception)
            overlayView = null
            overlayTextView = null
            isOverlayVisible = false
        }
    }

    private fun removeOverlayView() {
        val currentView = overlayView ?: return
        try {
            windowManager?.removeView(currentView)
            LogCompat.i("Overlay removed")
        } catch (exception: Exception) {
            LogCompat.w("Failed to remove overlay view", exception)
        }

        overlayView = null
        overlayTextView = null
        isOverlayVisible = false
    }

    private fun pickNotificationIcon(percent: Int?): Int {
        if (percent == null) {
            return R.drawable.ic_battery_unknown
        }

        return when {
            percent <= 10 -> R.drawable.ic_battery_0
            percent <= 35 -> R.drawable.ic_battery_25
            percent <= 60 -> R.drawable.ic_battery_50
            percent <= 85 -> R.drawable.ic_battery_75
            else -> R.drawable.ic_battery_100
        }
    }

    private fun buildNotification(contentText: String, iconRes: Int): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            pendingIntentFlags()
        )

        val stopIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, BatteryOverlayService::class.java).apply {
                action = ACTION_STOP_MONITORING
            },
            pendingIntentFlags()
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }

        val configuredBuilder = builder
            .setSmallIcon(iconRes)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(contentText)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(openAppIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.notification_action_stop),
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

    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.notification_channel_description)
        }
        notificationManager?.createNotificationChannel(channel)
        LogCompat.d("Notification channel ensured")
    }

    private fun pendingIntentFlags(): Int {
        val baseFlags = PendingIntent.FLAG_UPDATE_CURRENT
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            baseFlags or PendingIntent.FLAG_IMMUTABLE
        } else {
            baseFlags
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).roundToInt()
    }

    private data class GamepadBatterySnapshot(
        val controllerName: String?,
        val batteryPercent: Int?
    )
}
