package com.android.synclab.glimpse.infra.repository

import android.graphics.Color
import android.hardware.BatteryState
import android.hardware.lights.Light
import android.hardware.lights.LightState
import android.hardware.lights.LightsManager
import android.hardware.lights.LightsRequest
import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import com.android.synclab.glimpse.base.contracts.GamepadRepository
import com.android.synclab.glimpse.data.model.BatteryChargeStatus
import com.android.synclab.glimpse.data.model.ControllerInfo
import com.android.synclab.glimpse.data.model.ControllerLightCommandResult
import com.android.synclab.glimpse.data.model.ControllerLightCommandStatus
import com.android.synclab.glimpse.data.model.GamepadBatterySnapshot
import com.android.synclab.glimpse.infra.input.InputDeviceGateway
import com.android.synclab.glimpse.utils.InputDeviceLogUtils
import com.android.synclab.glimpse.utils.LogCompat
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

class GamepadRepositoryImpl(
    private val inputDeviceGateway: InputDeviceGateway
) : GamepadRepository {
    companion object {
        private const val SONY_VENDOR_ID = 0x054C
        private const val LOG_PREFIX = "ControllerLight"
        private val lightCommandRequestSequence = AtomicInteger(0)
        private val DUALSHOCK4_PRODUCT_IDS = intArrayOf(
            0x05C4,
            0x09CC
        )
    }

    private var lightBarSession: LightsManager.LightsSession? = null
    private var lightBarSessionDeviceId: Int? = null
    private var cachedLightTarget: CachedLightTarget? = null

    private data class CachedLightTarget(
        val deviceId: Int,
        val lightId: Int,
        val lightName: String?
    )

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
                    descriptor = device.descriptor,
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

    @Synchronized
    override fun setPs4ControllerLightColor(color: Int): ControllerLightCommandResult {
        val requestId = lightCommandRequestSequence.incrementAndGet()
        val startedAtMs = SystemClock.elapsedRealtime()
        val colorHex = String.format("#%06X", 0xFFFFFF and color)
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)

        logDiagnosticDebug {
            "$LOG_PREFIX requestId=$requestId phase=start " +
                    "sdk=${Build.VERSION.SDK_INT} color=$colorHex rgb=($red,$green,$blue) " +
                    "thread=${Thread.currentThread().name}"
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            val elapsedMs = SystemClock.elapsedRealtime() - startedAtMs
            LogCompat.w(
                "$LOG_PREFIX requestId=$requestId phase=result status=${ControllerLightCommandStatus.UNSUPPORTED_API} " +
                        "reason=unsupported_sdk sdk=${Build.VERSION.SDK_INT} elapsedMs=$elapsedMs"
            )
            return ControllerLightCommandResult(
                status = ControllerLightCommandStatus.UNSUPPORTED_API,
                colorHex = colorHex,
                detail = "sdk=${Build.VERSION.SDK_INT}"
            )
        }

        tryApplyColorWithCachedTarget(
            requestId = requestId,
            startedAtMs = startedAtMs,
            color = color,
            colorHex = colorHex
        )?.let { fastResult ->
            return fastResult
        }

        val devices = inputDeviceGateway.getInputDevices()
        logDiagnosticDebug {
            "$LOG_PREFIX requestId=$requestId phase=device_scan " +
                    "deviceCount=${devices.size}"
        }
        devices.forEachIndexed { index, device ->
            val supportsGamepad = device.supportsSource(InputDevice.SOURCE_GAMEPAD)
            val supportsJoystick = device.supportsSource(InputDevice.SOURCE_JOYSTICK)
            val likelyPs4 = isLikelyPs4ControllerForLightCommand(device)
            val lightsInfo = runCatching {
                val lights = device.lightsManager.lights
                "lightsCount=${lights.size}"
            }.getOrElse { throwable ->
                "lightsError=${throwable.javaClass.simpleName}:${throwable.message.orEmpty()}"
            }
            logDiagnosticDebug {
                "$LOG_PREFIX requestId=$requestId phase=device_scan index=$index " +
                        "deviceId=${device.id} name=${device.name} " +
                        "vendor=${device.vendorId} product=${device.productId} " +
                        "sources=0x${device.sources.toString(16)} " +
                        "supportsGamepad=$supportsGamepad supportsJoystick=$supportsJoystick " +
                        "likelyPs4=$likelyPs4 ${InputDeviceLogUtils.buildBatteryInfo(device)} $lightsInfo"
            }
        }

        val preferredPs4Device = devices.firstOrNull(::isLikelyPs4ControllerForLightCommand)
        val targetDevice = preferredPs4Device ?: devices.firstOrNull(::isGamepad)
        val selectionReason = when {
            preferredPs4Device != null -> "ps4_match"
            targetDevice != null -> "first_gamepad_fallback"
            else -> "none"
        }

        logDiagnosticDebug {
            "$LOG_PREFIX requestId=$requestId phase=select_device " +
                    "reason=$selectionReason selectedDeviceId=${targetDevice?.id}"
        }

        if (targetDevice == null) {
            cachedLightTarget = null
            val elapsedMs = SystemClock.elapsedRealtime() - startedAtMs
            LogCompat.w(
                "$LOG_PREFIX requestId=$requestId phase=result status=${ControllerLightCommandStatus.NO_CONTROLLER} " +
                        "elapsedMs=$elapsedMs"
            )
            return ControllerLightCommandResult(
                status = ControllerLightCommandStatus.NO_CONTROLLER,
                colorHex = colorHex
            )
        }

        return runCatching {
            val lightsManager = targetDevice.lightsManager
            val lights = lightsManager.lights
            logDiagnosticDebug {
                "$LOG_PREFIX requestId=$requestId phase=select_light " +
                        "deviceId=${targetDevice.id} name=${targetDevice.name} lightsCount=${lights.size}"
            }

            lights.forEach { light ->
                val stateInfo = runCatching {
                    lightsManager.getLightState(light).toString()
                }.getOrElse { throwable ->
                    "stateError=${throwable.javaClass.simpleName}:${throwable.message.orEmpty()}"
                }
                logDiagnosticDebug {
                    "$LOG_PREFIX requestId=$requestId phase=light_detail " +
                            "deviceId=${targetDevice.id} lightId=${light.id} name=${light.name} " +
                            "type=${lightTypeLabel(light.type)}(${light.type}) " +
                            "rgb=${light.hasRgbControl()} brightness=${light.hasBrightnessControl()} " +
                            "ordinal=${light.ordinal} state=$stateInfo"
                }
            }

            val targetLight = lights.firstOrNull { it.hasRgbControl() }
            val lightReason = if (targetLight != null) "rgb_capable" else "no_rgb_light"
            logDiagnosticDebug {
                "$LOG_PREFIX requestId=$requestId phase=select_light_result " +
                        "reason=$lightReason selectedLightId=${targetLight?.id}"
            }

            if (targetLight == null) {
                cachedLightTarget = null
                val elapsedMs = SystemClock.elapsedRealtime() - startedAtMs
                LogCompat.w(
                    "$LOG_PREFIX requestId=$requestId phase=result status=${ControllerLightCommandStatus.NO_LIGHT} " +
                            "deviceId=${targetDevice.id} reason=$lightReason elapsedMs=$elapsedMs"
                )
                ControllerLightCommandResult(
                    status = ControllerLightCommandStatus.NO_LIGHT,
                    targetDeviceId = targetDevice.id,
                    colorHex = colorHex
                )
            } else {
                cachedLightTarget = CachedLightTarget(
                    deviceId = targetDevice.id,
                    lightId = targetLight.id,
                    lightName = targetLight.name
                )
                applyColorToTarget(
                    targetDevice = targetDevice,
                    lightsManager = lightsManager,
                    targetLight = targetLight,
                    color = color,
                    colorHex = colorHex,
                    requestId = requestId,
                    startedAtMs = startedAtMs,
                    phaseLabel = "apply_request",
                    includeStateAfter = true
                )
            }
        }.getOrElse { throwable ->
            closeControllerLightSessionInternal(
                reason = "requestFailure:${throwable.javaClass.simpleName}",
                requestId = requestId,
                clearTargetCache = true
            )
            val elapsedMs = SystemClock.elapsedRealtime() - startedAtMs
            val errorDetail = "${throwable.javaClass.simpleName}:${throwable.message.orEmpty()}"
            when (throwable) {
                is SecurityException -> {
                    LogCompat.e(
                        "$LOG_PREFIX requestId=$requestId phase=result " +
                                "status=${ControllerLightCommandStatus.PERMISSION_DENIED} " +
                                "deviceId=${targetDevice.id} color=$colorHex error=$errorDetail " +
                                "elapsedMs=$elapsedMs",
                        throwable
                    )
                    ControllerLightCommandResult(
                        status = ControllerLightCommandStatus.PERMISSION_DENIED,
                        targetDeviceId = targetDevice.id,
                        colorHex = colorHex,
                        detail = throwable.message
                    )
                }

                else -> {
                    LogCompat.e(
                        "$LOG_PREFIX requestId=$requestId phase=result " +
                                "status=${ControllerLightCommandStatus.FAILED} " +
                                "deviceId=${targetDevice.id} color=$colorHex error=$errorDetail " +
                                "elapsedMs=$elapsedMs",
                        throwable
                    )
                    ControllerLightCommandResult(
                        status = ControllerLightCommandStatus.FAILED,
                        targetDeviceId = targetDevice.id,
                        colorHex = colorHex,
                        detail = throwable.javaClass.simpleName
                    )
                }
            }
        }
    }

    private fun tryApplyColorWithCachedTarget(
        requestId: Int,
        startedAtMs: Long,
        color: Int,
        colorHex: String
    ): ControllerLightCommandResult? {
        val target = cachedLightTarget ?: return null
        val cachedDevice = InputDevice.getDevice(target.deviceId)
        if (cachedDevice == null || !isGamepad(cachedDevice)) {
            cachedLightTarget = null
            logDiagnosticDebug {
                "$LOG_PREFIX requestId=$requestId phase=fast_path_miss " +
                        "reason=device_unavailable cachedDeviceId=${target.deviceId}"
            }
            return null
        }

        return try {
            val lightsManager = cachedDevice.lightsManager
            val cachedLight = lightsManager.lights.firstOrNull { it.id == target.lightId }
            if (cachedLight == null) {
                cachedLightTarget = null
                logDiagnosticDebug {
                    "$LOG_PREFIX requestId=$requestId phase=fast_path_miss " +
                            "reason=light_unavailable cachedDeviceId=${target.deviceId} " +
                            "cachedLightId=${target.lightId}"
                }
                return null
            }
            applyColorToTarget(
                targetDevice = cachedDevice,
                lightsManager = lightsManager,
                targetLight = cachedLight,
                color = color,
                colorHex = colorHex,
                requestId = requestId,
                startedAtMs = startedAtMs,
                phaseLabel = "fast_apply",
                includeStateAfter = false
            )
        } catch (throwable: Throwable) {
            val errorDetail = "${throwable.javaClass.simpleName}:${throwable.message.orEmpty()}"
            LogCompat.w(
                "$LOG_PREFIX requestId=$requestId phase=fast_path_error " +
                        "deviceId=${target.deviceId} lightId=${target.lightId} error=$errorDetail",
                throwable
            )
            closeControllerLightSessionInternal(
                reason = "fastPathFailure:${throwable.javaClass.simpleName}",
                requestId = requestId,
                clearTargetCache = true
            )
            null
        }
    }

    private fun applyColorToTarget(
        targetDevice: InputDevice,
        lightsManager: LightsManager,
        targetLight: Light,
        color: Int,
        colorHex: String,
        requestId: Int,
        startedAtMs: Long,
        phaseLabel: String,
        includeStateAfter: Boolean
    ): ControllerLightCommandResult {
        val request = LightsRequest.Builder()
            .addLight(
                targetLight,
                LightState.Builder()
                    .setColor(color)
                    .build()
            )
            .build()

        val session = obtainLightBarSession(targetDevice, requestId)
        logDiagnosticDebug {
            "$LOG_PREFIX requestId=$requestId phase=$phaseLabel " +
                    "deviceId=${targetDevice.id} lightId=${targetLight.id} " +
                    "lightName=${targetLight.name} color=$colorHex " +
                    "sessionDeviceId=$lightBarSessionDeviceId"
        }
        session.requestLights(request)

        val stateAfter = if (includeStateAfter) {
            runCatching { lightsManager.getLightState(targetLight).toString() }
                .getOrElse { throwable ->
                    "stateAfterError=${throwable.javaClass.simpleName}:${throwable.message.orEmpty()}"
                }
        } else {
            "stateAfter=skipped"
        }

        val elapsedMs = SystemClock.elapsedRealtime() - startedAtMs
        val resultMessage =
            "$LOG_PREFIX requestId=$requestId phase=result status=${ControllerLightCommandStatus.SUCCESS} " +
                    "path=$phaseLabel deviceId=${targetDevice.id} lightId=${targetLight.id} " +
                    "color=$colorHex $stateAfter elapsedMs=$elapsedMs"
        if (includeStateAfter) {
            logDiagnosticInfo { resultMessage }
        } else {
            logDiagnosticDebug { resultMessage }
        }

        return ControllerLightCommandResult(
            status = ControllerLightCommandStatus.SUCCESS,
            targetDeviceId = targetDevice.id,
            targetLightId = targetLight.id,
            colorHex = colorHex,
            detail = stateAfter
        )
    }

    @Synchronized
    override fun closeControllerLightSession(reason: String) {
        closeControllerLightSessionInternal(
            reason = reason,
            requestId = null,
            clearTargetCache = false
        )
    }

    private fun closeControllerLightSessionInternal(
        reason: String,
        requestId: Int?,
        clearTargetCache: Boolean
    ) {
        val logContext = if (requestId != null) {
            "$LOG_PREFIX requestId=$requestId"
        } else {
            LOG_PREFIX
        }
        if (clearTargetCache) {
            cachedLightTarget = null
        }

        val session = lightBarSession ?: run {
            logDiagnosticDebug {
                "$logContext phase=session_close skipped=true reason=$reason " +
                        "noActiveSession=true cacheCleared=$clearTargetCache"
            }
            return
        }

        runCatching {
            session.close()
        }.onFailure { throwable ->
            LogCompat.w(
                "$logContext phase=session_close failed=true reason=$reason " +
                        "cacheCleared=$clearTargetCache",
                throwable
            )
        }
        logDiagnosticDebug {
            "$logContext phase=session_close failed=false reason=$reason " +
                    "deviceId=$lightBarSessionDeviceId cacheCleared=$clearTargetCache"
        }
        lightBarSession = null
        lightBarSessionDeviceId = null
    }

    private fun isGamepad(device: InputDevice): Boolean {
        return device.supportsSource(InputDevice.SOURCE_GAMEPAD) ||
                device.supportsSource(InputDevice.SOURCE_JOYSTICK)
    }

    private fun isLikelyPs4ControllerForLightCommand(device: InputDevice): Boolean {
        if (!isGamepad(device)) {
            return false
        }
        return isPs4Controller(device)
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

    private fun obtainLightBarSession(
        targetDevice: InputDevice,
        requestId: Int
    ): LightsManager.LightsSession {
        val existingSession = lightBarSession
        if (existingSession != null && lightBarSessionDeviceId == targetDevice.id) {
            logDiagnosticDebug {
                "$LOG_PREFIX requestId=$requestId phase=session_reuse " +
                        "deviceId=${targetDevice.id}"
            }
            return existingSession
        }

        if (existingSession != null) {
            logDiagnosticDebug {
                "$LOG_PREFIX requestId=$requestId phase=session_switch " +
                        "oldDeviceId=$lightBarSessionDeviceId newDeviceId=${targetDevice.id}"
            }
            runCatching {
                existingSession.close()
            }.onFailure { throwable ->
                LogCompat.w(
                    "$LOG_PREFIX requestId=$requestId phase=session_switch_close_failed",
                    throwable
                )
            }
            lightBarSession = null
            lightBarSessionDeviceId = null
        }

        val newSession = targetDevice.lightsManager.openSession()
        lightBarSession = newSession
        lightBarSessionDeviceId = targetDevice.id
        logDiagnosticDebug {
            "$LOG_PREFIX requestId=$requestId phase=session_open " +
                    "deviceId=${targetDevice.id}"
        }
        return newSession
    }

    private fun lightTypeLabel(type: Int): String {
        return when (type) {
            Light.LIGHT_TYPE_INPUT -> "INPUT"
            Light.LIGHT_TYPE_PLAYER_ID -> "PLAYER_ID"
            Light.LIGHT_TYPE_MICROPHONE -> "MICROPHONE"
            Light.LIGHT_TYPE_KEYBOARD_BACKLIGHT -> "KEYBOARD_BACKLIGHT"
            else -> "UNKNOWN"
        }
    }

    private fun isDiagnosticLightLoggingEnabled(): Boolean {
        return LogCompat.isDebugBuild()
    }

    private inline fun logDiagnosticDebug(message: () -> String) {
        if (isDiagnosticLightLoggingEnabled()) {
            LogCompat.d(message())
        }
    }

    private inline fun logDiagnosticInfo(message: () -> String) {
        if (isDiagnosticLightLoggingEnabled()) {
            LogCompat.i(message())
        }
    }
}
