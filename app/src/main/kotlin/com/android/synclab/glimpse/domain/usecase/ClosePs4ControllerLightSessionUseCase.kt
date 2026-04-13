package com.android.synclab.glimpse.domain.usecase

import com.android.synclab.glimpse.base.contracts.GamepadRepository

class ClosePs4ControllerLightSessionUseCase(
    private val repository: GamepadRepository
) {
    operator fun invoke(reason: String) {
        repository.closeControllerLightSession(reason)
    }
}
