package com.android.synclab.glimpse.domain.usecase

import com.android.synclab.glimpse.base.contracts.GamepadRepository
import com.android.synclab.glimpse.data.model.GamepadBatterySnapshot

class GetPrimaryGamepadBatteryUseCase(
    private val repository: GamepadRepository
) {
    operator fun invoke(defaultControllerName: String): GamepadBatterySnapshot {
        return repository.getPrimaryGamepadBatterySnapshot(defaultControllerName)
    }
}
