package com.android.synclab.glimpse.domain.usecase

import com.android.synclab.glimpse.base.contracts.ControllerProfileRepository
import com.android.synclab.glimpse.data.model.ControllerProfile

class UpsertControllerProfileUseCase(
    private val repository: ControllerProfileRepository
) {
    operator fun invoke(profile: ControllerProfile) {
        repository.upsert(profile)
    }
}
