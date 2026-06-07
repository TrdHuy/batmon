package com.android.synclab.glimpse.presentation

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.synclab.glimpse.R
import com.android.synclab.glimpse.presentation.model.FeatureGuideContentProvider
import com.android.synclab.glimpse.presentation.view.FeatureGuideAdapter
import com.android.synclab.glimpse.utils.LogCompat

class FeatureGuideActivity : AppCompatActivity() {
    private lateinit var contentProvider: FeatureGuideContentProvider
    private lateinit var adapter: FeatureGuideAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feature_guide)

        contentProvider = FeatureGuideContentProvider(this)
        adapter = FeatureGuideAdapter { item ->
            LogCompat.d("Feature guide item clicked id=${item.id}")
            startActivity(
                Intent(this, FeatureDetailActivity::class.java).putExtra(
                    FeatureGuideContentProvider.EXTRA_FEATURE_ID,
                    item.id
                )
            )
        }

        findViewById<View>(R.id.featureGuideBackButton).setOnClickListener {
            LogCompat.d("Feature guide back clicked")
            finish()
        }
        findViewById<RecyclerView>(R.id.featureGuideRecyclerView).apply {
            layoutManager = LinearLayoutManager(this@FeatureGuideActivity)
            adapter = this@FeatureGuideActivity.adapter
        }
        adapter.submitList(contentProvider.getItems())
    }
}
