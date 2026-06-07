package com.android.synclab.glimpse.presentation

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.android.synclab.glimpse.R
import com.android.synclab.glimpse.presentation.model.FeatureGuideContentProvider
import com.android.synclab.glimpse.utils.LogCompat

class FeatureDetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feature_detail)

        findViewById<View>(R.id.featureDetailBackButton).setOnClickListener {
            LogCompat.d("Feature detail back clicked")
            finish()
        }

        val featureId = intent.getStringExtra(FeatureGuideContentProvider.EXTRA_FEATURE_ID)
        val item = FeatureGuideContentProvider(this).findItem(featureId)
        if (item == null) {
            LogCompat.e("Feature detail missing content id=$featureId")
            Toast.makeText(this, R.string.toast_action_failed, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        title = item.title
        findViewById<ImageView>(R.id.featureDetailIcon).setImageResource(item.iconRes)
        findViewById<TextView>(R.id.featureDetailTitle).text = item.title
        findViewById<TextView>(R.id.featureDetailSubtitle).text = item.shortDescription
        findViewById<TextView>(R.id.featureDetailWhatItDoesText).text = item.whatItDoes
        findViewById<TextView>(R.id.featureDetailBenefitsText).text = item.benefits
        findViewById<TextView>(R.id.featureDetailHowItWorksText).text = item.howItWorks
        findViewById<TextView>(R.id.featureDetailNotesText).text = item.notes
    }
}
