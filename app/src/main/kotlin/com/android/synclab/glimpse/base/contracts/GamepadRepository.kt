package com.android.synclab.glimpse.base.contracts

import com.android.synclab.glimpse.data.model.ControllerInfo
import com.android.synclab.glimpse.data.model.GamepadBatterySnapshot

interface GamepadRepository {
    fun getConnectedPs4Controllers(defaultDeviceName: String): List<ControllerInfo>

    fun getPrimaryGamepadBatterySnapshot(defaultControllerName: String): GamepadBatterySnapshot
}
