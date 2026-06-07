package com.android.synclab.glimpse.presentation.model

import android.content.Context
import com.android.synclab.glimpse.R

class FeatureGuideContentProvider(
    private val context: Context
) {
    fun getItems(): List<FeatureGuideItem> {
        return listOf(
            FeatureGuideItem(
                id = ID_BACKGROUND_MONITORING,
                iconRes = R.drawable.ic_ui_monitor,
                title = context.getString(R.string.feature_guide_background_monitoring_title),
                shortDescription = context.getString(
                    R.string.feature_guide_background_monitoring_short_description
                ),
                whatItDoes = context.getString(
                    R.string.feature_guide_background_monitoring_what_it_does
                ),
                benefits = context.getString(
                    R.string.feature_guide_background_monitoring_benefits
                ),
                howItWorks = context.getString(
                    R.string.feature_guide_background_monitoring_how_it_works
                ),
                notes = context.getString(R.string.feature_guide_background_monitoring_notes)
            ),
            FeatureGuideItem(
                id = ID_LIVE_BATTERY_OVERLAY,
                iconRes = R.drawable.ic_ui_overlay,
                title = context.getString(R.string.feature_guide_live_battery_overlay_title),
                shortDescription = context.getString(
                    R.string.feature_guide_live_battery_overlay_short_description
                ),
                whatItDoes = context.getString(
                    R.string.feature_guide_live_battery_overlay_what_it_does
                ),
                benefits = context.getString(
                    R.string.feature_guide_live_battery_overlay_benefits
                ),
                howItWorks = context.getString(
                    R.string.feature_guide_live_battery_overlay_how_it_works
                ),
                notes = context.getString(R.string.feature_guide_live_battery_overlay_notes)
            ),
            FeatureGuideItem(
                id = ID_CUSTOMIZE_VIBE,
                iconRes = R.drawable.ic_ui_vibe,
                title = context.getString(R.string.feature_guide_customize_vibe_title),
                shortDescription = context.getString(
                    R.string.feature_guide_customize_vibe_short_description
                ),
                whatItDoes = context.getString(R.string.feature_guide_customize_vibe_what_it_does),
                benefits = context.getString(R.string.feature_guide_customize_vibe_benefits),
                howItWorks = context.getString(R.string.feature_guide_customize_vibe_how_it_works),
                notes = context.getString(R.string.feature_guide_customize_vibe_notes)
            ),
            FeatureGuideItem(
                id = ID_PROTECT_BATTERY,
                iconRes = R.drawable.ic_ui_protect_battery,
                title = context.getString(R.string.feature_guide_protect_battery_title),
                shortDescription = context.getString(
                    R.string.feature_guide_protect_battery_short_description
                ),
                whatItDoes = context.getString(R.string.feature_guide_protect_battery_what_it_does),
                benefits = context.getString(R.string.feature_guide_protect_battery_benefits),
                howItWorks = context.getString(R.string.feature_guide_protect_battery_how_it_works),
                notes = context.getString(R.string.feature_guide_protect_battery_notes)
            )
        )
    }

    fun findItem(featureId: String?): FeatureGuideItem? {
        return getItems().firstOrNull { item -> item.id == featureId }
    }

    companion object {
        const val EXTRA_FEATURE_ID = "com.android.synclab.glimpse.extra.FEATURE_ID"
        const val ID_BACKGROUND_MONITORING = "background_monitoring"
        const val ID_LIVE_BATTERY_OVERLAY = "live_battery_overlay"
        const val ID_CUSTOMIZE_VIBE = "customize_vibe"
        const val ID_PROTECT_BATTERY = "protect_battery"
    }
}
