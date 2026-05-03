package com.android.synclab.glimpse.presentation.model

import com.android.synclab.glimpse.data.model.BatteryChargeStatus

object DebugControllerPageFactory {

    fun createPages(previousSelectedUniqueId: String? = null): List<ControllerPageUiModel> {
        val basePages = listOf(
            ControllerPageUiModel(
                uniqueId = "debug-controller-alpha",
                descriptor = null,
                deviceId = null,
                name = "Debug Controller Alpha",
                vendorId = null,
                productId = null,
                batteryPercent = 78,
                batteryStatus = BatteryChargeStatus.CHARGING,
                isSelected = false,
                isMock = true
            ),
            ControllerPageUiModel(
                uniqueId = "debug-controller-bravo",
                descriptor = null,
                deviceId = null,
                name = "Debug Controller Bravo",
                vendorId = null,
                productId = null,
                batteryPercent = 42,
                batteryStatus = BatteryChargeStatus.DISCHARGING,
                isSelected = false,
                isMock = true
            ),
            ControllerPageUiModel(
                uniqueId = "debug-controller-charlie",
                descriptor = null,
                deviceId = null,
                name = "Debug Controller Charlie",
                vendorId = null,
                productId = null,
                batteryPercent = null,
                batteryStatus = BatteryChargeStatus.UNKNOWN,
                isSelected = false,
                isMock = true
            )
        )

        val selectedUniqueId = when {
            previousSelectedUniqueId != null && basePages.any { it.uniqueId == previousSelectedUniqueId } -> {
                previousSelectedUniqueId
            }
            else -> basePages.first().uniqueId
        }

        return basePages.map { page ->
            page.copy(isSelected = page.uniqueId == selectedUniqueId)
        }
    }
}
