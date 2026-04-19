package com.android.synclab.glimpse.domain.usecase

import com.android.synclab.glimpse.base.contracts.ControllerProfileRepository

class DeleteControllerProfileUseCase(
    private val repository: ControllerProfileRepository
) {
    operator fun invoke(id: String) {
        repository.deleteById(id)
    }
}
