package com.android.synclab.glimpse.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.android.synclab.glimpse.base.contracts.MonitoringStateProvider
import com.android.synclab.glimpse.data.model.BatteryChargeStatus
import com.android.synclab.glimpse.data.model.ControllerInfo
import com.android.synclab.glimpse.domain.usecase.GetConnectedPs4ControllersUseCase
import com.android.synclab.glimpse.infra.input.InputDeviceGateway
import com.android.synclab.glimpse.presentation.model.EventChangeParam
import com.android.synclab.glimpse.presentation.model.MainUiState
import com.android.synclab.glimpse.utils.LogCompat

class MainViewModel(
    private val inputDeviceGateway: InputDeviceGateway,
    private val getConnectedPs4ControllersUseCase: GetConnectedPs4ControllersUseCase,
    private val monitoringStateProvider: MonitoringStateProvider
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

        if (!inputDeviceGateway.isInputManagerAvailable()) {
            updateState(
                newState = current.copy(
                    connectionState = MainUiState.ConnectionState.INPUT_MANAGER_UNAVAILABLE,
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
        val primaryController = ps4Controllers.firstOrNull()

        val newState = if (primaryController == null) {
            current.copy(
                connectionState = MainUiState.ConnectionState.DISCONNECTED,
                batteryPercent = null,
                batteryStatus = BatteryChargeStatus.UNKNOWN,
                controllerUniqueId = null,
                controllerDescriptor = null,
                controllerName = null
            )
        } else {
            current.copy(
                connectionState = MainUiState.ConnectionState.CONNECTED,
                batteryPercent = primaryController.batteryPercent,
                batteryStatus = primaryController.batteryStatus ?: BatteryChargeStatus.UNKNOWN,
                controllerUniqueId = buildControllerUniqueId(primaryController),
                controllerDescriptor = primaryController.descriptor?.trim()?.takeIf { it.isNotEmpty() },
                controllerName = primaryController.name
            )
        }

        updateState(
            newState = newState,
            eventType = EventChangeParam.EventType.CONTROLLER_INFO_UPDATED,
            source = source
        )

        LogCompat.d(
            "MainViewModel refreshControllerInfo controllers=${ps4Controllers.size} " +
                    "primaryBattery=${primaryController?.batteryPercent} " +
                    "primaryStatus=${primaryController?.batteryStatus} " +
                    "primaryUniqueId=${newState.controllerUniqueId} " +
                    "primaryDescriptor=${newState.controllerDescriptor} " +
                    "primaryName=${newState.controllerName}"
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
}

class MainViewModelFactory(
    private val inputDeviceGateway: InputDeviceGateway,
    private val getConnectedPs4ControllersUseCase: GetConnectedPs4ControllersUseCase,
    private val monitoringStateProvider: MonitoringStateProvider
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(
                inputDeviceGateway = inputDeviceGateway,
                getConnectedPs4ControllersUseCase = getConnectedPs4ControllersUseCase,
                monitoringStateProvider = monitoringStateProvider
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
