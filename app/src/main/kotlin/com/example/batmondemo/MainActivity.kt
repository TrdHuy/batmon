package com.example.batmondemo

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.BatteryState
import android.hardware.input.InputManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.text.TextUtils
import android.view.InputDevice
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import android.widget.Toolbar
import com.example.batmondemo.utils.LogCompat
import java.util.Locale

class MainActivity : Activity() {
    companion object {
        private const val SONY_VENDOR_ID = 0x054C
        private val DUALSHOCK4_PRODUCT_IDS = intArrayOf(
            0x05C4, // CUH-ZCT1
            0x09CC  // CUH-ZCT2
        )
        private const val REFRESH_INTERVAL_MS = 5_000L
        private const val REQUEST_CODE_POST_NOTIFICATIONS = 1001
        private const val REQUEST_CODE_BLUETOOTH_CONNECT = 1002
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var inputManager: InputManager? = null

    private lateinit var toolbar: Toolbar
    private lateinit var deviceInfoView: TextView

    private var pendingStartAfterNotificationPermission = false
    private var pendingStartAfterBluetoothPermission = false

    private val periodicRefresh = object : Runnable {
        override fun run() {
            refreshControllerInfo()
            mainHandler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    private val inputDeviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) {
            refreshControllerInfo()
        }

        override fun onInputDeviceRemoved(deviceId: Int) {
            refreshControllerInfo()
        }

        override fun onInputDeviceChanged(deviceId: Int) {
            refreshControllerInfo()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogCompat.i("onCreate")
        LogCompat.e(
            "LIFECYCLE_MARKER onCreate pid=${Process.myPid()} " +
                    "sdk=${Build.VERSION.SDK_INT} package=$packageName"
        )
        setContentView(R.layout.activity_main)

        inputManager = getSystemService(InputManager::class.java)

        toolbar = findViewById(R.id.topToolbar)
        deviceInfoView = findViewById(R.id.deviceInfoView)

        setupToolbarMenu()
        deviceInfoView.setText(R.string.loading_controller_info)
    }

    override fun onResume() {
        super.onResume()
        LogCompat.d("onResume")
        LogCompat.e(
            "LIFECYCLE_MARKER onResume pid=${Process.myPid()} " +
                    "serviceRunning=${BatteryOverlayService.isRunning} " +
                    "overlayVisible=${BatteryOverlayService.isOverlayVisible}"
        )
        inputManager?.registerInputDeviceListener(inputDeviceListener, mainHandler)
        refreshControllerInfo()
        mainHandler.removeCallbacks(periodicRefresh)
        mainHandler.postDelayed(periodicRefresh, REFRESH_INTERVAL_MS)
    }

    override fun onPause() {
        super.onPause()
        LogCompat.d("onPause")
        inputManager?.unregisterInputDeviceListener(inputDeviceListener)
        mainHandler.removeCallbacks(periodicRefresh)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CODE_BLUETOOTH_CONNECT) {
            handleBluetoothPermissionResult(grantResults)
            return
        }

        if (requestCode != REQUEST_CODE_POST_NOTIFICATIONS) {
            return
        }

        val granted = grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            pendingStartAfterNotificationPermission = false
            LogCompat.w("POST_NOTIFICATIONS denied")
            showToast(R.string.toast_notification_permission_required)
            return
        }

        if (pendingStartAfterNotificationPermission) {
            pendingStartAfterNotificationPermission = false
            LogCompat.i("POST_NOTIFICATIONS granted, continue startMonitoring")
            if (shouldRequestBluetoothConnectPermission()) {
                pendingStartAfterBluetoothPermission = true
                requestPermissions(
                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                    REQUEST_CODE_BLUETOOTH_CONNECT
                )
                showToast(R.string.toast_bluetooth_permission_required)
                return
            }

            if (dispatchServiceAction(BatteryOverlayService.ACTION_START_MONITORING, true)) {
                showToast(R.string.toast_monitoring_started)
            }
        }
    }

