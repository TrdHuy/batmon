package com.android.synclab.glimpse.base.contracts

import com.android.synclab.glimpse.data.model.ControllerInfo
import com.android.synclab.glimpse.data.model.GamepadBatterySnapshot
import com.android.synclab.glimpse.data.model.ControllerLightCommandResult

interface GamepadRepository {
    fun getConnectedPs4Controllers(defaultDeviceName: String): List<ControllerInfo>

    fun getPrimaryGamepadBatterySnapshot(defaultControllerName: String): GamepadBatterySnapshot

    fun setPs4ControllerLightColor(
        color: Int,
        controllerIdentifier: String? = null
    ): ControllerLightCommandResult

    fun closeControllerLightSession(reason: String)
}
