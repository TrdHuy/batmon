package com.android.synclab.glimpse.presentation.view

import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.android.synclab.glimpse.R
import com.android.synclab.glimpse.presentation.model.SettingItemUiModel
import kotlin.math.roundToInt

class SettingItemAdapter(
    private val onToggleChanged: (SettingItemUiModel.ItemId, Boolean) -> Unit,
    private val onItemClicked: (SettingItemUiModel.ItemId) -> Unit
) : ListAdapter<SettingItemUiModel, SettingItemAdapter.SettingItemViewHolder>(DiffCallback) {

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<SettingItemUiModel>() {
            override fun areItemsTheSame(
                oldItem: SettingItemUiModel,
                newItem: SettingItemUiModel
            ): Boolean = oldItem.id == newItem.id

            override fun areContentsTheSame(
                oldItem: SettingItemUiModel,
                newItem: SettingItemUiModel
            ): Boolean = oldItem == newItem
        }
    }

    private var containerHeightPx: Int = 0

    fun updateContainerHeight(heightPx: Int) {
        if (heightPx <= 0) {
            return
        }
        containerHeightPx = heightPx
        if (itemCount <= 0) {
            return
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SettingItemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_setting, parent, false)
        return SettingItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: SettingItemViewHolder, position: Int) {
        holder.bind(
            item = getItem(position),
            targetHeight = resolveItemHeight(),
            position = position,
            totalCount = itemCount
        )
    }

    private fun resolveItemHeight(): Int? {
        if (containerHeightPx <= 0 || itemCount <= 0) {
            return null
        }
        return containerHeightPx / itemCount
    }

    inner class SettingItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val root: ConstraintLayout = view.findViewById(R.id.settingItemRoot)
        private val icon: ImageView = view.findViewById(R.id.settingItemIcon)
        private val title: TextView = view.findViewById(R.id.settingItemTitle)
        private val subtitle: TextView = view.findViewById(R.id.settingItemSubtitle)
        private val controlContainer: FrameLayout = view.findViewById(R.id.settingItemControlContainer)
        private val toggle: GlimpseToggleView = view.findViewById(R.id.settingItemToggle)
        private val indicator: ImageView = view.findViewById(R.id.settingItemIndicator)
        private val actionIcon: ImageView = view.findViewById(R.id.settingItemActionIcon)
        private val separator: View = view.findViewById(R.id.settingItemSeparator)
        private val density = view.resources.displayMetrics.density

        private var boundItem: SettingItemUiModel? = null

        init {
            root.setOnClickListener {
                val item = boundItem ?: return@setOnClickListener
                when (item.control) {
                    is SettingItemUiModel.Control.Toggle -> {
                        toggle.performClick()
                        it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }

                    is SettingItemUiModel.Control.None,
                    is SettingItemUiModel.Control.Indicator,
                    is SettingItemUiModel.Control.Action -> {
                        onItemClicked(item.id)
                        it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                }
            }
        }

        fun bind(
            item: SettingItemUiModel,
            targetHeight: Int?,
            position: Int,
            totalCount: Int
        ) {
            boundItem = item
            updateItemHeight(targetHeight)
            applyEdgeStyle(position = position, totalCount = totalCount)

            icon.setImageResource(item.iconRes)
            updateIconSize(item.iconWidthDp, item.iconHeightDp)
            title.text = item.title

            val subtitleText = item.subtitle
            if (subtitleText.isNullOrBlank()) {
                subtitle.visibility = View.GONE
                subtitle.text = ""
            } else {
                subtitle.visibility = View.VISIBLE
                subtitle.text = subtitleText
            }

            toggle.visibility = View.GONE
            indicator.visibility = View.GONE
            actionIcon.visibility = View.GONE
            controlContainer.isEnabled = true
            controlContainer.visibility = View.VISIBLE

            when (val control = item.control) {
                is SettingItemUiModel.Control.Toggle -> {
                    controlContainer.visibility = View.VISIBLE
                    toggle.visibility = View.VISIBLE
                    toggle.setOnCheckedChangeListener(null)
                    toggle.isChecked = control.checked
                    toggle.isEnabled = control.enabled
                    toggle.setOnCheckedChangeListener { _, checked ->
                        onToggleChanged(item.id, checked)
                    }
                }

                is SettingItemUiModel.Control.None -> {
                    controlContainer.visibility = View.GONE
                }

                is SettingItemUiModel.Control.Indicator -> {
                    controlContainer.visibility = View.VISIBLE
                    indicator.visibility = View.VISIBLE
                }

                is SettingItemUiModel.Control.Action -> {
                    controlContainer.visibility = View.VISIBLE
                    actionIcon.visibility = View.VISIBLE
                    actionIcon.setImageResource(control.iconRes)
                }
            }
        }

        fun updateItemHeight(targetHeight: Int?) {
            val desiredHeight = targetHeight ?: ViewGroup.LayoutParams.WRAP_CONTENT
            val params = root.layoutParams
            if (params.height == desiredHeight) {
                return
            }
            params.height = desiredHeight
            root.layoutParams = params
        }

        private fun updateIconSize(widthDp: Float, heightDp: Float) {
            val desiredWidth = (widthDp * density).roundToInt()
            val desiredHeight = (heightDp * density).roundToInt()
            val params = icon.layoutParams
            if (params.width == desiredWidth && params.height == desiredHeight) {
                return
            }
            params.width = desiredWidth
            params.height = desiredHeight
            icon.layoutParams = params
        }

        private fun applyEdgeStyle(position: Int, totalCount: Int) {
            val isFirst = position == 0
            val isLast = position == totalCount - 1

            val rippleRes = when {
                totalCount <= 1 -> R.drawable.ripple_setting_item_white_all
                isFirst -> R.drawable.ripple_setting_item_white_top
                isLast -> R.drawable.ripple_setting_item_white_bottom
                else -> R.drawable.ripple_setting_item_white
            }
            root.foreground = AppCompatResources.getDrawable(root.context, rippleRes)
            separator.visibility = if (isLast || totalCount <= 1) View.GONE else View.VISIBLE
        }
    }
}
