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
import com.android.synclab.glimpse.presentation.feature.BackgroundMonitoringPlanner
import com.android.synclab.glimpse.presentation.feature.LiveBatteryOverlayPlanner
import com.android.synclab.glimpse.presentation.model.ControllerPageUiModel
import com.android.synclab.glimpse.presentation.model.EventChangeParam
import com.android.synclab.glimpse.presentation.model.MainUiState
import com.android.synclab.glimpse.presentation.model.PendingBackgroundMonitoringStart
import com.android.synclab.glimpse.presentation.model.SettingItemUiModel
import com.android.synclab.glimpse.presentation.view.CustomizeVibeDialog
import com.android.synclab.glimpse.presentation.view.ControllerPageAdapter
import com.android.synclab.glimpse.presentation.view.SettingsPanelView
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
    private lateinit var utilSettingsPanel: SettingsPanelView
    private lateinit var otherSettingsPanel: SettingsPanelView

    private lateinit var inputDeviceGateway: InputDeviceGateway
    private lateinit var setPs4ControllerLightColorUseCase: SetPs4ControllerLightColorUseCase
    private lateinit var closePs4ControllerLightSessionUseCase: ClosePs4ControllerLightSessionUseCase
    private lateinit var getControllerProfileUseCase: GetControllerProfileUseCase
    private lateinit var upsertControllerProfileUseCase: UpsertControllerProfileUseCase
    private lateinit var viewModel: MainViewModel

    private val backgroundMonitoringPlanner = BackgroundMonitoringPlanner()
    private val liveBatteryOverlayPlanner = LiveBatteryOverlayPlanner()

    private var pendingStartAfterNotificationPermission = false
    private var pendingStartAfterBluetoothPermission = false
    private var pendingBackgroundMonitoringStart: PendingBackgroundMonitoringStart? = null
    private var protectBatteryEnabled = false
    private var selectedVibeColor: Int = DEFAULT_VIBE_COLOR
    private var selectedBackgroundMonitoringEnabled = false
    private var selectedLiveBatteryOverlayEnabled = false
    private var activeControllerDescriptor: String? = null
    private var activeControllerUniqueId: String? = null
    private var activeControllerName: String? = null
    private var lastLoadedProfileDescriptor: String? = null
    private var customizeVibeDialog: CustomizeVibeDialog? = null
    private var hasLoggedFixedSettingsLayout = false
    @Volatile
    private var controllerProfileGeneration: Long = 0L

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
        utilSettingsPanel = findViewById(R.id.utilSettingsPanel)
        otherSettingsPanel = findViewById(R.id.otherSettingsPanel)
        controllerPageAdapter = ControllerPageAdapter()

        setupToolbarMenu()
        setupControllerPager()
        setupFixedSettingsPanels()
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
            pendingBackgroundMonitoringStart = null
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

            if (resumePendingBackgroundMonitoringStart("post_notifications_granted")) {
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
                    LogCompat.dDebug {
                        "UI_VERIFY ControllerPager statusOnly onPageSelected " +
                                "position=$position uniqueId=${maskIdentifier(page.uniqueId)} " +
                                "placeholder=${page.isPlaceholder}"
                    }
                    controllerPager.post {
                        logControllerPagerBounds("onPageSelected:$position")
                    }
                    if (page.isPlaceholder) {
                        return
                    }
                    if (viewModel.currentUiState().selectedControllerUniqueId == page.uniqueId) {
                        return
                    }
                    controllerPager.post {
                        val currentPage = controllerPageAdapter.getPageAt(controllerPager.currentItem)
                            ?: return@post
                        if (controllerPager.currentItem != position ||
                            currentPage.uniqueId != page.uniqueId ||
                            viewModel.currentUiState().selectedControllerUniqueId == currentPage.uniqueId
                        ) {
                            return@post
                        }
                        LogCompat.dDebug {
                            "ControllerPager onPageSelected position=$position uniqueId=${maskIdentifier(page.uniqueId)}"
                        }
                        viewModel.selectController(
                            uniqueId = currentPage.uniqueId,
                            source = EventChangeParam.Source.VIEW
                        )
                    }
                }

                override fun onPageScrollStateChanged(state: Int) {
                    if (state != ViewPager2.SCROLL_STATE_IDLE) {
                        return
                    }
                    controllerPager.post {
                        logControllerPagerBounds("scrollIdle:${controllerPager.currentItem}")
                    }
                }
            }
        )
    }

    private fun setupFixedSettingsPanels() {
        utilSettingsPanel.setInteractionHandlers(
            onToggleChanged = { itemId, checked ->
                onFixedSettingToggleChanged(itemId, checked)
            },
            onItemClicked = { itemId ->
                onFixedSettingItemClicked(itemId)
            }
        )
        otherSettingsPanel.setInteractionHandlers(
            onToggleChanged = { itemId, checked ->
                onFixedSettingToggleChanged(itemId, checked)
            },
            onItemClicked = { itemId ->
                onFixedSettingItemClicked(itemId)
            }
        )
        controllerPager.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (!hasLoggedFixedSettingsLayout) {
                hasLoggedFixedSettingsLayout = true
                logFixedSettingsLayout("firstLayout")
                logControllerPagerBounds("firstLayout")
            }
        }
        mainHandler.post {
            logFixedSettingsLayout("postSetup")
            logControllerPagerBounds("postSetup")
        }
        mainHandler.postDelayed({
            logFixedSettingsLayout("postLayout")
            logControllerPagerBounds("postLayout")
        }, 500L)
    }

    private fun onFixedSettingToggleChanged(
        itemId: SettingItemUiModel.ItemId,
        checked: Boolean
    ) {
        val page = resolveCurrentControllerPage()
        LogCompat.dDebug {
            "UI_VERIFY FixedSettings toggle " +
                    "item=$itemId checked=$checked currentItem=${controllerPager.currentItem} " +
                    "resolvedPage=${page?.uniqueId?.let(::maskIdentifier) ?: "none"} " +
                    "placeholder=${page?.isPlaceholder}"
        }
        if (page != null && !page.isPlaceholder) {
            ensureControllerPageSelected(page.uniqueId)
        }
        handleSettingToggleChanged(itemId, checked)
    }

    private fun onFixedSettingItemClicked(
        itemId: SettingItemUiModel.ItemId
    ) {
        val page = resolveCurrentControllerPage()
        LogCompat.dDebug {
            "UI_VERIFY FixedSettings click " +
                    "item=$itemId currentItem=${controllerPager.currentItem} " +
                    "resolvedPage=${page?.uniqueId?.let(::maskIdentifier) ?: "none"} " +
                    "placeholder=${page?.isPlaceholder}"
        }
        if (page != null && !page.isPlaceholder) {
            ensureControllerPageSelected(page.uniqueId)
        }
        handleSettingItemClicked(itemId)
    }

    private fun ensureControllerPageSelected(uniqueId: String) {
        val currentState = viewModel.currentUiState()
        if (currentState.selectedControllerUniqueId == uniqueId) {
            return
        }
        LogCompat.dDebug {
            "ControllerPager ensureSelected uniqueId=${maskIdentifier(uniqueId)}"
        }
        viewModel.selectController(
            uniqueId = uniqueId,
            source = EventChangeParam.Source.VIEW
        )
    }

    private fun handleSettingToggleChanged(
        itemId: SettingItemUiModel.ItemId,
        checked: Boolean
    ) {
        LogCompat.dDebug { "settingToggleChanged id=$itemId checked=$checked" }
        when (itemId) {
            SettingItemUiModel.ItemId.BACKGROUND_MONITORING -> {
                val applied = handleBackgroundMonitoringToggle(
                    enabled = checked,
                    reason = "fixed_settings"
                )
                if (applied) {
                    showToast(
                        if (checked) R.string.toast_monitoring_started else R.string.toast_monitoring_stopped
                    )
                }
            }

            SettingItemUiModel.ItemId.LIVE_BATTERY_OVERLAY -> {
                val applied = handleLiveBatteryOverlayToggle(
                    enabled = checked,
                    reason = "fixed_settings"
                )
                if (applied) {
                    showToast(
                        if (checked) R.string.toast_overlay_shown else R.string.toast_overlay_hidden
                    )
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

    private fun handleBackgroundMonitoringToggle(
        enabled: Boolean,
        reason: String
    ): Boolean {
        val state = viewModel.currentUiState()
        LogCompat.dDebug {
            "UI_VERIFY BM toggle " +
                    "reason=$reason enabled=$enabled " +
                    "profileId=${state.controllerPersistentId?.let(::maskIdentifier) ?: "none"} " +
                    "runtimeId=${state.controllerUniqueId?.let(::maskIdentifier) ?: "none"} " +
                    "serviceRunning=${BatteryOverlayService.isRunning} " +
                    "monitoring=${BatteryOverlayService.isMonitoringEnabled}"
        }
        val decision = backgroundMonitoringPlanner.planUserToggle(
            enabled = enabled,
            state = buildBackgroundMonitoringState(
                profileId = state.controllerPersistentId,
                controllerIdentifier = state.controllerUniqueId
            ),
            reason = reason
        )
        return executeBackgroundMonitoringDecision(
            decision = decision,
            state = state,
            reason = reason
        )
    }

    private fun executeBackgroundMonitoringDecision(
        decision: BackgroundMonitoringPlanner.Decision,
        state: MainUiState,
        reason: String
    ): Boolean {
        when (decision) {
            is BackgroundMonitoringPlanner.Decision.Start -> {
                pendingBackgroundMonitoringStart = decision.pendingStart
                return dispatchAndApplyBackgroundMonitoringStart(
                    state = state,
                    profileId = decision.pendingStart.profileId.orEmpty(),
                    controllerIdentifier = decision.pendingStart.controllerIdentifier.orEmpty(),
                    pendingStart = decision.pendingStart,
                    reason = reason
                )
            }

            is BackgroundMonitoringPlanner.Decision.Stop -> {
                selectedBackgroundMonitoringEnabled = decision.selectedEnabled
                if (decision.clearPending) {
                    pendingBackgroundMonitoringStart = null
                }
                bindFixedSettingsPanel(state)

                val dispatched = if (decision.shouldDispatchStop) {
                    dispatchServiceAction(
                        action = BatteryOverlayService.ACTION_STOP_MONITORING,
                        foreground = false
                    )
                } else {
                    LogCompat.dDebug {
                        "UI_VERIFY BM applySelected reason=$reason action=stop_skipped monitoring_idle"
                    }
                    true
                }
                if (dispatched && decision.persistProfileId != null) {
                    persistControllerProfile(
                        lightbarColor = selectedVibeColor,
                        targetId = decision.persistProfileId,
                        backgroundMonitoringEnabled = false,
                        reason = "bm_$reason"
                    )
                }
                refreshSettingsStateLater()
                return dispatched
            }

            is BackgroundMonitoringPlanner.Decision.RequestPermission -> {
                decision.selectedEnabled?.let {
                    selectedBackgroundMonitoringEnabled = it
                    bindFixedSettingsPanel(state)
                }
                requestBackgroundMonitoringPermission(decision)
                refreshSettingsStateLater()
                return false
            }

            is BackgroundMonitoringPlanner.Decision.Reject -> {
                LogCompat.dDebug {
                    "UI_VERIFY BM rejected reason=$reason plannerReason=${decision.reason}"
                }
                decision.selectedEnabled?.let {
                    selectedBackgroundMonitoringEnabled = it
                    bindFixedSettingsPanel(state)
                }
                refreshSettingsStateLater()
                return false
            }
        }
    }

    private fun requestBackgroundMonitoringPermission(
        decision: BackgroundMonitoringPlanner.Decision.RequestPermission
    ) {
        pendingBackgroundMonitoringStart = decision.pendingStart
        when (decision.permission) {
            BackgroundMonitoringPlanner.Permission.POST_NOTIFICATIONS -> {
                pendingStartAfterNotificationPermission = true
                requestPermissions(
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_CODE_POST_NOTIFICATIONS
                )
            }

            BackgroundMonitoringPlanner.Permission.BLUETOOTH_CONNECT -> {
                pendingStartAfterBluetoothPermission = true
                requestPermissions(
                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                    REQUEST_CODE_BLUETOOTH_CONNECT
                )
                showToast(R.string.toast_bluetooth_permission_required)
            }
        }
    }

    private fun handleLiveBatteryOverlayToggle(
        enabled: Boolean,
        reason: String
    ): Boolean {
        val state = viewModel.currentUiState()
        val persistentId = state.controllerPersistentId
        val controllerIdentifier = state.controllerUniqueId
        LogCompat.dDebug {
            "UI_VERIFY LBO toggle " +
                    "reason=$reason enabled=$enabled " +
                    "profileId=${persistentId?.let(::maskIdentifier) ?: "none"} " +
                    "runtimeId=${controllerIdentifier?.let(::maskIdentifier) ?: "none"}"
        }

        val decision = liveBatteryOverlayPlanner.planUserToggle(
            enabled = enabled,
            state = buildLiveBatteryOverlayState(
                profileId = persistentId,
                controllerIdentifier = controllerIdentifier
            )
        )
        return executeLiveBatteryOverlayDecision(
            decision = decision,
            state = state,
            reason = reason
        )
    }

    private fun applyLiveBatteryOverlayPreference(
        enabled: Boolean,
        controllerIdentifier: String?,
        reason: String
    ): Boolean {
        LogCompat.dDebug {
            "UI_VERIFY LBO applySelected " +
                    "reason=$reason enabled=$enabled " +
                    "runtimeId=${controllerIdentifier?.let(::maskIdentifier) ?: "none"} " +
                    "serviceRunning=${BatteryOverlayService.isRunning} " +
                    "overlayVisible=${BatteryOverlayService.isOverlayVisible}"
        }

        val decision = liveBatteryOverlayPlanner.planProfilePreference(
            enabled = enabled,
            state = buildLiveBatteryOverlayState(
                profileId = null,
                controllerIdentifier = controllerIdentifier
            )
        )
        return executeLiveBatteryOverlayDecision(
            decision = decision,
            state = viewModel.currentUiState(),
            reason = reason
        )
    }

    private fun executeLiveBatteryOverlayDecision(
        decision: LiveBatteryOverlayPlanner.Decision,
        state: MainUiState,
        reason: String
    ): Boolean {
        when (decision) {
            is LiveBatteryOverlayPlanner.Decision.Show -> {
                val dispatched = dispatchServiceAction(
                    action = BatteryOverlayService.ACTION_SHOW_OVERLAY,
                    foreground = false,
                    controllerIdentifier = decision.controllerIdentifier
                )
                selectedLiveBatteryOverlayEnabled = dispatched && decision.selectedEnabled
                bindFixedSettingsPanel(state)
                if (dispatched && decision.persistProfileId != null) {
                    persistControllerProfile(
                        lightbarColor = selectedVibeColor,
                        targetId = decision.persistProfileId,
                        liveBatteryOverlayEnabled = true,
                        reason = "lbo_$reason"
                    )
                }
                refreshSettingsStateLater()
                return dispatched
            }

            is LiveBatteryOverlayPlanner.Decision.Hide -> {
                selectedLiveBatteryOverlayEnabled = decision.selectedEnabled
                bindFixedSettingsPanel(state)
                val dispatched = if (decision.shouldDispatchHide) {
                    dispatchServiceAction(
                        action = BatteryOverlayService.ACTION_HIDE_OVERLAY,
                        foreground = false
                    )
                } else {
                    LogCompat.dDebug {
                        "UI_VERIFY LBO applySelected reason=$reason action=hide_skipped service_idle"
                    }
                    true
                }
                if (dispatched && decision.persistProfileId != null) {
                    persistControllerProfile(
                        lightbarColor = selectedVibeColor,
                        targetId = decision.persistProfileId,
                        liveBatteryOverlayEnabled = false,
                        reason = "lbo_$reason"
                    )
                }
                refreshSettingsStateLater()
                return dispatched
            }

            is LiveBatteryOverlayPlanner.Decision.RequestOverlayPermission -> {
                decision.selectedEnabled?.let {
                    selectedLiveBatteryOverlayEnabled = it
                    bindFixedSettingsPanel(state)
                }
                requestOverlayPermission()
                showToast(R.string.toast_overlay_permission_required)
                refreshSettingsStateLater(300L)
                return false
            }

            is LiveBatteryOverlayPlanner.Decision.Reject -> {
                LogCompat.dDebug {
                    "UI_VERIFY LBO rejected reason=$reason plannerReason=${decision.reason}"
                }
                decision.selectedEnabled?.let {
                    selectedLiveBatteryOverlayEnabled = it
                    bindFixedSettingsPanel(state)
                }
                refreshSettingsStateLater()
                return false
            }
        }
    }

    private fun buildLiveBatteryOverlayState(
        profileId: String?,
        controllerIdentifier: String?
    ): LiveBatteryOverlayPlanner.State {
        return LiveBatteryOverlayPlanner.State(
            profileId = profileId,
            controllerIdentifier = controllerIdentifier,
            hasOverlayPermission = Settings.canDrawOverlays(this),
            isServiceRunning = BatteryOverlayService.isRunning,
            isOverlayVisible = BatteryOverlayService.isOverlayVisible
        )
    }

    private fun handleSettingItemClicked(itemId: SettingItemUiModel.ItemId) {
        LogCompat.dDebug { "settingItemClicked id=$itemId" }
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
            LogCompat.dDebug { "CustomizeVibeDialog already visible" }
            return
        }
        // TODO(PR-23): Gate dialog opening on the target controller profile load so the
        // initial color cannot come from the previously selected page.
        val currentState = viewModel.currentUiState()
        val profileId = currentState.controllerPersistentId
        val runtimeControllerId = currentState.controllerUniqueId
        LogCompat.dDebug {
            "CustomizeVibeDialog open profileId=${profileId?.let(::maskIdentifier)} " +
                    "runtimeId=${runtimeControllerId?.let(::maskIdentifier)} " +
                    "selectedColor=${toHexColor(selectedVibeColor)}"
        }

        val dialog = CustomizeVibeDialog(
            context = this,
            initialColor = selectedVibeColor,
            setPs4ControllerLightColorUseCase = setPs4ControllerLightColorUseCase,
            controllerIdentifier = runtimeControllerId,
            onColorApplied = { color ->
                selectedVibeColor = color
                LogCompat.dDebug {
                    "CustomizeVibeDialog onColorApplied color=${toHexColor(color)} " +
                            "profileId=${profileId?.let(::maskIdentifier)} " +
                            "runtimeId=${runtimeControllerId?.let(::maskIdentifier)}"
                }
                persistControllerProfile(color, profileId)
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
                monitoringCheckBox.isChecked = selectedBackgroundMonitoringEnabled
                floatingOverlayCheckBox.isEnabled = hasOverlayPermission
                floatingOverlayCheckBox.isChecked = selectedLiveBatteryOverlayEnabled
                syncing = false
                LogCompat.d(
                    "Config dialog sync: overlayPermission=$hasOverlayPermission " +
                            "monitoring=${state.isMonitoringEnabled} " +
                            "bmProfile=$selectedBackgroundMonitoringEnabled " +
                            "overlayVisible=${state.isOverlayVisible} " +
                            "lboProfile=$selectedLiveBatteryOverlayEnabled"
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
                val applied = handleBackgroundMonitoringToggle(
                    enabled = isChecked,
                    reason = "config_dialog"
                )
                if (applied) {
                    showToast(
                        if (isChecked) R.string.toast_monitoring_started else R.string.toast_monitoring_stopped
                    )
                }
                mainHandler.postDelayed({ syncState() }, 350L)
            }

            floatingOverlayCheckBox.setOnCheckedChangeListener { _, isChecked ->
                if (syncing) {
                    return@setOnCheckedChangeListener
                }
                LogCompat.i("Config checkbox changed: floatingOverlay=$isChecked")
                val applied = handleLiveBatteryOverlayToggle(
                    enabled = isChecked,
                    reason = "config_dialog"
                )
                if (applied) {
                    showToast(
                        if (isChecked) R.string.toast_overlay_shown else R.string.toast_overlay_hidden
                    )
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

    private fun resumePendingBackgroundMonitoringStart(reason: String): Boolean {
        val pendingStart = pendingBackgroundMonitoringStart
        pendingBackgroundMonitoringStart = null
        val currentState = viewModel.currentUiState()
        LogCompat.dDebug {
            "UI_VERIFY BM resumePending " +
                    "reason=$reason originalReason=${pendingStart?.reason ?: "none"} " +
                    "profileId=${pendingStart?.profileId?.let(::maskIdentifier) ?: "none"} " +
                    "runtimeId=${pendingStart?.controllerIdentifier?.let(::maskIdentifier) ?: "none"}"
        }
        val decision = backgroundMonitoringPlanner.planResumePending(
            pendingStart = pendingStart,
            currentState = buildBackgroundMonitoringState(
                profileId = currentState.controllerPersistentId,
                controllerIdentifier = currentState.controllerUniqueId
            )
        )
        return executeBackgroundMonitoringDecision(
            decision = decision,
            state = currentState,
            reason = reason
        )
    }

    private fun dispatchAndApplyBackgroundMonitoringStart(
        state: MainUiState,
        profileId: String,
        controllerIdentifier: String,
        pendingStart: PendingBackgroundMonitoringStart,
        reason: String
    ): Boolean {
        val dispatched = dispatchServiceAction(
            action = BatteryOverlayService.ACTION_START_MONITORING,
            foreground = true,
            controllerIdentifier = controllerIdentifier
        )
        if (!dispatched) {
            pendingBackgroundMonitoringStart = null
            LogCompat.dDebug {
                "UI_VERIFY BM startRejected reason=$reason " +
                        "profileId=${profileId.let(::maskIdentifier)} " +
                        "runtimeId=${controllerIdentifier.let(::maskIdentifier)}"
            }
            val result = backgroundMonitoringPlanner.planStartDispatchResult(
                pendingStart = pendingStart,
                dispatched = false
            )
            if (result.clearPending) {
                pendingBackgroundMonitoringStart = null
            }
            result.selectedEnabled?.let {
                selectedBackgroundMonitoringEnabled = it
                bindFixedSettingsPanel(state)
            }
            return false
        }

        val result = backgroundMonitoringPlanner.planStartDispatchResult(
            pendingStart = pendingStart,
            dispatched = true
        )
        if (result.clearPending) {
            pendingBackgroundMonitoringStart = null
        }
        result.selectedEnabled?.let {
            selectedBackgroundMonitoringEnabled = it
            bindFixedSettingsPanel(state)
        }
        if (result.persistProfileId != null && result.persistEnabled != null) {
            persistControllerProfile(
                lightbarColor = selectedVibeColor,
                targetId = result.persistProfileId,
                backgroundMonitoringEnabled = result.persistEnabled,
                reason = "bm_$reason"
            )
        } else {
            LogCompat.dDebug {
                "UI_VERIFY BM applySelected reason=$reason action=runtime_started " +
                        "profileId=${profileId.let(::maskIdentifier)} " +
                        "runtimeId=${controllerIdentifier.let(::maskIdentifier)}"
            }
        }
        refreshSettingsStateLater()
        return true
    }

    private fun applyBackgroundMonitoringPreference(
        enabled: Boolean,
        profileId: String?,
        controllerIdentifier: String?,
        reason: String
    ): Boolean {
        val resolvedProfileId = profileId?.takeIf { it.isNotBlank() }
        val resolvedControllerIdentifier = controllerIdentifier?.takeIf { it.isNotBlank() }
        LogCompat.dDebug {
            "UI_VERIFY BM applySelected " +
                    "reason=$reason enabled=$enabled " +
                    "profileId=${resolvedProfileId?.let(::maskIdentifier) ?: "none"} " +
                    "runtimeId=${resolvedControllerIdentifier?.let(::maskIdentifier) ?: "none"} " +
                    "serviceRunning=${BatteryOverlayService.isRunning} " +
                    "monitoring=${BatteryOverlayService.isMonitoringEnabled}"
        }
        val decision = backgroundMonitoringPlanner.planProfilePreference(
            enabled = enabled,
            state = buildBackgroundMonitoringState(
                profileId = profileId,
                controllerIdentifier = controllerIdentifier
            ),
            reason = reason
        )
        return executeBackgroundMonitoringDecision(
            decision = decision,
            state = viewModel.currentUiState(),
            reason = reason
        )
    }

    private fun buildBackgroundMonitoringState(
        profileId: String?,
        controllerIdentifier: String?
    ): BackgroundMonitoringPlanner.State {
        return BackgroundMonitoringPlanner.State(
            profileId = profileId,
            controllerIdentifier = controllerIdentifier,
            isMonitoringEnabled = BatteryOverlayService.isMonitoringEnabled,
            hasNotificationPermission = !shouldRequestNotificationPermission(),
            hasBluetoothConnectPermission = !shouldRequestBluetoothConnectPermission()
        )
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

    private fun handleBluetoothPermissionResult(grantResults: IntArray) {
        val granted = grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            pendingStartAfterBluetoothPermission = false
            pendingBackgroundMonitoringStart = null
            LogCompat.w("BLUETOOTH_CONNECT denied")
            showToast(R.string.toast_bluetooth_permission_required)
            return
        }

        if (pendingStartAfterBluetoothPermission) {
            pendingStartAfterBluetoothPermission = false
            LogCompat.i("BLUETOOTH_CONNECT granted, continue startMonitoring")
            if (resumePendingBackgroundMonitoringStart("bluetooth_connect_granted")) {
                showToast(R.string.toast_monitoring_started)
            }
        }
    }

    private fun dispatchServiceAction(
        action: String,
        foreground: Boolean,
        controllerIdentifier: String? = null
    ): Boolean {
        val intent = Intent(this, BatteryOverlayService::class.java).apply {
            this.action = action
            controllerIdentifier?.takeIf { it.isNotBlank() }?.let {
                putExtra(BatteryOverlayService.EXTRA_CONTROLLER_IDENTIFIER, it)
            }
        }

        val result = runCatching {
            if (foreground && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                LogCompat.i(
                    "startForegroundService action=$action " +
                            "controller=${controllerIdentifier?.let(::maskIdentifier) ?: "none"}"
                )
                startForegroundService(intent)
            } else {
                LogCompat.i(
                    "startService action=$action " +
                            "controller=${controllerIdentifier?.let(::maskIdentifier) ?: "none"}"
                )
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
            LogCompat.dDebug {
                "onViewModelChange event=${changeParam.eventType} " +
                        "source=${changeParam.source} note=${changeParam.note} " +
                        "serviceRunning=${changeParam.state.isServiceRunning} " +
                        "monitoring=${changeParam.state.isMonitoringEnabled} " +
                        "overlayVisible=${changeParam.state.isOverlayVisible}"
            }
        }
    }

    private fun renderUiState(state: MainUiState) {
        if (!::controllerPageAdapter.isInitialized || !::controllerPager.isInitialized) {
            return
        }

        handleControllerProfileState(state)
        controllerPageAdapter.submitState(state = state)
        syncControllerPagerSelection(state)
        bindFixedSettingsPanel(state)
    }

    private fun bindFixedSettingsPanel(state: MainUiState) {
        if (!::utilSettingsPanel.isInitialized || !::otherSettingsPanel.isInitialized) {
            return
        }

        val page = resolveCurrentControllerPage()
        LogCompat.dDebug {
            "UI_VERIFY FixedSettings bind " +
                    "currentItem=${controllerPager.currentItem} " +
                    "page=${page?.uniqueId?.let(::maskIdentifier) ?: "none"} " +
                    "placeholder=${page?.isPlaceholder} " +
                    "selected=${state.selectedControllerUniqueId?.let(::maskIdentifier) ?: "none"} " +
                    "monitoring=${state.isMonitoringEnabled} " +
                    "bmProfile=$selectedBackgroundMonitoringEnabled " +
                    "overlay=${state.isOverlayVisible} " +
                    "lboProfile=$selectedLiveBatteryOverlayEnabled " +
                    "protect=$protectBatteryEnabled"
        }
        utilSettingsPanel.submitItems(buildUtilSettingItems(state))
        otherSettingsPanel.submitItems(buildOtherSettingItems())
    }

    private fun buildUtilSettingItems(state: MainUiState): List<SettingItemUiModel> {
        return listOf(
            SettingItemUiModel(
                id = SettingItemUiModel.ItemId.BACKGROUND_MONITORING,
                iconRes = R.drawable.ic_ui_monitor,
                iconWidthDp = 26f,
                iconHeightDp = 14f,
                title = getString(R.string.settings_background_monitoring),
                control = SettingItemUiModel.Control.Toggle(
                    checked = selectedBackgroundMonitoringEnabled
                )
            ),
            SettingItemUiModel(
                id = SettingItemUiModel.ItemId.LIVE_BATTERY_OVERLAY,
                iconRes = R.drawable.ic_ui_overlay,
                iconWidthDp = 25f,
                iconHeightDp = 25f,
                title = getString(R.string.settings_live_overlay),
                control = SettingItemUiModel.Control.Toggle(
                    checked = selectedLiveBatteryOverlayEnabled
                )
            ),
            SettingItemUiModel(
                id = SettingItemUiModel.ItemId.CUSTOMIZE_VIBE,
                iconRes = R.drawable.ic_ui_vibe,
                iconWidthDp = 26f,
                iconHeightDp = 26f,
                title = getString(R.string.settings_customize_vibe),
                control = SettingItemUiModel.Control.None
            )
        )
    }

    private fun buildOtherSettingItems(): List<SettingItemUiModel> {
        return listOf(
            SettingItemUiModel(
                id = SettingItemUiModel.ItemId.PROTECT_BATTERY,
                iconRes = R.drawable.ic_ui_protect_battery,
                iconWidthDp = 22f,
                iconHeightDp = 26f,
                title = getString(R.string.settings_protect_battery),
                subtitle = getString(R.string.settings_limit_charging_subtitle),
                control = SettingItemUiModel.Control.Toggle(
                    checked = protectBatteryEnabled
                )
            )
        )
    }

    private fun resolveCurrentControllerPage(): ControllerPageUiModel? {
        if (!::controllerPageAdapter.isInitialized || !::controllerPager.isInitialized) {
            return null
        }

        val currentPage = controllerPageAdapter.getPageAt(controllerPager.currentItem)
        if (currentPage != null) {
            return currentPage
        }

        val selectedUniqueId = viewModel.currentUiState().selectedControllerUniqueId
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return (0 until controllerPageAdapter.itemCount).firstNotNullOfOrNull { position ->
            controllerPageAdapter.getPageAt(position)
                ?.takeIf { page -> page.uniqueId == selectedUniqueId }
        }
    }

    private fun logFixedSettingsLayout(reason: String) {
        if (!::controllerPager.isInitialized ||
            !::utilSettingsPanel.isInitialized ||
            !::otherSettingsPanel.isInitialized
        ) {
            return
        }

        LogCompat.dDebug {
            "UI_VERIFY FixedSettings layout " +
                    "reason=$reason " +
                    "pagerTop=${controllerPager.top} pagerBottom=${controllerPager.bottom} " +
                    "pagerLeft=${controllerPager.left} pagerRight=${controllerPager.right} " +
                    "utilTop=${utilSettingsPanel.top} utilBottom=${utilSettingsPanel.bottom} " +
                    "otherTop=${otherSettingsPanel.top} otherBottom=${otherSettingsPanel.bottom}"
        }
    }

    private fun logControllerPagerBounds(reason: String) {
        if (!::controllerPager.isInitialized || !::controllerPageAdapter.isInitialized) {
            return
        }

        val recyclerView = controllerPager.getChildAt(0) as? RecyclerView
        val childBounds = recyclerView?.let { rv ->
            (0 until rv.childCount).joinToString(separator = ";") { index ->
                val child = rv.getChildAt(index)
                val adapterPosition = rv.getChildAdapterPosition(child)
                "adapter=$adapterPosition,left=${child.left},right=${child.right},width=${child.width}"
            }
        } ?: "none"
        LogCompat.dDebug {
            "UI_VERIFY ControllerPager bounds " +
                    "reason=$reason current=${controllerPager.currentItem} " +
                    "itemCount=${controllerPageAdapter.itemCount} " +
                    "pagerLeft=${controllerPager.left} pagerRight=${controllerPager.right} " +
                    "pagerWidth=${controllerPager.width} " +
                    "rvWidth=${recyclerView?.width} rvScrollX=${recyclerView?.scrollX} " +
                    "children=[$childBounds]"
        }
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

        LogCompat.dDebug {
            "ControllerPager syncSelection current=${controllerPager.currentItem} target=$selectedIndex selectedUniqueId=${selectedUniqueId?.let(::maskIdentifier)}"
        }
        controllerPager.setCurrentItem(selectedIndex, false)
    }

    private fun handleControllerProfileState(state: MainUiState) {
        val selectedUniqueId = state.selectedControllerUniqueId?.takeIf { it.isNotBlank() }
        val descriptor = state.controllerDescriptor?.takeIf { it.isNotBlank() }
        val previousSelectedUniqueId = activeControllerUniqueId
        if (selectedUniqueId != previousSelectedUniqueId) {
            controllerProfileGeneration++
            LogCompat.d(
                "ControllerProfile active controller changed " +
                        "from=${previousSelectedUniqueId?.let(::maskIdentifier)} " +
                        "to=${selectedUniqueId?.let(::maskIdentifier)} " +
                        "connectionState=${state.connectionState}"
            )
        }
        activeControllerDescriptor = descriptor
        activeControllerUniqueId = selectedUniqueId
        activeControllerName = state.controllerName?.takeIf { it.isNotBlank() }

        val persistentId = state.controllerPersistentId

        if (persistentId == null) {
            lastLoadedProfileDescriptor = null
            if (previousSelectedUniqueId != null) {
                selectedVibeColor = DEFAULT_VIBE_COLOR
                selectedBackgroundMonitoringEnabled = false
                selectedLiveBatteryOverlayEnabled = false
                applyBackgroundMonitoringPreference(
                    enabled = false,
                    profileId = null,
                    controllerIdentifier = null,
                    reason = "controller_disconnected"
                )
                applyLiveBatteryOverlayPreference(
                    enabled = false,
                    controllerIdentifier = null,
                    reason = "controller_disconnected"
                )
                LogCompat.d("ControllerProfile reset to default color because controller disconnected")
            }
            return
        }

        if (selectedUniqueId != previousSelectedUniqueId) {
            selectedVibeColor = DEFAULT_VIBE_COLOR
            selectedBackgroundMonitoringEnabled = false
            selectedLiveBatteryOverlayEnabled = false
            lastLoadedProfileDescriptor = null
            applyBackgroundMonitoringPreference(
                enabled = false,
                profileId = persistentId,
                controllerIdentifier = selectedUniqueId,
                reason = "controller_changed_default"
            )
            applyLiveBatteryOverlayPreference(
                enabled = false,
                controllerIdentifier = selectedUniqueId,
                reason = "controller_changed_default"
            )
        }

        if (persistentId == lastLoadedProfileDescriptor) {
            LogCompat.d(
                "ControllerProfile load skipped id=${maskIdentifier(persistentId)} reason=already_loaded"
            )
            return
        }

        lastLoadedProfileDescriptor = persistentId
        LogCompat.d(
            "ControllerProfile load scheduled id=${maskIdentifier(persistentId)} " +
                    "runtimeId=${selectedUniqueId?.let(::maskIdentifier) ?: "n/a"} " +
                    "activeName=${activeControllerName ?: "n/a"}"
        )
        loadControllerProfile(
            persistentId = persistentId,
            controllerIdentifier = selectedUniqueId
        )
    }

    private fun loadControllerProfile(
        persistentId: String,
        controllerIdentifier: String?
    ) {
        LogCompat.d(
            "ControllerProfile load queued id=${maskIdentifier(persistentId)} " +
                    "runtimeId=${controllerIdentifier?.let(::maskIdentifier) ?: "n/a"}"
        )
        val requestGeneration = controllerProfileGeneration
        profileIoExecutor.execute {
            LogCompat.d(
                "ControllerProfile load started id=${maskIdentifier(persistentId)} " +
                        "runtimeId=${controllerIdentifier?.let(::maskIdentifier) ?: "n/a"}"
            )
            val profile = runCatching {
                getControllerProfileUseCase(persistentId)
            }.onFailure { throwable ->
                LogCompat.e("ControllerProfile load failed id=${maskIdentifier(persistentId)}", throwable)
            }.getOrNull()

            var restoreStatus: com.android.synclab.glimpse.data.model.ControllerLightCommandStatus? = null

            if (profile != null) {
                if (requestGeneration != controllerProfileGeneration) {
                    LogCompat.d(
                        "ControllerProfile auto-restore skipped id=${maskIdentifier(persistentId)} " +
                                "reason=stale_request currentGeneration=$controllerProfileGeneration " +
                                "requestGeneration=$requestGeneration"
                    )
                } else {
                    LogCompat.i(
                        "ControllerProfile auto-restoring vibe id=${maskIdentifier(persistentId)} " +
                                "runtimeId=${controllerIdentifier?.let(::maskIdentifier) ?: "n/a"} " +
                                "color=${toHexColor(profile.lightbarColor)}"
                    )
                    runCatching {
                        val result = setPs4ControllerLightColorUseCase(
                            profile.lightbarColor,
                            controllerIdentifier = controllerIdentifier
                        )
                        restoreStatus = result.status
                    }.onFailure { throwable ->
                        LogCompat.e(
                            "ControllerProfile auto-restore failed id=${maskIdentifier(persistentId)}",
                            throwable
                        )
                    }
                }
            }

            mainHandler.post {
                if (isDestroyed) {
                    LogCompat.d(
                        "ControllerProfile load ignored id=${maskIdentifier(persistentId)} reason=activity_destroyed"
                    )
                    return@post
                }
                val currentState = viewModel.currentUiState()
                val currentPersistentId = currentState.controllerPersistentId
                val currentRuntimeId = currentState.controllerUniqueId
                if (currentPersistentId != persistentId) {
                    LogCompat.d(
                        "ControllerProfile load ignored id=${maskIdentifier(persistentId)} " +
                                "reason=active_controller_switched current=${currentPersistentId?.let(::maskIdentifier)}"
                    )
                    return@post
                }
                if (controllerIdentifier != null && currentRuntimeId != controllerIdentifier) {
                    LogCompat.d(
                        "ControllerProfile load ignored id=${maskIdentifier(persistentId)} " +
                                "reason=runtime_controller_switched " +
                                "expected=${controllerIdentifier.let(::maskIdentifier)} " +
                                "current=${currentRuntimeId?.let(::maskIdentifier)}"
                    )
                    return@post
                }
                if (profile == null) {
                    LogCompat.d("ControllerProfile missing id=${maskIdentifier(persistentId)}")
                    selectedBackgroundMonitoringEnabled = false
                    selectedLiveBatteryOverlayEnabled = false
                    LogCompat.dDebug {
                        "UI_VERIFY BM profileLoad " +
                                "id=${maskIdentifier(persistentId)} runtimeId=${controllerIdentifier?.let(::maskIdentifier) ?: "n/a"} " +
                                "enabled=false missing=true"
                    }
                    LogCompat.dDebug {
                        "UI_VERIFY LBO profileLoad " +
                                "id=${maskIdentifier(persistentId)} runtimeId=${controllerIdentifier?.let(::maskIdentifier) ?: "n/a"} " +
                                "enabled=false missing=true"
                    }
                    bindFixedSettingsPanel(currentState)
                    applyBackgroundMonitoringPreference(
                        enabled = false,
                        profileId = persistentId,
                        controllerIdentifier = controllerIdentifier,
                        reason = "profile_missing"
                    )
                    applyLiveBatteryOverlayPreference(
                        enabled = false,
                        controllerIdentifier = controllerIdentifier,
                        reason = "profile_missing"
                    )
                    return@post
                }

                selectedBackgroundMonitoringEnabled = profile.backgroundMonitoringEnabled
                selectedLiveBatteryOverlayEnabled = profile.liveBatteryOverlayEnabled
                LogCompat.dDebug {
                    "UI_VERIFY BM profileLoad " +
                            "id=${maskIdentifier(persistentId)} runtimeId=${controllerIdentifier?.let(::maskIdentifier) ?: "n/a"} " +
                            "enabled=${profile.backgroundMonitoringEnabled} missing=false"
                }
                LogCompat.dDebug {
                    "UI_VERIFY LBO profileLoad " +
                            "id=${maskIdentifier(persistentId)} runtimeId=${controllerIdentifier?.let(::maskIdentifier) ?: "n/a"} " +
                            "enabled=${profile.liveBatteryOverlayEnabled} missing=false"
                }
                bindFixedSettingsPanel(currentState)
                applyBackgroundMonitoringPreference(
                    enabled = profile.backgroundMonitoringEnabled,
                    profileId = persistentId,
                    controllerIdentifier = controllerIdentifier,
                    reason = "profile_loaded"
                )
                applyLiveBatteryOverlayPreference(
                    enabled = profile.liveBatteryOverlayEnabled,
                    controllerIdentifier = controllerIdentifier,
                    reason = "profile_loaded"
                )

                // Only update internal color state if the hardware application was actually successful
                if (restoreStatus == com.android.synclab.glimpse.data.model.ControllerLightCommandStatus.SUCCESS) {
                    selectedVibeColor = profile.lightbarColor
                } else {
                    LogCompat.w(
                        "ControllerProfile color state not updated because hardware restore failed " +
                                "status=${restoreStatus ?: "error"}"
                    )
                }

                if (activeControllerName.isNullOrBlank()) {
                    activeControllerName = profile.deviceName
                }
                LogCompat.d(
                    "ControllerProfile loaded id=${maskIdentifier(persistentId)} " +
                            "runtimeId=${controllerIdentifier?.let(::maskIdentifier) ?: "n/a"} " +
                            "deviceName=${profile.deviceName} color=${toHexColor(profile.lightbarColor)} " +
                            "bm=${profile.backgroundMonitoringEnabled} " +
                            "lbo=${profile.liveBatteryOverlayEnabled} " +
                            "restoreStatus=${restoreStatus ?: "n/a"}"
                )
            }
        }
    }

    private fun persistControllerProfile(
        lightbarColor: Int,
        targetId: String? = null,
        liveBatteryOverlayEnabled: Boolean = selectedLiveBatteryOverlayEnabled,
        backgroundMonitoringEnabled: Boolean = selectedBackgroundMonitoringEnabled,
        reason: String = "profile"
    ) {
        val persistentId = targetId ?: viewModel.currentUiState().controllerPersistentId
        if (persistentId.isNullOrBlank()) {
            LogCompat.d(
                "ControllerProfile save skipped: missing persistent identifier"
            )
            return
        }
        val deviceName = activeControllerName?.takeIf { it.isNotBlank() }
            ?: getString(R.string.unknown_device_name)
        val profile = ControllerProfile(
            id = persistentId,
            deviceName = deviceName,
            lightbarColor = lightbarColor,
            liveBatteryOverlayEnabled = liveBatteryOverlayEnabled,
            backgroundMonitoringEnabled = backgroundMonitoringEnabled
        )
        LogCompat.d(
            "ControllerProfile save queued id=${maskIdentifier(persistentId)} " +
                    "reason=$reason deviceName=$deviceName " +
                    "color=${toHexColor(lightbarColor)} " +
                    "bm=$backgroundMonitoringEnabled lbo=$liveBatteryOverlayEnabled"
        )
        profileIoExecutor.execute {
            runCatching {
                upsertControllerProfileUseCase(profile)
            }.onSuccess {
                LogCompat.d(
                    "ControllerProfile saved id=${maskIdentifier(persistentId)} " +
                            "reason=$reason deviceName=$deviceName " +
                            "color=${toHexColor(lightbarColor)} " +
                            "bm=$backgroundMonitoringEnabled lbo=$liveBatteryOverlayEnabled"
                )
                LogCompat.dDebug {
                    "UI_VERIFY BM profilePersist " +
                            "id=${maskIdentifier(persistentId)} enabled=$backgroundMonitoringEnabled " +
                            "reason=$reason"
                }
                LogCompat.dDebug {
                    "UI_VERIFY LBO profilePersist " +
                            "id=${maskIdentifier(persistentId)} enabled=$liveBatteryOverlayEnabled " +
                            "reason=$reason"
                }
            }.onFailure { throwable ->
                LogCompat.e("ControllerProfile save failed id=${maskIdentifier(persistentId)}", throwable)
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
