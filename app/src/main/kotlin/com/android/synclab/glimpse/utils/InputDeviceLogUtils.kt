package com.android.synclab.glimpse.utils

import android.os.Build
import android.view.InputDevice

object InputDeviceLogUtils {
    fun buildBatteryInfo(device: InputDevice): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return "batteryState=unsupported_sdk_${Build.VERSION.SDK_INT}"
        }

        return runCatching {
            val state = device.batteryState
            "batteryPresent=${state.isPresent} batteryCapacity=${state.capacity} batteryStatus=${state.status}"
        }.getOrElse { throwable ->
            "batteryReadError=${throwable.javaClass.simpleName}"
        }
    }
}
