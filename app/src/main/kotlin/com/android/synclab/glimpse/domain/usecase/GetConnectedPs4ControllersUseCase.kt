package com.android.synclab.glimpse.domain.usecase

import com.android.synclab.glimpse.base.contracts.GamepadRepository
import com.android.synclab.glimpse.data.model.ControllerInfo

class GetConnectedPs4ControllersUseCase(
    private val repository: GamepadRepository
) {
    operator fun invoke(defaultDeviceName: String): List<ControllerInfo> {
        return repository.getConnectedPs4Controllers(defaultDeviceName)
    }
}
