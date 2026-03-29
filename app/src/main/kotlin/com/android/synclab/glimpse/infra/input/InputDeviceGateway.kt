package com.android.synclab.glimpse.infra.input

import android.content.Context
import android.hardware.input.InputManager
import android.os.Handler
import android.view.InputDevice

class InputDeviceGateway(context: Context) {
    private val inputManager: InputManager? = context.getSystemService(InputManager::class.java)

    fun isInputManagerAvailable(): Boolean {
        return inputManager != null
    }

    fun registerInputDeviceListener(listener: InputManager.InputDeviceListener, handler: Handler) {
        inputManager?.registerInputDeviceListener(listener, handler)
    }

    fun unregisterInputDeviceListener(listener: InputManager.InputDeviceListener) {
        inputManager?.unregisterInputDeviceListener(listener)
    }

    fun getInputDevices(): List<InputDevice> {
        val manager = inputManager
        val deviceIds = manager?.inputDeviceIds ?: InputDevice.getDeviceIds()
        return deviceIds
            .asSequence()
            .mapNotNull { deviceId ->
                manager?.getInputDevice(deviceId) ?: InputDevice.getDevice(deviceId)
            }
            .toList()
    }
}
