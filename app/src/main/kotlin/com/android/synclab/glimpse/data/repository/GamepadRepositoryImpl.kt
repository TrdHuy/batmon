package com.android.synclab.glimpse.data.repository

import android.hardware.BatteryState
import android.os.Build
import android.view.InputDevice
import com.android.synclab.glimpse.domain.model.BatteryChargeStatus
import com.android.synclab.glimpse.domain.model.ControllerInfo
import com.android.synclab.glimpse.domain.model.GamepadBatterySnapshot
import com.android.synclab.glimpse.domain.repository.GamepadRepository
import com.android.synclab.glimpse.infra.input.InputDeviceGateway
import com.android.synclab.glimpse.utils.LogCompat
import java.util.Locale
import kotlin.math.roundToInt

class GamepadRepositoryImpl(
    private val inputDeviceGateway: InputDeviceGateway
) : GamepadRepository {
    companion object {
        private const val SONY_VENDOR_ID = 0x054C
        private val DUALSHOCK4_PRODUCT_IDS = intArrayOf(
            0x05C4,
            0x09CC
        )
    }

    override fun getConnectedPs4Controllers(defaultDeviceName: String): List<ControllerInfo> {
        return inputDeviceGateway
            .getInputDevices()
            .asSequence()
            .filter(::isGamepad)
            .filter(::isPs4Controller)
            .map { device ->
                val batteryInfo = readBatteryInfo(device)
                ControllerInfo(
                    deviceId = device.id,
                    name = device.name ?: defaultDeviceName,
                    vendorId = device.vendorId,
                    productId = device.productId,
                    batteryPercent = batteryInfo.first,
                    batteryStatus = batteryInfo.second
                )
            }
            .toList()
    }

    override fun getPrimaryGamepadBatterySnapshot(defaultControllerName: String): GamepadBatterySnapshot {
        val devices = inputDeviceGateway.getInputDevices()
        var fallbackControllerName: String? = null

        for (device in devices) {
            if (!isGamepad(device)) {
                continue
            }

            val controllerName = device.name ?: defaultControllerName
            if (fallbackControllerName == null) {
                fallbackControllerName = controllerName
            }

            val batteryPercent = readBatteryInfo(device).first
            if (batteryPercent != null) {
                return GamepadBatterySnapshot(controllerName, batteryPercent)
            }
        }

        return when {
            fallbackControllerName != null -> GamepadBatterySnapshot(fallbackControllerName, null)
            else -> GamepadBatterySnapshot(null, null)
        }
    }

    private fun isGamepad(device: InputDevice): Boolean {
        return device.supportsSource(InputDevice.SOURCE_GAMEPAD) ||
                device.supportsSource(InputDevice.SOURCE_JOYSTICK)
    }

    private fun isPs4Controller(device: InputDevice): Boolean {
        val vendorId = device.vendorId
        val productId = device.productId
        if (vendorId == SONY_VENDOR_ID && isDualShock4ProductId(productId)) {
            return true
        }

        val lowerName = (device.name ?: "").lowercase(Locale.US)
        if (lowerName.contains("dualshock")) {
            return true
        }

        return vendorId == SONY_VENDOR_ID &&
                productId == 0 &&
                lowerName.contains("wireless controller")
    }

    private fun isDualShock4ProductId(productId: Int): Boolean {
        return DUALSHOCK4_PRODUCT_IDS.contains(productId)
    }

    private fun readBatteryInfo(device: InputDevice): Pair<Int?, BatteryChargeStatus?> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return Pair(null, null)
        }

        return try {
            val batteryState: BatteryState = device.batteryState ?: return Pair(null, null)
            if (!batteryState.isPresent) {
                return Pair(null, null)
            }

            val capacity = batteryState.capacity
            if (capacity.isNaN() || capacity < 0f) {
                return Pair(null, null)
            }

            val normalized = if (capacity > 1.0f) capacity else capacity * 100f
            val percent = normalized.roundToInt().coerceIn(0, 100)
            Pair(percent, mapStatus(batteryState.status))
        } catch (exception: Exception) {
            LogCompat.w("Failed to read gamepad battery state", exception)
            Pair(null, null)
        }
    }

    private fun mapStatus(status: Int): BatteryChargeStatus {
        return when (status) {
            BatteryState.STATUS_CHARGING -> BatteryChargeStatus.CHARGING
            BatteryState.STATUS_DISCHARGING -> BatteryChargeStatus.DISCHARGING
            BatteryState.STATUS_FULL -> BatteryChargeStatus.FULL
            BatteryState.STATUS_NOT_CHARGING -> BatteryChargeStatus.NOT_CHARGING
            else -> BatteryChargeStatus.UNKNOWN
        }
    }
}
