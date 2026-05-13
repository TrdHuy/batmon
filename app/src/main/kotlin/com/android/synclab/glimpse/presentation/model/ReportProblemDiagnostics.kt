package com.android.synclab.glimpse.presentation.model

import java.util.Locale

data class ReportProblemDiagnostics(
    val appVersion: String,
    val appVersionCode: Int,
    val packageName: String,
    val androidRelease: String,
    val sdkInt: Int,
    val manufacturer: String,
    val model: String,
    val timestamp: String,
    val serviceRunning: Boolean,
    val monitoringEnabled: Boolean,
    val overlayVisible: Boolean,
    val controllers: List<ControllerDiagnostics>
) {
    data class ControllerDiagnostics(
        val name: String,
        val deviceId: Int,
        val vendorId: Int,
        val productId: Int,
        val descriptor: String?,
        val batteryPercent: Int?,
        val batteryStatus: String
    )

    fun toEmailSection(): String {
        return buildString {
            appendLine("Diagnostics:")
            appendLine("App version: $appVersion ($appVersionCode)")
            appendLine("Package: $packageName")
            appendLine("Android: $androidRelease / API $sdkInt")
            appendLine("Device: $manufacturer $model")
            appendLine("Timestamp: $timestamp")
            appendLine("Service running: $serviceRunning")
            appendLine("Background Monitoring runtime: $monitoringEnabled")
            appendLine("Live Battery Overlay runtime: $overlayVisible")
            appendLine("Controllers:")
            if (controllers.isEmpty()) {
                appendLine("- none")
            } else {
                controllers.forEachIndexed { index, controller ->
                    appendLine(
                        "- #${index + 1}: ${controller.name}, " +
                                "deviceId=${controller.deviceId}, " +
                                "vendor/product=${controller.vendorId.toHexId()}/${controller.productId.toHexId()}, " +
                                "descriptor=${maskSensitiveIdentifier(controller.descriptor)}, " +
                                "battery=${controller.batteryPercent?.let { "$it%" } ?: "unavailable"}, " +
                                "status=${controller.batteryStatus}"
                    )
                }
            }
        }.trimEnd()
    }
}

private fun maskSensitiveIdentifier(value: String?): String {
    val normalized = value?.takeIf { it.isNotBlank() } ?: return "n/a"
    return if (normalized.length <= 12) {
        "***"
    } else {
        "${normalized.take(6)}...${normalized.takeLast(6)}"
    }
}

private fun Int.toHexId(): String {
    return String.format(Locale.US, "0x%04X", this)
}
