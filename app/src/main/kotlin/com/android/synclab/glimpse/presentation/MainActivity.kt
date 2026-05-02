package com.android.synclab.glimpse.presentation

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.input.InputManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.widget.ViewPager2
import androidx.recyclerview.widget.RecyclerView
import com.android.synclab.glimpse.R
import com.android.synclab.glimpse.data.model.ControllerProfile
import com.android.synclab.glimpse.di.AppContainer
import com.android.synclab.glimpse.domain.usecase.ClosePs4ControllerLightSessionUseCase
import com.android.synclab.glimpse.domain.usecase.GetControllerProfileUseCase
import com.android.synclab.glimpse.domain.usecase.SetPs4ControllerLightColorUseCase
import com.android.synclab.glimpse.domain.usecase.UpsertControllerProfileUseCase
import com.android.synclab.glimpse.infra.input.InputDeviceGateway
import com.android.synclab.glimpse.presentation.model.EventChangeParam
import com.android.synclab.glimpse.presentation.model.MainUiState
import com.android.synclab.glimpse.presentation.model.SettingItemUiModel
import com.android.synclab.glimpse.presentation.view.CustomizeVibeDialog
import com.android.synclab.glimpse.presentation.view.ControllerPageAdapter
import com.android.synclab.glimpse.presentation.viewmodel.MainViewModel
import com.android.synclab.glimpse.presentation.viewmodel.MainViewModelFactory
import com.android.synclab.glimpse.utils.InputDeviceLogUtils
import com.android.synclab.glimpse.utils.LogCompat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    companion object {
        private const val REFRESH_INTERVAL_MS = 5_000L
        private const val SERVICE_ACTION_STATE_SYNC_DELAY_MS = 350L
        private const val REQUEST_CODE_POST_NOTIFICATIONS = 1001
        private const val REQUEST_CODE_BLUETOOTH_CONNECT = 1002
        private val DEFAULT_VIBE_COLOR = Color.rgb(44, 100, 255)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val profileIoExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private lateinit var toolbar: Toolbar
    private lateinit var controllerPager: ViewPager2
    private lateinit var controllerPageAdapter: ControllerPageAdapter

    private lateinit var inputDeviceGateway: InputDeviceGateway
    private lateinit var setPs4ControllerLightColorUseCase: SetPs4ControllerLightColorUseCase
    private lateinit var closePs4ControllerLightSessionUseCase: ClosePs4ControllerLightSessionUseCase
    private lateinit var getControllerProfileUseCase: GetControllerProfileUseCase
    private lateinit var upsertControllerProfileUseCase: UpsertControllerProfileUseCase
    private lateinit var viewModel: MainViewModel

    private var pendingStartAfterNotificationPermission = false
    private var pendingStartAfterBluetoothPermission = false
    private var protectBatteryEnabled = false
    private var selectedVibeColor: Int = DEFAULT_VIBE_COLOR
    private var activeControllerDescriptor: String? = null
    private var activeControllerName: String? = null
    private var lastLoadedProfileDescriptor: String? = null
    private var customizeVibeDialog: CustomizeVibeDialog? = null

    private val periodicRefresh = object : Runnable {
        override fun run() {
            requestControllerRefresh(EventChangeParam.Source.SYSTEM)
            mainHandler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    private val inputDeviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) {
            logInputDeviceCallback(event = "added", deviceId = deviceId)
            requestControllerRefresh(EventChangeParam.Source.SYSTEM)
        }

        override fun onInputDeviceRemoved(deviceId: Int) {
            logInputDeviceCallback(event = "removed", deviceId = deviceId)
            requestControllerRefresh(EventChangeParam.Source.SYSTEM)
        }

        override fun onInputDeviceChanged(deviceId: Int) {
            logInputDeviceCallback(event = "changed", deviceId = deviceId)
            requestControllerRefresh(EventChangeParam.Source.SYSTEM)
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
        setPs4ControllerLightColorUseCase = appContainer.provideSetPs4ControllerLightColorUseCase()
        closePs4ControllerLightSessionUseCase =
            appContainer.provideClosePs4ControllerLightSessionUseCase()
        getControllerProfileUseCase = appContainer.provideGetControllerProfileUseCase()
        upsertControllerProfileUseCase = appContainer.provideUpsertControllerProfileUseCase()
        viewModel = ViewModelProvider(
            this,
            MainViewModelFactory(
                inputDeviceGateway = inputDeviceGateway,
                getConnectedPs4ControllersUseCase = appContainer.provideConnectedPs4ControllersUseCase(),
                monitoringStateProvider = appContainer.provideMonitoringStateProvider(),
                developerOptionManager = appContainer.provideDeveloperOptionManager()
            )
        ).get(MainViewModel::class.java)

        toolbar = findViewById(R.id.topToolbar)
        controllerPager = findViewById(R.id.controllerPager)
        controllerPageAdapter = ControllerPageAdapter(
            onToggleChanged = { page, itemId, checked ->
                onControllerPageToggleChanged(page.uniqueId, itemId, checked)
            },
            onItemClicked = { page, itemId ->
                onControllerPageItemClicked(page.uniqueId, itemId)
            }
        )

        setupToolbarMenu()
        setupControllerPager()
        bindViewModelObserver()

        renderUiState(viewModel.currentUiState())
        requestControllerRefresh(EventChangeParam.Source.SYSTEM)
    }

    override fun onResume() {
        super.onResume()
        LogCompat.d("onResume")
        val state = viewModel.currentUiState()
        LogCompat.e(
            "LIFECYCLE_MARKER onResume pid=${Process.myPid()} " +
                    "serviceRunning=${state.isServiceRunning} " +
                    "overlayVisible=${state.isOverlayVisible}"
        )
        inputDeviceGateway.registerInputDeviceListener(inputDeviceListener, mainHandler)
        viewModel.syncServiceState(source = EventChangeParam.Source.SYSTEM)
        requestControllerRefresh(EventChangeParam.Source.SYSTEM)
        mainHandler.removeCallbacks(periodicRefresh)
        mainHandler.postDelayed(periodicRefresh, REFRESH_INTERVAL_MS)
    }

    override fun onPause() {
        super.onPause()
        LogCompat.d("onPause")
        inputDeviceGateway.unregisterInputDeviceListener(inputDeviceListener)
        mainHandler.removeCallbacks(periodicRefresh)
    }

    override fun onDestroy() {
        customizeVibeDialog?.dismiss()
        customizeVibeDialog = null
        if (::closePs4ControllerLightSessionUseCase.isInitialized) {
            closePs4ControllerLightSessionUseCase("onDestroy")
        }
        if (::viewModel.isInitialized) {
            viewModel.clearOnViewModelChange()
        }
        profileIoExecutor.shutdownNow()
        super.onDestroy()
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

    private fun setupControllerPager() {
        controllerPager.adapter = controllerPageAdapter
        controllerPager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    val page = controllerPageAdapter.getPageAt(position) ?: return
                    if (page.isPlaceholder) {
                        return
                    }
                    if (viewModel.currentUiState().selectedControllerUniqueId == page.uniqueId) {
                        return
                    }
                    LogCompat.d(
                        "ControllerPager onPageSelected position=$position uniqueId=${maskIdentifier(page.uniqueId)}"
                    )
                    viewModel.selectController(
                        uniqueId = page.uniqueId,
                        source = EventChangeParam.Source.VIEW
                    )
                }
            }
        )
    }

    private fun onControllerPageToggleChanged(
        uniqueId: String,
        itemId: SettingItemUiModel.ItemId,
        checked: Boolean
    ) {
        ensureControllerPageSelected(uniqueId)
        handleSettingToggleChanged(itemId, checked)
    }

    private fun onControllerPageItemClicked(
        uniqueId: String,
        itemId: SettingItemUiModel.ItemId
    ) {
        ensureControllerPageSelected(uniqueId)
        handleSettingItemClicked(itemId)
    }

    private fun ensureControllerPageSelected(uniqueId: String) {
        val currentState = viewModel.currentUiState()
        if (currentState.selectedControllerUniqueId == uniqueId) {
            return
        }
        LogCompat.d(
            "ControllerPager ensureSelected uniqueId=${maskIdentifier(uniqueId)}"
        )
        viewModel.selectController(
            uniqueId = uniqueId,
            source = EventChangeParam.Source.VIEW
        )
    }

    private fun handleSettingToggleChanged(
        itemId: SettingItemUiModel.ItemId,
        checked: Boolean
    ) {
        LogCompat.d("settingToggleChanged id=$itemId checked=$checked")
        when (itemId) {
            SettingItemUiModel.ItemId.BACKGROUND_MONITORING -> {
                if (checked) {
                    startMonitoring()
                } else if (dispatchServiceAction(BatteryOverlayService.ACTION_STOP_MONITORING, false)) {
                    showToast(R.string.toast_monitoring_stopped)
                }
            }

            SettingItemUiModel.ItemId.LIVE_BATTERY_OVERLAY -> {
                if (checked) {
                    if (!Settings.canDrawOverlays(this)) {
                        requestOverlayPermission()
                        showToast(R.string.toast_overlay_permission_required)
                        refreshSettingsStateLater(300L)
                        return
                    }
                    if (dispatchServiceAction(BatteryOverlayService.ACTION_SHOW_OVERLAY, false)) {
                        showToast(R.string.toast_overlay_shown)
                    }
                } else {
                    if (!BatteryOverlayService.isRunning) {
                        LogCompat.d("Ignore hide overlay because service is not running")
                        refreshSettingsStateLater(200L)
                        return
                    }
                    if (dispatchServiceAction(BatteryOverlayService.ACTION_HIDE_OVERLAY, false)) {
                        showToast(R.string.toast_overlay_hidden)
                    }
                }
            }

            SettingItemUiModel.ItemId.PROTECT_BATTERY -> {
                protectBatteryEnabled = checked
            }

            SettingItemUiModel.ItemId.CUSTOMIZE_VIBE -> {
                // No toggle action for this item type.
            }
        }
        refreshSettingsStateLater()
    }

    private fun handleSettingItemClicked(itemId: SettingItemUiModel.ItemId) {
        LogCompat.d("settingItemClicked id=$itemId")
        when (itemId) {
            SettingItemUiModel.ItemId.CUSTOMIZE_VIBE -> {
                showCustomizeVibeDialog()
            }

            SettingItemUiModel.ItemId.BACKGROUND_MONITORING,
            SettingItemUiModel.ItemId.LIVE_BATTERY_OVERLAY,
            SettingItemUiModel.ItemId.PROTECT_BATTERY -> {
                // Toggle rows are handled from onToggleChanged.
            }
        }
    }

    private fun showCustomizeVibeDialog() {
        val activeDialog = customizeVibeDialog
        if (activeDialog?.isShowing == true) {
            LogCompat.d("CustomizeVibeDialog already visible")
            return
        }
        LogCompat.d(
            "CustomizeVibeDialog open " +
                    "activeDescriptor=${activeControllerDescriptor?.let(::maskIdentifier)} " +
                    "selectedColor=${toHexColor(selectedVibeColor)}"
        )

        val dialog = CustomizeVibeDialog(
            context = this,
            initialColor = selectedVibeColor,
            setPs4ControllerLightColorUseCase = setPs4ControllerLightColorUseCase,
            onColorApplied = { color ->
                selectedVibeColor = color
                LogCompat.d(
                    "CustomizeVibeDialog onColorApplied color=${toHexColor(color)} " +
                            "activeDescriptor=${activeControllerDescriptor?.let(::maskIdentifier)}"
                )
                persistControllerProfile(color)
            },
            onDismiss = {
                customizeVibeDialog = null
            }
        )
        customizeVibeDialog = dialog
        dialog.show()
    }

    private fun refreshSettingsStateLater(delayMs: Long = 250L) {
        mainHandler.postDelayed(
            {
                viewModel.syncServiceState(source = EventChangeParam.Source.VIEW)
            },
            delayMs
        )
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
                viewModel.syncServiceState(source = EventChangeParam.Source.VIEW)
                val hasOverlayPermission = Settings.canDrawOverlays(this)
                val state = viewModel.currentUiState()
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
                    requestControllerRefresh(EventChangeParam.Source.VIEW)
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

        mainHandler.postDelayed(
            {
                viewModel.syncServiceState(source = EventChangeParam.Source.VIEW)
                requestControllerRefresh(EventChangeParam.Source.VIEW)
            },
            SERVICE_ACTION_STATE_SYNC_DELAY_MS
        )
        return true
    }

    private fun requestControllerRefresh(
        source: EventChangeParam.Source = EventChangeParam.Source.VIEW
    ) {
        if (!::viewModel.isInitialized) {
            return
        }
        LogCompat.d(
            "requestControllerRefresh source=$source " +
                    "activeDescriptor=${activeControllerDescriptor?.let(::maskIdentifier)} " +
                    "loadedDescriptor=${lastLoadedProfileDescriptor?.let(::maskIdentifier)}"
        )
        viewModel.refreshControllerInfo(
            unknownDeviceName = getString(R.string.unknown_device_name),
            source = source
        )
    }

    private fun logInputDeviceCallback(event: String, deviceId: Int) {
        if (!::inputDeviceGateway.isInitialized) {
            LogCompat.d("InputDeviceListener event=$event deviceId=$deviceId gateway=uninitialized")
            return
        }

        val device = inputDeviceGateway.getInputDevices().firstOrNull { it.id == deviceId }
            ?: InputDevice.getDevice(deviceId)
        if (device == null) {
            val ids = inputDeviceGateway.getInputDevices().joinToString(prefix = "[", postfix = "]") { it.id.toString() }
            LogCompat.d("InputDeviceListener event=$event deviceId=$deviceId device=not_found knownDeviceIds=$ids")
            return
        }

        val supportsGamepad = device.supportsSource(InputDevice.SOURCE_GAMEPAD)
        val supportsJoystick = device.supportsSource(InputDevice.SOURCE_JOYSTICK)
        if (!isDiagnosticInputDeviceLoggingEnabled()) {
            LogCompat.d("InputDeviceListener event=$event deviceId=${device.id}")
            return
        }
        LogCompat.d(
            "InputDeviceListener event=$event " +
                    "deviceId=${device.id} " +
                    "sources=0x${device.sources.toString(16)} " +
                    "supportsGamepad=$supportsGamepad supportsJoystick=$supportsJoystick " +
                    "${InputDeviceLogUtils.buildBatteryInfo(device)}"
        )

        LogCompat.d(
            "InputDeviceDiagnostic event=$event " +
                    "deviceId=${device.id} descriptor=${device.descriptor} " +
                    "vendor=${device.vendorId} product=${device.productId} " +
                    "external=${device.isExternal} virtual=${device.isVirtual} enabled=${device.isEnabled} " +
                    "controllerNumber=${device.controllerNumber} " +
                    "hasMicrophone=${device.hasMicrophone()} " +
                    "keyboardType=${device.keyboardType}(${keyboardTypeLabel(device.keyboardType)})"
        )

        val motionRanges = device.motionRanges.joinToString(separator = "; ") { range ->
            val axisLabel = MotionEvent.axisToString(range.axis)
            "axis=${range.axis}($axisLabel),source=0x${range.source.toString(16)},min=${range.min},max=${range.max},flat=${range.flat},fuzz=${range.fuzz},resolution=${range.resolution}"
        }
        LogCompat.d(
            "InputDeviceMotionRanges deviceId=${device.id} count=${device.motionRanges.size} " +
                    if (motionRanges.isEmpty()) "ranges=[]" else "ranges=[$motionRanges]"
        )

        LogCompat.d("InputDeviceDump begin event=$event deviceId=${device.id}")
        device.toString()
            .lineSequence()
            .filter { it.isNotBlank() }
            .forEach { line ->
                LogCompat.d("InputDeviceDump deviceId=${device.id} $line")
            }
        LogCompat.d("InputDeviceDump end event=$event deviceId=${device.id}")
    }

    private fun isDiagnosticInputDeviceLoggingEnabled(): Boolean {
        return LogCompat.isDebugBuild()
    }

    private fun keyboardTypeLabel(type: Int): String {
        return when (type) {
            InputDevice.KEYBOARD_TYPE_NONE -> "none"
            InputDevice.KEYBOARD_TYPE_NON_ALPHABETIC -> "non_alphabetic"
            InputDevice.KEYBOARD_TYPE_ALPHABETIC -> "alphabetic"
            else -> "unknown"
        }
    }

    private fun bindViewModelObserver() {
        viewModel.setOnViewModelChange { changeParam ->
            renderUiState(changeParam.state)
            LogCompat.d(
                "onViewModelChange event=${changeParam.eventType} " +
                        "source=${changeParam.source} note=${changeParam.note} " +
                        "serviceRunning=${changeParam.state.isServiceRunning} " +
                        "monitoring=${changeParam.state.isMonitoringEnabled} " +
                        "overlayVisible=${changeParam.state.isOverlayVisible}"
            )
        }
    }

    private fun renderUiState(state: MainUiState) {
        if (!::controllerPageAdapter.isInitialized || !::controllerPager.isInitialized) {
            return
        }

        handleControllerProfileState(state)
        controllerPageAdapter.submitState(
            state = state,
            selectedVibeColor = selectedVibeColor,
            protectBatteryEnabled = protectBatteryEnabled
        )
        syncControllerPagerSelection(state)
    }

    private fun syncControllerPagerSelection(state: MainUiState) {
        val selectedUniqueId = state.selectedControllerUniqueId
        val selectedIndex = if (selectedUniqueId.isNullOrBlank()) {
            if (controllerPageAdapter.itemCount > 0) 0 else RecyclerView.NO_POSITION
        } else {
            (0 until controllerPageAdapter.itemCount).firstOrNull { position ->
                controllerPageAdapter.getPageAt(position)?.uniqueId == selectedUniqueId
            } ?: RecyclerView.NO_POSITION
        }

        if (selectedIndex == RecyclerView.NO_POSITION) {
            return
        }

        if (controllerPager.currentItem == selectedIndex) {
            return
        }

        LogCompat.d(
            "ControllerPager syncSelection current=${controllerPager.currentItem} target=$selectedIndex selectedUniqueId=${selectedUniqueId?.let(::maskIdentifier)}"
        )
        controllerPager.setCurrentItem(selectedIndex, false)
    }

    private fun handleControllerProfileState(state: MainUiState) {
        val descriptor = state.controllerDescriptor?.takeIf { it.isNotBlank() }
        val previousDescriptor = activeControllerDescriptor
        if (descriptor != previousDescriptor) {
            LogCompat.d(
                "ControllerProfile active controller changed " +
                        "from=${previousDescriptor?.let(::maskIdentifier)} " +
                        "to=${descriptor?.let(::maskIdentifier)} " +
                        "connectionState=${state.connectionState}"
            )
        }
        activeControllerDescriptor = descriptor
        activeControllerName = state.controllerName?.takeIf { it.isNotBlank() }

        if (descriptor == null) {
            lastLoadedProfileDescriptor = null
            if (previousDescriptor != null) {
                selectedVibeColor = DEFAULT_VIBE_COLOR
                LogCompat.d("ControllerProfile reset to default color because controller disconnected")
            }
            return
        }

        if (descriptor == lastLoadedProfileDescriptor) {
            LogCompat.d(
                "ControllerProfile load skipped id=${maskIdentifier(descriptor)} reason=already_loaded"
            )
            return
        }

        selectedVibeColor = DEFAULT_VIBE_COLOR
        lastLoadedProfileDescriptor = descriptor
        LogCompat.d(
            "ControllerProfile load scheduled id=${maskIdentifier(descriptor)} " +
                    "activeName=${activeControllerName ?: "n/a"}"
        )
        loadControllerProfile(descriptor)
    }

    private fun loadControllerProfile(descriptor: String) {
        LogCompat.d("ControllerProfile load queued id=${maskIdentifier(descriptor)}")
        profileIoExecutor.execute {
            LogCompat.d("ControllerProfile load started id=${maskIdentifier(descriptor)}")
            val profile = runCatching {
                getControllerProfileUseCase(descriptor)
            }.onFailure { throwable ->
                LogCompat.e("ControllerProfile load failed id=${maskIdentifier(descriptor)}", throwable)
            }.getOrNull()

            if (profile != null) {
                LogCompat.i(
                    "ControllerProfile auto-restoring vibe id=${maskIdentifier(descriptor)} " +
                            "color=${toHexColor(profile.lightbarColor)}"
                )
                runCatching {
                    setPs4ControllerLightColorUseCase(profile.lightbarColor)
                }.onFailure { throwable ->
                    LogCompat.e(
                        "ControllerProfile auto-restore failed id=${maskIdentifier(descriptor)}",
                        throwable
                    )
                }
            }

            mainHandler.post {
                if (isDestroyed) {
                    LogCompat.d(
                        "ControllerProfile load ignored id=${maskIdentifier(descriptor)} reason=activity_destroyed"
                    )
                    return@post
                }
                if (activeControllerDescriptor != descriptor) {
                    LogCompat.d(
                        "ControllerProfile load ignored id=${maskIdentifier(descriptor)} " +
                                "reason=active_controller_switched current=${activeControllerDescriptor?.let(::maskIdentifier)}"
                    )
                    return@post
                }
                if (profile == null) {
                    LogCompat.d("ControllerProfile missing id=${maskIdentifier(descriptor)}")
                    return@post
                }
                selectedVibeColor = profile.lightbarColor
                if (activeControllerName.isNullOrBlank()) {
                    activeControllerName = profile.deviceName
                }
                LogCompat.d(
                    "ControllerProfile loaded id=${maskIdentifier(descriptor)} " +
                            "deviceName=${profile.deviceName} color=${toHexColor(profile.lightbarColor)}"
                )
            }
        }
    }

    private fun persistControllerProfile(lightbarColor: Int) {
        val descriptor = activeControllerDescriptor
        if (descriptor.isNullOrBlank()) {
            LogCompat.d("ControllerProfile save skipped: missing descriptor")
            return
        }
        val deviceName = activeControllerName?.takeIf { it.isNotBlank() }
            ?: getString(R.string.unknown_device_name)
        val profile = ControllerProfile(
            id = descriptor,
            deviceName = deviceName,
            lightbarColor = lightbarColor
        )
        LogCompat.d(
            "ControllerProfile save queued id=${maskIdentifier(descriptor)} " +
                    "deviceName=$deviceName color=${toHexColor(lightbarColor)}"
        )
        profileIoExecutor.execute {
            runCatching {
                upsertControllerProfileUseCase(profile)
            }.onSuccess {
                LogCompat.d(
                    "ControllerProfile saved id=${maskIdentifier(descriptor)} " +
                            "deviceName=$deviceName color=${toHexColor(lightbarColor)}"
                )
            }.onFailure { throwable ->
                LogCompat.e("ControllerProfile save failed id=${maskIdentifier(descriptor)}", throwable)
            }
        }
    }

    private fun maskIdentifier(raw: String): String {
        return if (raw.length <= 12) raw else "${raw.take(6)}...${raw.takeLast(6)}"
    }

    private fun toHexColor(color: Int): String {
        return String.format(Locale.US, "#%06X", 0xFFFFFF and color)
    }

    private fun showToast(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }
}
