package com.android.synclab.glimpse.domain.usecase

import com.android.synclab.glimpse.data.model.ControllerInfo
import com.android.synclab.glimpse.infra.protectbattery.AndroidProtectBatteryGateway

open class ProtectBatteryUseCases(
    private val gateway: AndroidProtectBatteryGateway? = null,
    private val getConnectedPs4ControllersUseCase: GetConnectedPs4ControllersUseCase? = null
) {
    open fun isEnabled(): Boolean {
        return requireGateway().isEnabled()
    }

    open fun setEnabled(enabled: Boolean) {
        requireGateway().setEnabled(enabled)
    }

    open fun getAlertedControllerIds(): Set<String> {
        return requireGateway().getAlertedControllerIds()
    }

    open fun setAlertedControllerIds(controllerIds: Set<String>) {
        requireGateway().setAlertedControllerIds(controllerIds)
    }

    open fun getConnectedPs4Controllers(defaultDeviceName: String): List<ControllerInfo> {
        return requireGetConnectedPs4ControllersUseCase().invoke(defaultDeviceName)
    }

    open fun scheduleNextCheck() {
        requireGateway().scheduleNextCheck()
    }

    open fun cancelNextCheck() {
        requireGateway().cancelNextCheck()
    }

    open fun postThresholdAlert(
        controllerId: String,
        controllerName: String,
        percent: Int
    ) {
        requireGateway().postThresholdAlert(
            controllerId = controllerId,
            controllerName = controllerName,
            percent = percent
        )
    }

    open fun postDevControllerThresholdAlert(percent: Int) {
        requireGateway().postDevControllerThresholdAlert(percent)
    }

    private fun requireGateway(): AndroidProtectBatteryGateway {
        return requireNotNull(gateway) {
            "AndroidProtectBatteryGateway is required for production ProtectBatteryUseCases"
        }
    }

    private fun requireGetConnectedPs4ControllersUseCase(): GetConnectedPs4ControllersUseCase {
        return requireNotNull(getConnectedPs4ControllersUseCase) {
            "GetConnectedPs4ControllersUseCase is required for production ProtectBatteryUseCases"
        }
    }
}
