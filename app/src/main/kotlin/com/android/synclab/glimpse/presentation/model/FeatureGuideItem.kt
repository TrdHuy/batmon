package com.android.synclab.glimpse.presentation.model

import androidx.annotation.DrawableRes

data class FeatureGuideItem(
    val id: String,
    @DrawableRes val iconRes: Int,
    val title: String,
    val shortDescription: String,
    val whatItDoes: String,
    val benefits: String,
    val howItWorks: String,
    val notes: String
)
