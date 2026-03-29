package com.android.synclab.glimpse.domain.usecase

import com.android.synclab.glimpse.domain.model.ControllerInfo
import com.android.synclab.glimpse.domain.repository.GamepadRepository

class GetConnectedPs4ControllersUseCase(
    private val repository: GamepadRepository
) {
    operator fun invoke(defaultDeviceName: String): List<ControllerInfo> {
        return repository.getConnectedPs4Controllers(defaultDeviceName)
    }
}
