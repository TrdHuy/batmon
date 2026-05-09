package com.android.synclab.glimpse.presentation.view

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.android.synclab.glimpse.R
import com.android.synclab.glimpse.data.model.BatteryChargeStatus
import com.android.synclab.glimpse.presentation.model.ControllerPageUiModel
import com.android.synclab.glimpse.presentation.model.MainUiState
import com.android.synclab.glimpse.presentation.model.SettingItemUiModel
import com.android.synclab.glimpse.utils.LogCompat

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
        val oldPages = pages
        val oldState = uiState
        val oldProtectBatteryEnabled = this.protectBatteryEnabled
        val newPages = if (state.controllerPages.isNotEmpty()) {
            state.controllerPages
        } else {
            listOf(buildPlaceholderPage(state))
        }
        val diffResult = DiffUtil.calculateDiff(
            ControllerPageDiffCallback(
                oldPages = oldPages,
                newPages = newPages
            )
        )
        val globalPayload = buildGlobalPayload(
            oldState = oldState,
            newState = state,
            oldProtectBatteryEnabled = oldProtectBatteryEnabled,
            newProtectBatteryEnabled = protectBatteryEnabled
        )
        LogCompat.d(
            "UI_VERIFY ControllerPageAdapter submit " +
                    "oldCount=${oldPages.size} newCount=${newPages.size} " +
                    "pagesChanged=${oldPages != newPages} " +
                    "globalPayload=$globalPayload " +
                    "selected=${state.selectedControllerUniqueId?.let(::maskControllerPageId) ?: "n/a"}"
        )

        uiState = state
        this.selectedVibeColor = selectedVibeColor
        this.protectBatteryEnabled = protectBatteryEnabled
        pages = newPages

        diffResult.dispatchUpdatesTo(this)
        if (globalPayload.hasChanges() && pages.isNotEmpty()) {
            notifyItemRangeChanged(0, pages.size, globalPayload)
        }
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
        LogCompat.d(
            "UI_VERIFY ControllerPageAdapter fullBind " +
                    "position=$position id=${maskControllerPageId(pages[position].uniqueId)} " +
                    "selected=${pages[position].isSelected}"
        )
        holder.bind(
            page = pages[position],
            state = uiState,
            selectedVibeColor = selectedVibeColor,
            protectBatteryEnabled = protectBatteryEnabled
        )
    }

    override fun onBindViewHolder(
        holder: ControllerPageViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        val payload = payloads
            .filterIsInstance<ControllerPagePayload>()
            .fold(ControllerPagePayload()) { current, next -> current.merge(next) }
        if (!payload.hasChanges()) {
            onBindViewHolder(holder, position)
            return
        }

        LogCompat.d(
            "UI_VERIFY ControllerPageAdapter payloadBind " +
                    "position=$position id=${maskControllerPageId(pages[position].uniqueId)} " +
                    "payload=$payload"
        )
        holder.bindPayload(
            page = pages[position],
            state = uiState,
            protectBatteryEnabled = protectBatteryEnabled,
            payload = payload
        )
    }

    private fun buildPlaceholderPage(state: MainUiState): ControllerPageUiModel {
        val label = when (state.connectionState) {
            MainUiState.ConnectionState.LOADING -> "loading-controller"
            MainUiState.ConnectionState.INPUT_MANAGER_UNAVAILABLE -> "input-manager-unavailable"
            MainUiState.ConnectionState.DISCONNECTED -> "no-controller"
            MainUiState.ConnectionState.CONNECTED -> state.controllerUniqueId ?: "selected-controller"
        }
        val id = "__placeholder__:$label"
        return ControllerPageUiModel(
            uniqueId = id,
            persistentId = id,
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

    private fun buildGlobalPayload(
        oldState: MainUiState,
        newState: MainUiState,
        oldProtectBatteryEnabled: Boolean,
        newProtectBatteryEnabled: Boolean
    ): ControllerPagePayload {
        return ControllerPagePayload(
            deviceInfoChanged = oldState.connectionState != newState.connectionState,
            settingsChanged = oldState.isMonitoringEnabled != newState.isMonitoringEnabled ||
                    oldState.isOverlayVisible != newState.isOverlayVisible ||
                    oldProtectBatteryEnabled != newProtectBatteryEnabled
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
            bindDeviceInfo(page, state)
            bindBattery(page)
            bindSettings(page, state, protectBatteryEnabled)
            bindSelection(page)
        }

        internal fun bindPayload(
            page: ControllerPageUiModel,
            state: MainUiState,
            protectBatteryEnabled: Boolean,
            payload: ControllerPagePayload
        ) {
            if (payload.deviceInfoChanged) {
                bindDeviceInfo(page, state)
            }
            if (payload.batteryChanged) {
                bindBattery(page)
            }
            if (payload.settingsChanged) {
                bindSettings(page, state, protectBatteryEnabled)
            }
            if (payload.selectionChanged) {
                bindSelection(page)
            }
        }

        private fun bindDeviceInfo(
            page: ControllerPageUiModel,
            state: MainUiState
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
        }

        private fun bindBattery(page: ControllerPageUiModel) {
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
            batteryStateText.visibility = View.VISIBLE

            chargingIconView.setGlowStyle(
                color = CHARGING_GLOW_COLOR,
                radiusPx = 11f * itemView.resources.displayMetrics.density
            )
            chargingIconView.setGlowEnabled(isCharging)
        }

        private fun bindSettings(
            page: ControllerPageUiModel,
            state: MainUiState,
            protectBatteryEnabled: Boolean
        ) {
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
        }

        private fun bindSelection(page: ControllerPageUiModel) {
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

    internal data class ControllerPagePayload(
        val deviceInfoChanged: Boolean = false,
        val batteryChanged: Boolean = false,
        val settingsChanged: Boolean = false,
        val selectionChanged: Boolean = false
    ) {
        fun hasChanges(): Boolean {
            return deviceInfoChanged || batteryChanged || settingsChanged || selectionChanged
        }

        fun merge(other: ControllerPagePayload): ControllerPagePayload {
            return ControllerPagePayload(
                deviceInfoChanged = deviceInfoChanged || other.deviceInfoChanged,
                batteryChanged = batteryChanged || other.batteryChanged,
                settingsChanged = settingsChanged || other.settingsChanged,
                selectionChanged = selectionChanged || other.selectionChanged
            )
        }
    }

    private class ControllerPageDiffCallback(
        private val oldPages: List<ControllerPageUiModel>,
        private val newPages: List<ControllerPageUiModel>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldPages.size

        override fun getNewListSize(): Int = newPages.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldPages[oldItemPosition].uniqueId == newPages[newItemPosition].uniqueId
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldPages[oldItemPosition] == newPages[newItemPosition]
        }

        override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any {
            val oldPage = oldPages[oldItemPosition]
            val newPage = newPages[newItemPosition]
            val payload = ControllerPagePayload(
                deviceInfoChanged = oldPage.descriptor != newPage.descriptor ||
                        oldPage.name != newPage.name ||
                        oldPage.isPlaceholder != newPage.isPlaceholder,
                batteryChanged = oldPage.batteryPercent != newPage.batteryPercent ||
                        oldPage.batteryStatus != newPage.batteryStatus,
                selectionChanged = oldPage.isSelected != newPage.isSelected
            )
            LogCompat.d(
                "UI_VERIFY ControllerPageAdapter diffPayload " +
                        "id=${maskControllerPageId(newPage.uniqueId)} payload=$payload"
            )
            return payload
        }
    }

    companion object {
        private const val DEFAULT_SELECTED_VIBE_COLOR = Color.BLUE
        private const val CHARGING_GLOW_COLOR = 0xFFD58C2E.toInt()
    }
}

private fun maskControllerPageId(raw: String): String {
    return if (raw.length <= 12) raw else "${raw.take(6)}...${raw.takeLast(6)}"
}
