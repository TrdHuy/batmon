package com.android.synclab.glimpse.infra.repository.roomdb

import com.android.synclab.glimpse.base.contracts.ControllerProfileRepository
import com.android.synclab.glimpse.data.model.ControllerProfile
import com.android.synclab.glimpse.infra.repository.roomdb.core.ControllerProfileDao
import com.android.synclab.glimpse.infra.repository.roomdb.core.ControllerProfileEntity
import com.android.synclab.glimpse.utils.LogCompat
import java.util.Locale

class RoomControllerProfileRepository(
    private val controllerProfileDao: ControllerProfileDao
) : ControllerProfileRepository {
    override fun getById(id: String): ControllerProfile? {
        val maskedId = maskIdentifier(id)
        LogCompat.d("ControllerProfileRepository getById start id=$maskedId")
        val entity = controllerProfileDao.getById(id)
        if (entity == null) {
            LogCompat.d("ControllerProfileRepository getById miss id=$maskedId")
            return null
        }
        LogCompat.d(
            "ControllerProfileRepository getById hit id=$maskedId " +
                    "deviceName=${entity.deviceName} " +
                    "color=${toHexColor(entity.lightbarColor)} " +
                    "lbo=${entity.liveBatteryOverlayEnabled}"
        )
        return entity.toModel()
    }

    override fun upsert(profile: ControllerProfile) {
        LogCompat.d(
            "ControllerProfileRepository upsert id=${maskIdentifier(profile.id)} " +
                    "deviceName=${profile.deviceName} " +
                    "color=${toHexColor(profile.lightbarColor)} " +
                    "lbo=${profile.liveBatteryOverlayEnabled}"
        )
        controllerProfileDao.upsert(profile.toEntity())
    }

    override fun deleteById(id: String) {
        LogCompat.d("ControllerProfileRepository deleteById id=${maskIdentifier(id)}")
        controllerProfileDao.deleteById(id)
    }

    private fun ControllerProfileEntity.toModel(): ControllerProfile {
        return ControllerProfile(
            id = id,
            deviceName = deviceName,
            lightbarColor = lightbarColor,
            liveBatteryOverlayEnabled = liveBatteryOverlayEnabled
        )
    }

    private fun ControllerProfile.toEntity(): ControllerProfileEntity {
        return ControllerProfileEntity(
            id = id,
            deviceName = deviceName,
            lightbarColor = lightbarColor,
            liveBatteryOverlayEnabled = liveBatteryOverlayEnabled
        )
    }

    private fun maskIdentifier(raw: String): String {
        return if (raw.length <= 12) raw else "${raw.take(6)}...${raw.takeLast(6)}"
    }

    private fun toHexColor(color: Int): String {
        return String.format(Locale.US, "#%06X", 0xFFFFFF and color)
    }
}
