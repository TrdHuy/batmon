package com.android.synclab.glimpse.domain.usecase

import com.android.synclab.glimpse.base.contracts.ControllerProfileRepository
import com.android.synclab.glimpse.data.model.ControllerProfile

class GetControllerProfileUseCase(
    private val repository: ControllerProfileRepository
) {
    operator fun invoke(id: String): ControllerProfile? {
        return repository.getById(id)
    }
}
