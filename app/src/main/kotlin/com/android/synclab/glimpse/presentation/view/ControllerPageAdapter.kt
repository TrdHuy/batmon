package com.android.synclab.glimpse.presentation.view

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.android.synclab.glimpse.R
import com.android.synclab.glimpse.data.model.BatteryChargeStatus
import com.android.synclab.glimpse.presentation.model.ControllerPageUiModel
import com.android.synclab.glimpse.presentation.model.MainUiState
import com.android.synclab.glimpse.presentation.model.SettingItemUiModel

class ControllerPageAdapter(
    private val onToggleChanged: (ControllerPageUiModel, SettingItemUiModel.ItemId, Boolean) -> Unit,
    private val onItemClicked: (ControllerPageUiModel, SettingItemUiModel.ItemId) -> Unit
) : RecyclerView.Adapter<ControllerPageAdapter.ControllerPageViewHolder>() {

    private var pages: List<ControllerPageUiModel> = emptyList()
    private var uiState: MainUiState = MainUiState()
    private var selectedVibeColor: Int = DEFAULT_SELECTED_VIBE_COLOR
    private var protectBatteryEnabled: Boolean = false

    init {
        setHasStableIds(true)
    }

    fun submitState(
        state: MainUiState,
        selectedVibeColor: Int,
        protectBatteryEnabled: Boolean
    ) {
        uiState = state
        this.selectedVibeColor = selectedVibeColor
        this.protectBatteryEnabled = protectBatteryEnabled
        pages = if (state.controllerPages.isNotEmpty()) {
            state.controllerPages
        } else {
            listOf(buildPlaceholderPage(state))
        }
        notifyDataSetChanged()
    }

    fun getPageAt(position: Int): ControllerPageUiModel? {
        return pages.getOrNull(position)
    }

    override fun getItemCount(): Int = pages.size

    override fun getItemId(position: Int): Long {
        return pages.getOrNull(position)?.uniqueId?.hashCode()?.toLong() ?: RecyclerView.NO_ID
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ControllerPageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_controller_page, parent, false)
        return ControllerPageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ControllerPageViewHolder, position: Int) {
        holder.bind(
            page = pages[position],
            state = uiState,
            selectedVibeColor = selectedVibeColor,
            protectBatteryEnabled = protectBatteryEnabled
        )
    }

    private fun buildPlaceholderPage(state: MainUiState): ControllerPageUiModel {
        val label = when (state.connectionState) {
            MainUiState.ConnectionState.LOADING -> "loading-controller"
            MainUiState.ConnectionState.INPUT_MANAGER_UNAVAILABLE -> "input-manager-unavailable"
            MainUiState.ConnectionState.DISCONNECTED -> "no-controller"
            MainUiState.ConnectionState.CONNECTED -> state.controllerUniqueId ?: "selected-controller"
        }
        return ControllerPageUiModel(
            uniqueId = "__placeholder__:$label",
            descriptor = null,
            deviceId = null,
            name = label,
            vendorId = null,
            productId = null,
            batteryPercent = null,
            batteryStatus = BatteryChargeStatus.UNKNOWN,
            isSelected = false,
            isMock = false,
            isPlaceholder = true
        )
    }

    inner class ControllerPageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val deviceInfoView: TextView = view.findViewById(R.id.deviceInfoView)
        private val batteryPercentText: TextView = view.findViewById(R.id.batteryPercentText)
        private val batteryStateText: TextView = view.findViewById(R.id.batteryStateText)
        private val batteryUniqueIdText: TextView = view.findViewById(R.id.batteryUniqueIdText)
        private val batteryCircle: BatteryProgressView = view.findViewById(R.id.batteryCircle)
        private val chargingIconView: ChargingIconView = view.findViewById(R.id.chargingIcon)
        private val utilSettingsPanel: SettingsPanelView = view.findViewById(R.id.utilSettingsPanel)
        private val otherSettingsPanel: SettingsPanelView = view.findViewById(R.id.otherSettingsPanel)

        init {
            batteryCircle.max = 100
        }

        fun bind(
            page: ControllerPageUiModel,
            state: MainUiState,
            selectedVibeColor: Int,
            protectBatteryEnabled: Boolean
        ) {
            val isPlaceholder = page.isPlaceholder
            val statusLabelRes = when (state.connectionState) {
                MainUiState.ConnectionState.LOADING -> R.string.loading_controller_info
                MainUiState.ConnectionState.INPUT_MANAGER_UNAVAILABLE -> R.string.input_manager_unavailable
                MainUiState.ConnectionState.DISCONNECTED -> R.string.status_card_controller_disconnected
                MainUiState.ConnectionState.CONNECTED -> R.string.status_card_controller_connected
            }
            if (isPlaceholder || page.name.isBlank()) {
                deviceInfoView.setText(statusLabelRes)
            } else {
                deviceInfoView.text = page.name
            }

            batteryUniqueIdText.text = if (isPlaceholder) {
                itemView.context.getString(R.string.status_card_controller_unique_id_placeholder)
            } else {
                val displayId = page.descriptor?.takeIf { it.isNotBlank() } ?: page.uniqueId
                itemView.context.getString(
                    R.string.status_card_controller_unique_id_format,
                    displayId
                )
            }

            val batteryPercent = page.batteryPercent
            if (batteryPercent == null) {
                batteryPercentText.setText(R.string.status_card_battery_unknown)
                batteryCircle.setProgressCompat(0, false)
            } else {
                val clampedPercent = batteryPercent.coerceIn(0, 100)
                batteryPercentText.text = itemView.context.getString(
                    R.string.status_card_battery_percent,
                    clampedPercent
                )
                batteryCircle.setProgressCompat(clampedPercent, false)
            }

            val isCharging = page.batteryStatus == BatteryChargeStatus.CHARGING
            batteryCircle.setChargingAnimationEnabled(isCharging)
            batteryStateText.text = batteryStatusLabel(page.batteryStatus)

            chargingIconView.setGlowStyle(
                color = CHARGING_GLOW_COLOR,
                radiusPx = 11f * itemView.resources.displayMetrics.density
            )
            chargingIconView.setGlowEnabled(isCharging)

            utilSettingsPanel.setInteractionHandlers(
                onToggleChanged = { itemId, checked ->
                    onToggleChanged(page, itemId, checked)
                },
                onItemClicked = { itemId ->
                    onItemClicked(page, itemId)
                }
            )
            otherSettingsPanel.setInteractionHandlers(
                onToggleChanged = { itemId, checked ->
                    onToggleChanged(page, itemId, checked)
                },
                onItemClicked = { itemId ->
                    onItemClicked(page, itemId)
                }
            )

            utilSettingsPanel.submitItems(
                listOf(
                    SettingItemUiModel(
                        id = SettingItemUiModel.ItemId.BACKGROUND_MONITORING,
                        iconRes = R.drawable.ic_ui_monitor,
                        iconWidthDp = 26f,
                        iconHeightDp = 14f,
                        title = itemView.context.getString(R.string.settings_background_monitoring),
                        control = SettingItemUiModel.Control.Toggle(
                            checked = state.isMonitoringEnabled
                        )
                    ),
                    SettingItemUiModel(
                        id = SettingItemUiModel.ItemId.LIVE_BATTERY_OVERLAY,
                        iconRes = R.drawable.ic_ui_overlay,
                        iconWidthDp = 25f,
                        iconHeightDp = 25f,
                        title = itemView.context.getString(R.string.settings_live_overlay),
                        control = SettingItemUiModel.Control.Toggle(
                            checked = state.isOverlayVisible
                        )
                    ),
                    SettingItemUiModel(
                        id = SettingItemUiModel.ItemId.CUSTOMIZE_VIBE,
                        iconRes = R.drawable.ic_ui_vibe,
                        iconWidthDp = 26f,
                        iconHeightDp = 26f,
                        title = itemView.context.getString(R.string.settings_customize_vibe),
                        control = SettingItemUiModel.Control.None
                    )
                )
            )

            otherSettingsPanel.submitItems(
                listOf(
                    SettingItemUiModel(
                        id = SettingItemUiModel.ItemId.PROTECT_BATTERY,
                        iconRes = R.drawable.ic_ui_protect_battery,
                        iconWidthDp = 22f,
                        iconHeightDp = 26f,
                        title = itemView.context.getString(R.string.settings_protect_battery),
                        subtitle = itemView.context.getString(R.string.settings_limit_charging_subtitle),
                        control = SettingItemUiModel.Control.Toggle(
                            checked = protectBatteryEnabled
                        )
                    )
                )
            )

            itemView.isActivated = page.isSelected
        }

        private fun batteryStatusLabel(status: BatteryChargeStatus): String {
            return when (status) {
                BatteryChargeStatus.CHARGING -> itemView.context.getString(R.string.battery_status_charging)
                BatteryChargeStatus.DISCHARGING -> itemView.context.getString(R.string.battery_status_discharging)
                BatteryChargeStatus.FULL -> itemView.context.getString(R.string.battery_status_full)
                BatteryChargeStatus.NOT_CHARGING -> itemView.context.getString(R.string.battery_status_not_charging)
                BatteryChargeStatus.UNKNOWN -> itemView.context.getString(R.string.battery_status_unknown)
            }
        }
    }

    companion object {
        private const val DEFAULT_SELECTED_VIBE_COLOR = Color.BLUE
        private const val CHARGING_GLOW_COLOR = 0xFFD58C2E.toInt()
    }
}