    private fun setupToolbarMenu() {
        LogCompat.d("setupToolbarMenu")
        toolbar.inflateMenu(R.menu.main_menu)
        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_config) {
                LogCompat.d("Config icon clicked")
                showConfigDialog()
                return@setOnMenuItemClickListener true
            }
            false
        }
    }

    private fun showConfigDialog() {
        runCatching {
            val dialogView = layoutInflater.inflate(R.layout.dialog_config_checkboxes, null)
            val overlayPermissionCheckBox =
                dialogView.findViewById<CheckBox>(R.id.checkboxOverlayPermission)
            val monitoringCheckBox =
                dialogView.findViewById<CheckBox>(R.id.checkboxMonitoring)
            val floatingOverlayCheckBox =
                dialogView.findViewById<CheckBox>(R.id.checkboxFloatingOverlay)

            var syncing = false
            fun syncState() {
                syncing = true
                val hasOverlayPermission = Settings.canDrawOverlays(this)
                overlayPermissionCheckBox.isChecked = hasOverlayPermission
                monitoringCheckBox.isChecked = BatteryOverlayService.isRunning
                floatingOverlayCheckBox.isEnabled = hasOverlayPermission
                floatingOverlayCheckBox.isChecked = BatteryOverlayService.isOverlayVisible
                syncing = false
                LogCompat.d(
                    "Config dialog sync: overlayPermission=$hasOverlayPermission " +
                            "monitoring=${BatteryOverlayService.isRunning} " +
                            "overlayVisible=${BatteryOverlayService.isOverlayVisible}"
                )
            }

            overlayPermissionCheckBox.setOnCheckedChangeListener { _, isChecked ->
                if (syncing) {
                    return@setOnCheckedChangeListener
                }
                LogCompat.i("Config checkbox changed: overlayPermission=$isChecked")
                if (isChecked && !Settings.canDrawOverlays(this)) {
                    requestOverlayPermission()
                } else if (!isChecked && Settings.canDrawOverlays(this)) {
                    showToast(R.string.toast_overlay_permission_managed_by_system)
                }
                mainHandler.postDelayed({ syncState() }, 250L)
            }

            monitoringCheckBox.setOnCheckedChangeListener { _, isChecked ->
                if (syncing) {
                    return@setOnCheckedChangeListener
                }
                LogCompat.i("Config checkbox changed: monitoring=$isChecked")
                if (isChecked) {
                    startMonitoring()
                } else if (dispatchServiceAction(BatteryOverlayService.ACTION_STOP_MONITORING, false)) {
                    showToast(R.string.toast_monitoring_stopped)
                }
                mainHandler.postDelayed({ syncState() }, 350L)
            }

            floatingOverlayCheckBox.setOnCheckedChangeListener { _, isChecked ->
                if (syncing) {
                    return@setOnCheckedChangeListener
                }
                LogCompat.i("Config checkbox changed: floatingOverlay=$isChecked")
                if (isChecked) {
                    if (!Settings.canDrawOverlays(this)) {
                        requestOverlayPermission()
                        showToast(R.string.toast_overlay_permission_required)
                        mainHandler.postDelayed({ syncState() }, 250L)
                        return@setOnCheckedChangeListener
                    }

                    val foreground = !BatteryOverlayService.isRunning
                    if (foreground && !ensureForegroundPermissions(autoResumeStartMonitoring = false)) {
                        mainHandler.postDelayed({ syncState() }, 250L)
                        return@setOnCheckedChangeListener
                    }

                    if (dispatchServiceAction(BatteryOverlayService.ACTION_SHOW_OVERLAY, foreground)) {
                        showToast(R.string.toast_overlay_shown)
                    }
                } else {
                    if (!BatteryOverlayService.isRunning) {
                        LogCompat.d("Ignore hide overlay because service is not running")
                        mainHandler.postDelayed({ syncState() }, 250L)
                        return@setOnCheckedChangeListener
                    }
                    val foreground = !BatteryOverlayService.isRunning
                    if (foreground && !ensureForegroundPermissions(autoResumeStartMonitoring = false)) {
                        mainHandler.postDelayed({ syncState() }, 250L)
                        return@setOnCheckedChangeListener
                    }

                    if (dispatchServiceAction(BatteryOverlayService.ACTION_HIDE_OVERLAY, foreground)) {
                        showToast(R.string.toast_overlay_hidden)
                    }
                }
                mainHandler.postDelayed({ syncState() }, 350L)
            }

            syncState()

            AlertDialog.Builder(this)
                .setTitle(R.string.menu_config)
                .setView(dialogView)
                .setPositiveButton(R.string.config_dialog_close, null)
                .setOnDismissListener {
                    refreshControllerInfo()
                }
                .show()
            LogCompat.d("Config dialog shown")
        }.onFailure { throwable ->
            LogCompat.e("Failed to show config dialog", throwable)
            showToast(R.string.toast_action_failed)
        }
    }

    private fun requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            LogCompat.d("Overlay permission already granted")
            showToast(R.string.toast_overlay_permission_already_granted)
            return
        }

        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        runCatching {
            startActivity(intent)
            LogCompat.i("Opened overlay permission screen")
            showToast(R.string.toast_overlay_permission_requested)
        }.onFailure { throwable ->
            LogCompat.e("Failed to open overlay permission screen", throwable)
            showToast(R.string.toast_action_failed)
        }
    }

    private fun startMonitoring() {
        LogCompat.i("startMonitoring invoked")
        if (!ensureForegroundPermissions(autoResumeStartMonitoring = true)) {
            return
        }

        if (dispatchServiceAction(BatteryOverlayService.ACTION_START_MONITORING, true)) {
            showToast(R.string.toast_monitoring_started)
        }
    }

    private fun shouldRequestNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return false
        }
        return checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
    }

    private fun shouldRequestBluetoothConnectPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return false
        }
        return checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) !=
                PackageManager.PERMISSION_GRANTED
    }

    private fun ensureForegroundPermissions(autoResumeStartMonitoring: Boolean): Boolean {
        if (shouldRequestNotificationPermission()) {
            LogCompat.w("POST_NOTIFICATIONS not granted")
            pendingStartAfterNotificationPermission = autoResumeStartMonitoring
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_CODE_POST_NOTIFICATIONS
            )
            if (!autoResumeStartMonitoring) {
                showToast(R.string.toast_notification_permission_required)
            }
            return false
        }

        if (shouldRequestBluetoothConnectPermission()) {
            LogCompat.w("BLUETOOTH_CONNECT not granted")
            pendingStartAfterBluetoothPermission = autoResumeStartMonitoring
            requestPermissions(
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                REQUEST_CODE_BLUETOOTH_CONNECT
            )
            showToast(R.string.toast_bluetooth_permission_required)
            return false
        }

        return true
    }

    private fun handleBluetoothPermissionResult(grantResults: IntArray) {
        val granted = grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            pendingStartAfterBluetoothPermission = false
            LogCompat.w("BLUETOOTH_CONNECT denied")
            showToast(R.string.toast_bluetooth_permission_required)
            return
        }

        if (pendingStartAfterBluetoothPermission) {
            pendingStartAfterBluetoothPermission = false
            LogCompat.i("BLUETOOTH_CONNECT granted, continue startMonitoring")
            if (dispatchServiceAction(BatteryOverlayService.ACTION_START_MONITORING, true)) {
                showToast(R.string.toast_monitoring_started)
            }
        }
    }

    private fun dispatchServiceAction(action: String, foreground: Boolean): Boolean {
        val intent = Intent(this, BatteryOverlayService::class.java).apply {
            this.action = action
        }

        val result = runCatching {
            if (foreground && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                LogCompat.i("startForegroundService action=$action")
                startForegroundService(intent)
            } else {
                LogCompat.i("startService action=$action")
                startService(intent)
            }
        }

        result.exceptionOrNull()?.let { throwable ->
            LogCompat.e("Failed to dispatch service action=$action", throwable)
            showToast(R.string.toast_action_failed)
            return false
        }

        refreshControllerInfo()
        return true
    }

    private fun refreshControllerInfo() {
        if (!::deviceInfoView.isInitialized) {
            return
        }

        val monitorState = if (BatteryOverlayService.isRunning) {
            getString(R.string.monitor_service_running)
        } else {
            getString(R.string.monitor_service_stopped)
        }
        val overlayState = if (BatteryOverlayService.isOverlayVisible) {
            getString(R.string.overlay_state_visible)
        } else {
            getString(R.string.overlay_state_hidden)
        }

        val sections = mutableListOf<String>()
        sections.add("$monitorState\n$overlayState")

        val manager = inputManager
        if (manager == null) {
            sections.add(getString(R.string.input_manager_unavailable))
            deviceInfoView.text = TextUtils.join("\n\n", sections)
            return
        }

        val ps4Controllers = mutableListOf<String>()
        for (deviceId in manager.inputDeviceIds) {
            val device = manager.getInputDevice(deviceId) ?: continue
            if (!isGamepad(device) || !isPs4Controller(device)) {
                continue
            }
            ps4Controllers.add(formatControllerInfo(device))
        }

        if (ps4Controllers.isEmpty()) {
            sections.add(getString(R.string.no_ps4_controller_connected))
        } else {
            sections.add(TextUtils.join("\n\n", ps4Controllers))
        }

        LogCompat.d("refreshControllerInfo controllers=${ps4Controllers.size} serviceRunning=${BatteryOverlayService.isRunning} overlayVisible=${BatteryOverlayService.isOverlayVisible}")
        deviceInfoView.text = TextUtils.join("\n\n", sections)
    }

    private fun isGamepad(device: InputDevice): Boolean {
        return device.supportsSource(InputDevice.SOURCE_GAMEPAD) ||
                device.supportsSource(InputDevice.SOURCE_JOYSTICK)
    }

    private fun isPs4Controller(device: InputDevice): Boolean {
        val vendorId = device.vendorId
        val productId = device.productId
        if (vendorId == SONY_VENDOR_ID && isDualShock4ProductId(productId)) {
            return true
        }

        val lowerName = (device.name ?: "").lowercase(Locale.US)
        if (lowerName.contains("dualshock")) {
            return true
        }

        // Some DS4 connections expose vendor but miss product id.
        return vendorId == SONY_VENDOR_ID && productId == 0 &&
                lowerName.contains("wireless controller")
    }

    private fun isDualShock4ProductId(productId: Int): Boolean {
        return DUALSHOCK4_PRODUCT_IDS.contains(productId)
    }

    private fun formatControllerInfo(device: InputDevice): String {
        val deviceName = device.name ?: getString(R.string.unknown_device_name)
        return buildString {
            append(getString(R.string.controller_name_line, deviceName))
            append("\n")
            append(
                getString(
                    R.string.controller_status_line,
                    getString(R.string.controller_status_connected)
                )
            )
            append("\n")
            append(getString(R.string.controller_battery_line, readBatteryPercent(device)))
            append("\n")
            append(
                getString(
                    R.string.controller_vendor_product_line,
                    hex4(device.vendorId),
                    hex4(device.productId)
                )
            )
            append("\n")
            append(getString(R.string.controller_device_id_line, device.id))
        }
    }

    private fun readBatteryPercent(device: InputDevice): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return getString(R.string.battery_api_not_supported)
        }

        return try {
            val batteryState: BatteryState = device.batteryState ?: return getString(R.string.battery_unavailable)
            if (!batteryState.isPresent) {
                return getString(R.string.battery_unavailable)
            }

            val capacity = batteryState.capacity
            if (capacity.isNaN() || capacity < 0f) {
                return getString(R.string.battery_unavailable)
            }

            val normalized = if (capacity > 1.0f) capacity else capacity * 100f
            val percentage = normalized.toInt().coerceIn(0, 100)
            getString(
                R.string.battery_percentage_format,
                percentage,
                batteryStatusLabel(batteryState.status)
            )
        } catch (exception: Exception) {
            LogCompat.w("Failed to read battery state from InputDevice", exception)
            getString(R.string.battery_unavailable)
        }
    }

    private fun batteryStatusLabel(status: Int): String {
        return when (status) {
            BatteryState.STATUS_CHARGING -> getString(R.string.battery_status_charging)
            BatteryState.STATUS_DISCHARGING -> getString(R.string.battery_status_discharging)
            BatteryState.STATUS_FULL -> getString(R.string.battery_status_full)
            BatteryState.STATUS_NOT_CHARGING -> getString(R.string.battery_status_not_charging)
            else -> getString(R.string.battery_status_unknown)
        }
    }

    private fun hex4(value: Int): String {
        return String.format(Locale.US, "0x%04X", value and 0xFFFF)
    }

    private fun showToast(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }
}
