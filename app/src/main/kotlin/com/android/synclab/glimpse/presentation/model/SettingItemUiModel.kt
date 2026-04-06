package com.android.synclab.glimpse.presentation.model

import androidx.annotation.DrawableRes

data class SettingItemUiModel(
    val id: ItemId,
    @DrawableRes val iconRes: Int,
    val title: String,
    val subtitle: String? = null,
    val control: Control
) {
    enum class ItemId {
        BACKGROUND_MONITORING,
        LIVE_BATTERY_OVERLAY,
        CUSTOMIZE_VIBE,
        PROTECT_BATTERY
    }

    sealed interface Control {
        data class Toggle(
            val checked: Boolean,
            val enabled: Boolean = true
        ) : Control

        data object None : Control

        data object Indicator : Control

        data class Action(@DrawableRes val iconRes: Int) : Control
    }
}
