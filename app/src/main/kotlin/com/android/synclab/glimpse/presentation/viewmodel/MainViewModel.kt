package com.android.synclab.glimpse.presentation.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.android.synclab.glimpse.data.model.BatteryChargeStatus
import com.android.synclab.glimpse.domain.usecase.GetConnectedPs4ControllersUseCase
import com.android.synclab.glimpse.infra.input.InputDeviceGateway
import com.android.synclab.glimpse.presentation.BatteryOverlayService
import com.android.synclab.glimpse.presentation.model.MainUiAction
import com.android.synclab.glimpse.presentation.model.MainUiState
import com.android.synclab.glimpse.utils.LogCompat

class MainViewModel(
    private val inputDeviceGateway: InputDeviceGateway,
    private val getConnectedPs4ControllersUseCase: GetConnectedPs4ControllersUseCase
) : ViewModel() {

    private val _uiState = MutableLiveData(MainUiState())
    val uiState: LiveData<MainUiState> = _uiState

    init {
        syncServiceState()
    }

    fun onAction(action: MainUiAction) {
        when (action) {
            is MainUiAction.RefreshControllerInfo -> refreshControllerInfo(action.unknownDeviceName)
            MainUiAction.SyncServiceState -> syncServiceState()
        }
    }

    private fun refreshControllerInfo(unknownDeviceName: String) {
        val current = currentState().copy(
            isServiceRunning = BatteryOverlayService.isRunning,
            isMonitoringEnabled = BatteryOverlayService.isMonitoringEnabled,
            isOverlayVisible = BatteryOverlayService.isOverlayVisible
        )

        if (!inputDeviceGateway.isInputManagerAvailable()) {
            _uiState.value = current.copy(
                connectionState = MainUiState.ConnectionState.INPUT_MANAGER_UNAVAILABLE,
                batteryPercent = null,
                batteryStatus = BatteryChargeStatus.UNKNOWN
            )
            return
        }

        val ps4Controllers = getConnectedPs4ControllersUseCase(unknownDeviceName)
        val primaryController = ps4Controllers.firstOrNull()

        _uiState.value = if (primaryController == null) {
            current.copy(
                connectionState = MainUiState.ConnectionState.DISCONNECTED,
                batteryPercent = null,
                batteryStatus = BatteryChargeStatus.UNKNOWN
            )
        } else {
            current.copy(
                connectionState = MainUiState.ConnectionState.CONNECTED,
                batteryPercent = primaryController.batteryPercent,
                batteryStatus = primaryController.batteryStatus ?: BatteryChargeStatus.UNKNOWN
            )
        }

        LogCompat.d(
            "MainViewModel refreshControllerInfo controllers=${ps4Controllers.size} " +
                    "primaryBattery=${primaryController?.batteryPercent} " +
                    "primaryStatus=${primaryController?.batteryStatus}"
        )
    }

    private fun syncServiceState() {
        val current = currentState()
        _uiState.value = current.copy(
            isServiceRunning = BatteryOverlayService.isRunning,
            isMonitoringEnabled = BatteryOverlayService.isMonitoringEnabled,
            isOverlayVisible = BatteryOverlayService.isOverlayVisible
        )
    }

    private fun currentState(): MainUiState {
        return _uiState.value ?: MainUiState()
    }
}

class MainViewModelFactory(
    private val inputDeviceGateway: InputDeviceGateway,
    private val getConnectedPs4ControllersUseCase: GetConnectedPs4ControllersUseCase
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(
                inputDeviceGateway = inputDeviceGateway,
                getConnectedPs4ControllersUseCase = getConnectedPs4ControllersUseCase
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
