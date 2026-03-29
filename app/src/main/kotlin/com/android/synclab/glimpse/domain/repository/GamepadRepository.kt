package com.android.synclab.glimpse.domain.repository

import com.android.synclab.glimpse.domain.model.ControllerInfo
import com.android.synclab.glimpse.domain.model.GamepadBatterySnapshot

interface GamepadRepository {
    fun getConnectedPs4Controllers(defaultDeviceName: String): List<ControllerInfo>

    fun getPrimaryGamepadBatterySnapshot(defaultControllerName: String): GamepadBatterySnapshot
}
