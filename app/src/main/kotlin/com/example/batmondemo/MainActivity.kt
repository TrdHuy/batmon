package com.example.batmondemo

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.BatteryState
import android.hardware.input.InputManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.InputDevice
import android.view.MenuItem
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import android.widget.Toolbar
import java.util.Locale

class MainActivity : Activity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val SONY_VENDOR_ID = 0x054C
        private val DUALSHOCK4_PRODUCT_IDS = intArrayOf(
            0x05C4, // CUH-ZCT1
            0x09CC  // CUH-ZCT2
        )
        private const val REFRESH_INTERVAL_MS = 5_000L
        private const val REQUEST_CODE_POST_NOTIFICATIONS = 1001
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var inputManager: InputManager? = null

    private lateinit var toolbar: Toolbar
    private lateinit var deviceInfoView: TextView

    private var pendingStartAfterNotificationPermission = false

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
        setContentView(R.layout.activity_main)

        inputManager = getSystemService(InputManager::class.java)

        toolbar = findViewById(R.id.topToolbar)
        deviceInfoView = findViewById(R.id.deviceInfoView)

        setupToolbarMenu()
        deviceInfoView.setText(R.string.loading_controller_info)
    }

    override fun onResume() {
        super.onResume()
        inputManager?.registerInputDeviceListener(inputDeviceListener, mainHandler)
        refreshControllerInfo()
        mainHandler.removeCallbacks(periodicRefresh)
        mainHandler.postDelayed(periodicRefresh, REFRESH_INTERVAL_MS)
    }

    override fun onPause() {
        super.onPause()
        inputManager?.unregisterInputDeviceListener(inputDeviceListener)
        mainHandler.removeCallbacks(periodicRefresh)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode != REQUEST_CODE_POST_NOTIFICATIONS) {
            return
        }

        val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            pendingStartAfterNotificationPermission = false
            showToast(R.string.toast_notification_permission_required)
            return
        }

        if (pendingStartAfterNotificationPermission) {
            pendingStartAfterNotificationPermission = false
            dispatchServiceAction(BatteryOverlayService.ACTION_START_MONITORING, true)
            showToast(R.string.toast_monitoring_started)
        }
    }

    private fun setupToolbarMenu() {
        toolbar.inflateMenu(R.menu.main_menu)
        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_config) {
                showConfigMenu()
                return@setOnMenuItemClickListener true
            }
            false
        }
    }

    private fun showConfigMenu() {
        val popupMenu = PopupMenu(this, toolbar, Gravity.END)
        popupMenu.menuInflater.inflate(R.menu.config_menu, popupMenu.menu)
        popupMenu.setOnMenuItemClickListener { item -> handleConfigAction(item) }
        popupMenu.show()
    }

    private fun handleConfigAction(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_grant_overlay_permission -> {
                requestOverlayPermission()
                return true
            }

            R.id.action_start_monitoring -> {
                startMonitoring()
                return true
            }

            R.id.action_show_overlay -> {
                dispatchServiceAction(BatteryOverlayService.ACTION_SHOW_OVERLAY, true)
                showToast(R.string.toast_overlay_shown)
                return true
            }

            R.id.action_hide_overlay -> {
                dispatchServiceAction(BatteryOverlayService.ACTION_HIDE_OVERLAY, true)
                showToast(R.string.toast_overlay_hidden)
                return true
            }

            R.id.action_stop_monitoring -> {
                dispatchServiceAction(BatteryOverlayService.ACTION_STOP_MONITORING, false)
                showToast(R.string.toast_monitoring_stopped)
                return true
            }

            else -> return false
        }
    }

    private fun requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            showToast(R.string.toast_overlay_permission_already_granted)
            return
        }

        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
        showToast(R.string.toast_overlay_permission_requested)
    }

    private fun startMonitoring() {
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
            showToast(R.string.toast_overlay_permission_required)
            return
        }

        if (shouldRequestNotificationPermission()) {
            pendingStartAfterNotificationPermission = true
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_CODE_POST_NOTIFICATIONS
            )
            return
        }

        dispatchServiceAction(BatteryOverlayService.ACTION_START_MONITORING, true)
        showToast(R.string.toast_monitoring_started)
    }

    private fun shouldRequestNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return false
        }
        return checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
    }

    private fun dispatchServiceAction(action: String, foreground: Boolean) {
        val intent = Intent(this, BatteryOverlayService::class.java).apply {
            this.action = action
        }

        if (foreground && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        refreshControllerInfo()
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

        if (inputManager == null) {
            sections.add(getString(R.string.input_manager_unavailable))
            deviceInfoView.text = TextUtils.join("\n\n", sections)
            return
        }

        val ps4Controllers = mutableListOf<String>()
        for (deviceId in inputManager!!.inputDeviceIds) {
            val device = inputManager!!.getInputDevice(deviceId) ?: continue
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
            Log.w(TAG, "Failed to read battery state from InputDevice", exception)
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
