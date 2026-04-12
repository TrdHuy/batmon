package com.android.synclab.glimpse.presentation.view

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.synclab.glimpse.R
import com.android.synclab.glimpse.presentation.model.SettingItemUiModel

class SettingsPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val titleView: TextView
    private val cardView: ConstraintLayout
    private val recyclerView: RecyclerView

    private var onToggleChanged: (SettingItemUiModel.ItemId, Boolean) -> Unit = { _, _ -> }
    private var onItemClicked: (SettingItemUiModel.ItemId) -> Unit = {}

    private val adapter = SettingItemAdapter(
        onToggleChanged = { itemId, checked -> onToggleChanged(itemId, checked) },
        onItemClicked = { itemId -> onItemClicked(itemId) }
    )

    init {
        inflate(context, R.layout.view_settings_panel, this)
        titleView = findViewById(R.id.panelTitleView)
        cardView = findViewById(R.id.panelCardView)
        recyclerView = findViewById(R.id.panelRecyclerView)

        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
        recyclerView.itemAnimator = null
        recyclerView.isNestedScrollingEnabled = false
        recyclerView.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            adapter.updateContainerHeight(view.height)
        }

        applyAttributes(attrs, defStyleAttr)
    }

    fun setTitle(@StringRes titleRes: Int) {
        titleView.setText(titleRes)
    }

    fun setTitle(title: CharSequence) {
        titleView.text = title
    }

    fun setInteractionHandlers(
        onToggleChanged: (SettingItemUiModel.ItemId, Boolean) -> Unit,
        onItemClicked: (SettingItemUiModel.ItemId) -> Unit
    ) {
        this.onToggleChanged = onToggleChanged
        this.onItemClicked = onItemClicked
    }

    fun submitItems(items: List<SettingItemUiModel>) {
        adapter.submitList(items)
        recyclerView.post {
            adapter.updateContainerHeight(recyclerView.height)
        }
    }

    fun setCardHeight(heightPx: Int) {
        if (heightPx <= 0) {
            return
        }
        val params = cardView.layoutParams
        if (params.height == heightPx) {
            return
        }
        params.height = heightPx
        cardView.layoutParams = params
    }

    fun setCardBackgroundResourceCompat(@DrawableRes drawableRes: Int) {
        cardView.setBackgroundResource(drawableRes)
    }

    private fun applyAttributes(attrs: AttributeSet?, defStyleAttr: Int) {
        val typedArray = context.obtainStyledAttributes(
            attrs,
            R.styleable.SettingsPanelView,
            defStyleAttr,
            0
        )

        typedArray.getString(R.styleable.SettingsPanelView_panelTitle)?.let(::setTitle)

        val cardBackgroundRes = typedArray.getResourceId(
            R.styleable.SettingsPanelView_panelCardBackground,
            0
        )
        if (cardBackgroundRes != 0) {
            setCardBackgroundResourceCompat(cardBackgroundRes)
        }

        val cardHeight = typedArray.getDimensionPixelSize(
            R.styleable.SettingsPanelView_panelCardHeight,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        if (cardHeight > 0) {
            setCardHeight(cardHeight)
        }

        typedArray.recycle()
    }
}
