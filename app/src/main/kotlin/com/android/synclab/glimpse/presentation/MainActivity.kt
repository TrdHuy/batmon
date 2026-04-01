package com.android.synclab.glimpse.presentation

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.input.InputManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.ViewModelProvider
import com.android.synclab.glimpse.R
import com.android.synclab.glimpse.data.model.BatteryChargeStatus
import com.android.synclab.glimpse.di.AppContainer
import com.android.synclab.glimpse.infra.input.InputDeviceGateway
import com.android.synclab.glimpse.presentation.model.MainUiAction
import com.android.synclab.glimpse.presentation.model.MainUiState
import com.android.synclab.glimpse.presentation.viewmodel.MainViewModel
import com.android.synclab.glimpse.presentation.viewmodel.MainViewModelFactory
import com.android.synclab.glimpse.utils.LogCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.progressindicator.CircularProgressIndicator

class MainActivity : AppCompatActivity() {
    companion object {
        private const val REFRESH_INTERVAL_MS = 5_000L
        private const val REQUEST_CODE_POST_NOTIFICATIONS = 1001
        private const val REQUEST_CODE_BLUETOOTH_CONNECT = 1002
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var toolbar: Toolbar
    private lateinit var deviceInfoView: TextView
    private lateinit var batteryPercentText: TextView
    private lateinit var batteryStateText: TextView
    private lateinit var batteryCircle: CircularProgressIndicator
    private lateinit var bottomNav: BottomNavigationView

    private lateinit var inputDeviceGateway: InputDeviceGateway
    private lateinit var viewModel: MainViewModel

    private var pendingStartAfterNotificationPermission = false
    private var pendingStartAfterBluetoothPermission = false

    private val periodicRefresh = object : Runnable {
        override fun run() {
            requestControllerRefresh()
            mainHandler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    private val inputDeviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) {
            requestControllerRefresh()
        }

        override fun onInputDeviceRemoved(deviceId: Int) {
            requestControllerRefresh()
        }

        override fun onInputDeviceChanged(deviceId: Int) {
            requestControllerRefresh()
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

        val appContainer = AppContainer.from(applicationContext)
        inputDeviceGateway = appContainer.provideInputDeviceGateway()
        viewModel = ViewModelProvider(
            this,
            MainViewModelFactory(
                inputDeviceGateway = inputDeviceGateway,
                getConnectedPs4ControllersUseCase = appContainer.provideConnectedPs4ControllersUseCase()
            )
        ).get(MainViewModel::class.java)

        toolbar = findViewById(R.id.topToolbar)
        deviceInfoView = findViewById(R.id.deviceInfoView)
        batteryPercentText = findViewById(R.id.batteryPercentText)
        batteryStateText = findViewById(R.id.batteryStateText)
        batteryCircle = findViewById(R.id.batteryCircle)
        bottomNav = findViewById(R.id.bottomNav)

        setupToolbarMenu()
        setupBottomNav()
        observeUiState()

        renderUiState(viewModel.uiState.value ?: MainUiState())
        requestControllerRefresh()
    }

    override fun onResume() {
        super.onResume()
        LogCompat.d("onResume")
        val state = viewModel.uiState.value
        LogCompat.e(
            "LIFECYCLE_MARKER onResume pid=${Process.myPid()} " +
                    "serviceRunning=${state?.isServiceRunning ?: BatteryOverlayService.isRunning} " +
                    "overlayVisible=${state?.isOverlayVisible ?: BatteryOverlayService.isOverlayVisible}"
        )
        inputDeviceGateway.registerInputDeviceListener(inputDeviceListener, mainHandler)
        viewModel.onAction(MainUiAction.SyncServiceState)
        requestControllerRefresh()
        mainHandler.removeCallbacks(periodicRefresh)
        mainHandler.postDelayed(periodicRefresh, REFRESH_INTERVAL_MS)
    }

    override fun onPause() {
        super.onPause()
        LogCompat.d("onPause")
        inputDeviceGateway.unregisterInputDeviceListener(inputDeviceListener)
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

    private fun setupBottomNav() {
        bottomNav.selectedItemId = R.id.nav_home
        bottomNav.setOnItemSelectedListener { item ->
            LogCompat.d("BottomNav selected item=${item.itemId}")
            true
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
                viewModel.onAction(MainUiAction.SyncServiceState)
                val hasOverlayPermission = Settings.canDrawOverlays(this)
                val state = viewModel.uiState.value ?: MainUiState()
                overlayPermissionCheckBox.isChecked = hasOverlayPermission
                monitoringCheckBox.isChecked = state.isMonitoringEnabled
                floatingOverlayCheckBox.isEnabled = hasOverlayPermission
                floatingOverlayCheckBox.isChecked = state.isOverlayVisible
                syncing = false
                LogCompat.d(
                    "Config dialog sync: overlayPermission=$hasOverlayPermission " +
                            "monitoring=${state.isMonitoringEnabled} " +
                            "overlayVisible=${state.isOverlayVisible}"
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

                    if (dispatchServiceAction(BatteryOverlayService.ACTION_SHOW_OVERLAY, false)) {
                        showToast(R.string.toast_overlay_shown)
                    }
                } else {
                    if (!BatteryOverlayService.isRunning) {
                        LogCompat.d("Ignore hide overlay because service is not running")
                        mainHandler.postDelayed({ syncState() }, 250L)
                        return@setOnCheckedChangeListener
                    }

                    if (dispatchServiceAction(BatteryOverlayService.ACTION_HIDE_OVERLAY, false)) {
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
                    requestControllerRefresh()
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

        viewModel.onAction(MainUiAction.SyncServiceState)
        requestControllerRefresh()
        return true
    }

    private fun requestControllerRefresh() {
        if (!::viewModel.isInitialized) {
            return
        }
        viewModel.onAction(
            MainUiAction.RefreshControllerInfo(
                unknownDeviceName = getString(R.string.unknown_device_name)
            )
        )
    }

    private fun observeUiState() {
        viewModel.uiState.observe(this) { state ->
            renderUiState(state)
        }
    }

    private fun renderUiState(state: MainUiState) {
        if (!::deviceInfoView.isInitialized || !::batteryCircle.isInitialized) {
            return
        }

        when (state.connectionState) {
            MainUiState.ConnectionState.LOADING -> {
                deviceInfoView.setText(R.string.loading_controller_info)
            }

            MainUiState.ConnectionState.INPUT_MANAGER_UNAVAILABLE -> {
                deviceInfoView.setText(R.string.input_manager_unavailable)
            }

            MainUiState.ConnectionState.DISCONNECTED -> {
                deviceInfoView.setText(R.string.status_card_controller_disconnected)
            }

            MainUiState.ConnectionState.CONNECTED -> {
                deviceInfoView.setText(R.string.status_card_controller_connected)
            }
        }

        updateBatteryUi(state.batteryPercent, state.batteryStatus)
    }

    private fun updateBatteryUi(percent: Int?, status: BatteryChargeStatus) {
        if (percent == null) {
            batteryPercentText.setText(R.string.status_card_battery_unknown)
            batteryCircle.setProgressCompat(0, false)
        } else {
            val clampedPercent = percent.coerceIn(0, 100)
            batteryPercentText.text =
                getString(R.string.status_card_battery_percent, clampedPercent)
            batteryCircle.setProgressCompat(clampedPercent, false)
        }
        batteryStateText.text = batteryStatusLabel(status)
    }

    private fun batteryStatusLabel(status: BatteryChargeStatus): String {
        return when (status) {
            BatteryChargeStatus.CHARGING -> getString(R.string.battery_status_charging)
            BatteryChargeStatus.DISCHARGING -> getString(R.string.battery_status_discharging)
            BatteryChargeStatus.FULL -> getString(R.string.battery_status_full)
            BatteryChargeStatus.NOT_CHARGING -> getString(R.string.battery_status_not_charging)
            BatteryChargeStatus.UNKNOWN -> getString(R.string.battery_status_unknown)
        }
    }

    private fun showToast(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }
}
