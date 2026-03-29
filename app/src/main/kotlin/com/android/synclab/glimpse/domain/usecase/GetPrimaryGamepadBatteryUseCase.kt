package com.android.synclab.glimpse.domain.usecase

import com.android.synclab.glimpse.domain.model.GamepadBatterySnapshot
import com.android.synclab.glimpse.domain.repository.GamepadRepository

class GetPrimaryGamepadBatteryUseCase(
    private val repository: GamepadRepository
) {
    operator fun invoke(defaultControllerName: String): GamepadBatterySnapshot {
        return repository.getPrimaryGamepadBatterySnapshot(defaultControllerName)
    }
}
