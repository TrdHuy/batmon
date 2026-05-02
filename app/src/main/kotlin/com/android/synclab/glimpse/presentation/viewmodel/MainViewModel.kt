package com.android.synclab.glimpse.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.android.synclab.glimpse.base.contracts.MonitoringStateProvider
import com.android.synclab.glimpse.data.model.BatteryChargeStatus
import com.android.synclab.glimpse.data.model.ControllerInfo
import com.android.synclab.glimpse.domain.usecase.GetConnectedPs4ControllersUseCase
import com.android.synclab.glimpse.infra.input.InputDeviceGateway
import com.android.synclab.glimpse.presentation.model.DebugControllerPageFactory
import com.android.synclab.glimpse.presentation.model.ControllerPageUiModel
import com.android.synclab.glimpse.presentation.model.EventChangeParam
import com.android.synclab.glimpse.presentation.model.MainUiState
import com.android.synclab.glimpse.utils.LogCompat

class MainViewModel(
    private val inputDeviceGateway: InputDeviceGateway,
    private val getConnectedPs4ControllersUseCase: GetConnectedPs4ControllersUseCase,
    private val monitoringStateProvider: MonitoringStateProvider,
    private val isDebuggableApp: Boolean
) : ViewModel() {

    private var uiState: MainUiState = MainUiState()
    private var onViewModelChange: ((EventChangeParam) -> Unit)? = null

    init {
        syncServiceState(emitChange = false)
    }

    fun setOnViewModelChange(callback: ((EventChangeParam) -> Unit)?) {
        onViewModelChange = callback
        callback?.invoke(
            EventChangeParam(
                state = uiState,
                eventType = EventChangeParam.EventType.VIEW_ATTACHED,
                source = EventChangeParam.Source.SYSTEM,
                note = "observer_registered"
            )
        )
    }

    fun clearOnViewModelChange() {
        onViewModelChange = null
    }

    fun currentUiState(): MainUiState {
        return uiState
    }

    fun refreshControllerInfo(
        unknownDeviceName: String,
        source: EventChangeParam.Source = EventChangeParam.Source.VIEW
    ) {
        LogCompat.d(
            "MainViewModel refreshControllerInfo start source=$source " +
                    "inputManagerAvailable=${inputDeviceGateway.isInputManagerAvailable()}"
        )
        val current = uiState.copy(
            isServiceRunning = monitoringStateProvider.isServiceRunning,
            isMonitoringEnabled = monitoringStateProvider.isMonitoringEnabled,
            isOverlayVisible = monitoringStateProvider.isOverlayVisible
        )

        val mockModeEnabled = isDebugMockControllerPagesEnabled()
        if (mockModeEnabled) {
            val mockPages = DebugControllerPageFactory.createPages(
                previousSelectedUniqueId = uiState.selectedControllerUniqueId
            )
            val selectedMockPage = mockPages.firstOrNull { it.isSelected } ?: mockPages.first()
            val mockState = current.copy(
                connectionState = MainUiState.ConnectionState.CONNECTED,
                controllerPages = mockPages,
                selectedControllerUniqueId = selectedMockPage.uniqueId,
                batteryPercent = selectedMockPage.batteryPercent,
                batteryStatus = selectedMockPage.batteryStatus,
                controllerUniqueId = selectedMockPage.uniqueId,
                controllerDescriptor = selectedMockPage.descriptor,
                controllerName = selectedMockPage.name
            )
            updateState(
                newState = mockState,
                eventType = EventChangeParam.EventType.CONTROLLER_INFO_UPDATED,
                source = source
            )
            LogCompat.d(
                "MainViewModel refreshControllerInfo using mock pages " +
                        "count=${mockPages.size} selectedUniqueId=${selectedMockPage.uniqueId} " +
                        "battery=${selectedMockPage.batteryPercent} status=${selectedMockPage.batteryStatus}"
            )
            return
        }

        if (!inputDeviceGateway.isInputManagerAvailable()) {
            updateState(
                newState = current.copy(
                    connectionState = MainUiState.ConnectionState.INPUT_MANAGER_UNAVAILABLE,
                    controllerPages = emptyList(),
                    selectedControllerUniqueId = null,
                    batteryPercent = null,
                    batteryStatus = BatteryChargeStatus.UNKNOWN,
                    controllerUniqueId = null,
                    controllerDescriptor = null,
                    controllerName = null
                ),
                eventType = EventChangeParam.EventType.CONTROLLER_INFO_UPDATED,
                source = source
            )
            return
        }

        val ps4Controllers = getConnectedPs4ControllersUseCase(unknownDeviceName)
        val selectedUniqueId = resolveSelectedControllerUniqueId(ps4Controllers)
        val selectedController = ps4Controllers.firstOrNull {
            buildControllerUniqueId(it) == selectedUniqueId
        }
        val controllerPages = ps4Controllers.map { controller ->
            val uniqueId = buildControllerUniqueId(controller)
            controller.toControllerPage(
                uniqueId = uniqueId,
                isSelected = uniqueId == selectedUniqueId
            )
        }

        val newState = if (selectedController == null) {
            current.copy(
                connectionState = MainUiState.ConnectionState.DISCONNECTED,
                controllerPages = emptyList(),
                selectedControllerUniqueId = null,
                batteryPercent = null,
                batteryStatus = BatteryChargeStatus.UNKNOWN,
                controllerUniqueId = null,
                controllerDescriptor = null,
                controllerName = null
            )
        } else {
            current.copy(
                connectionState = MainUiState.ConnectionState.CONNECTED,
                controllerPages = controllerPages,
                selectedControllerUniqueId = selectedUniqueId,
                batteryPercent = selectedController.batteryPercent,
                batteryStatus = selectedController.batteryStatus ?: BatteryChargeStatus.UNKNOWN,
                controllerUniqueId = buildControllerUniqueId(selectedController),
                controllerDescriptor = selectedController.descriptor?.trim()?.takeIf { it.isNotEmpty() },
                controllerName = selectedController.name
            )
        }

        updateState(
            newState = newState,
            eventType = EventChangeParam.EventType.CONTROLLER_INFO_UPDATED,
            source = source
        )

        LogCompat.d(
            "MainViewModel refreshControllerInfo controllers=${ps4Controllers.size} " +
                    "controllerPages=${controllerPages.size} " +
                    "selectedUniqueId=${selectedUniqueId?.let(::maskIdentifier)} " +
                    "selectedBattery=${selectedController?.batteryPercent} " +
                    "selectedStatus=${selectedController?.batteryStatus} " +
                    "primaryUniqueId=${newState.controllerUniqueId?.let(::maskIdentifier)} " +
                    "primaryDescriptor=${newState.controllerDescriptor?.let(::maskIdentifier)} " +
                    "primaryName=${newState.controllerName}"
        )
    }

    fun selectController(
        uniqueId: String,
        source: EventChangeParam.Source = EventChangeParam.Source.VIEW
    ) {
        val target = uiState.controllerPages.firstOrNull { it.uniqueId == uniqueId }
        if (target == null || target.isPlaceholder) {
            LogCompat.d(
                "MainViewModel selectController ignored uniqueId=${maskIdentifier(uniqueId)} " +
                        "reason=not_found_or_placeholder"
            )
            return
        }
        if (uiState.selectedControllerUniqueId == uniqueId) {
            LogCompat.d(
                "MainViewModel selectController skipped uniqueId=${maskIdentifier(uniqueId)} reason=already_selected"
            )
            return
        }

        val updatedPages = uiState.controllerPages.map {
            it.copy(isSelected = it.uniqueId == uniqueId)
        }
        updateState(
            newState = uiState.copy(
                controllerPages = updatedPages,
                selectedControllerUniqueId = uniqueId,
                batteryPercent = target.batteryPercent,
                batteryStatus = target.batteryStatus,
                controllerUniqueId = target.uniqueId,
                controllerDescriptor = target.descriptor,
                controllerName = target.name
            ),
            eventType = EventChangeParam.EventType.CONTROLLER_INFO_UPDATED,
            source = source
        )
    }

    private fun resolveSelectedControllerUniqueId(controllers: List<ControllerInfo>): String? {
        if (controllers.isEmpty()) {
            return null
        }
        val previousSelected = uiState.selectedControllerUniqueId
        if (previousSelected != null && controllers.any { buildControllerUniqueId(it) == previousSelected }) {
            return previousSelected
        }
        return controllers.firstOrNull()?.let(::buildControllerUniqueId)
    }

    private fun ControllerInfo.toControllerPage(
        uniqueId: String,
        isSelected: Boolean
    ): ControllerPageUiModel {
        return ControllerPageUiModel(
            uniqueId = uniqueId,
            descriptor = descriptor?.trim()?.takeIf { it.isNotEmpty() },
            deviceId = deviceId,
            name = name,
            vendorId = vendorId,
            productId = productId,
            batteryPercent = batteryPercent,
            batteryStatus = batteryStatus ?: BatteryChargeStatus.UNKNOWN,
            isSelected = isSelected
        )
    }

    private fun buildControllerUniqueId(controller: ControllerInfo): String {
        val descriptor = controller.descriptor?.trim().orEmpty()
        if (descriptor.isNotEmpty()) {
            return descriptor
        }
        return "VID:${controller.vendorId}_PID:${controller.productId}_DID:${controller.deviceId}"
    }

    fun syncServiceState(
        source: EventChangeParam.Source = EventChangeParam.Source.VIEW,
        emitChange: Boolean = true
    ) {
        val newState = uiState.copy(
            isServiceRunning = monitoringStateProvider.isServiceRunning,
            isMonitoringEnabled = monitoringStateProvider.isMonitoringEnabled,
            isOverlayVisible = monitoringStateProvider.isOverlayVisible
        )
        updateState(
            newState = newState,
            eventType = EventChangeParam.EventType.SERVICE_STATE_SYNCED,
            source = source,
            emitChange = emitChange
        )
    }

    private fun updateState(
        newState: MainUiState,
        eventType: EventChangeParam.EventType,
        source: EventChangeParam.Source,
        emitChange: Boolean = true
    ) {
        val oldState = uiState
        uiState = newState
        LogCompat.d(
            "MainViewModel updateState event=$eventType source=$source emitChange=$emitChange " +
                    "connection=${oldState.connectionState}->${newState.connectionState} " +
                    "battery=${oldState.batteryPercent}->${newState.batteryPercent} " +
                    "status=${oldState.batteryStatus}->${newState.batteryStatus} " +
                    "descriptor=${oldState.controllerDescriptor?.let(::maskIdentifier)}->${newState.controllerDescriptor?.let(::maskIdentifier)} " +
                    "service=${oldState.isServiceRunning}->${newState.isServiceRunning} " +
                    "monitoring=${oldState.isMonitoringEnabled}->${newState.isMonitoringEnabled} " +
                    "overlay=${oldState.isOverlayVisible}->${newState.isOverlayVisible}"
        )
        if (emitChange) {
            onViewModelChange?.invoke(
                EventChangeParam(
                    state = uiState,
                    eventType = eventType,
                    source = source
                )
            )
        }
    }

    private fun maskIdentifier(raw: String): String {
        return if (raw.length <= 12) raw else "${raw.take(6)}...${raw.takeLast(6)}"
    }

    private fun isDebugMockControllerPagesEnabled(): Boolean {
        if (!isDebuggableApp) {
            return false
        }
        return readSystemProperty("debug.glimpse.mock_controllers") == "1"
    }

    private fun readSystemProperty(name: String): String? {
        return runCatching {
            val process = ProcessBuilder("/system/bin/getprop", name)
                .redirectErrorStream(true)
                .start()
            process.inputStream.bufferedReader().use { reader ->
                reader.readText().trim().takeIf { it.isNotEmpty() }
            }.also {
                process.waitFor()
            }
        }.getOrNull()
    }
}

class MainViewModelFactory(
    private val inputDeviceGateway: InputDeviceGateway,
    private val getConnectedPs4ControllersUseCase: GetConnectedPs4ControllersUseCase,
    private val monitoringStateProvider: MonitoringStateProvider,
    private val isDebuggableApp: Boolean
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(
                inputDeviceGateway = inputDeviceGateway,
                getConnectedPs4ControllersUseCase = getConnectedPs4ControllersUseCase,
                monitoringStateProvider = monitoringStateProvider,
                isDebuggableApp = isDebuggableApp
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
