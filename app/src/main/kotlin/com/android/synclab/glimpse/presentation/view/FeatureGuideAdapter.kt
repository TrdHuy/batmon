package com.android.synclab.glimpse.presentation.view

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.android.synclab.glimpse.R
import com.android.synclab.glimpse.presentation.model.FeatureGuideItem

class FeatureGuideAdapter(
    private val onItemClicked: (FeatureGuideItem) -> Unit
) : ListAdapter<FeatureGuideItem, FeatureGuideAdapter.FeatureGuideViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeatureGuideViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_feature_guide, parent, false)
        return FeatureGuideViewHolder(view)
    }

    override fun onBindViewHolder(holder: FeatureGuideViewHolder, position: Int) {
        holder.bind(
            item = getItem(position),
            showSeparator = position < itemCount - 1
        )
    }

    inner class FeatureGuideViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val root: View = view.findViewById(R.id.featureGuideItemRoot)
        private val icon: ImageView = view.findViewById(R.id.featureGuideItemIcon)
        private val title: TextView = view.findViewById(R.id.featureGuideItemTitle)
        private val description: TextView = view.findViewById(R.id.featureGuideItemDescription)
        private val separator: View = view.findViewById(R.id.featureGuideItemSeparator)

        private var boundItem: FeatureGuideItem? = null

        init {
            root.setOnClickListener {
                boundItem?.let(onItemClicked)
            }
        }

        fun bind(item: FeatureGuideItem, showSeparator: Boolean) {
            boundItem = item
            icon.setImageResource(item.iconRes)
            title.text = item.title
            description.text = item.shortDescription
            separator.visibility = if (showSeparator) View.VISIBLE else View.GONE
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<FeatureGuideItem>() {
            override fun areItemsTheSame(
                oldItem: FeatureGuideItem,
                newItem: FeatureGuideItem
            ): Boolean = oldItem.id == newItem.id

            override fun areContentsTheSame(
                oldItem: FeatureGuideItem,
                newItem: FeatureGuideItem
            ): Boolean = oldItem == newItem
        }
    }
}
