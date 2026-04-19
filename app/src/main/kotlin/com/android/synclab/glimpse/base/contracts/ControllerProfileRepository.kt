package com.android.synclab.glimpse.base.contracts

import com.android.synclab.glimpse.data.model.ControllerProfile

interface ControllerProfileRepository {
    fun getById(id: String): ControllerProfile?

    fun upsert(profile: ControllerProfile)

    fun deleteById(id: String)
}
