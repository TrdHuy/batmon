package com.android.synclab.glimpse.domain.usecase

import com.android.synclab.glimpse.base.contracts.GamepadRepository
import com.android.synclab.glimpse.data.model.ControllerLightCommandResult

class SetPs4ControllerLightColorUseCase(
    private val repository: GamepadRepository
) {
    operator fun invoke(
        color: Int,
        controllerIdentifier: String? = null
    ): ControllerLightCommandResult {
        return repository.setPs4ControllerLightColor(
            color = color,
            controllerIdentifier = controllerIdentifier
        )
    }
}
