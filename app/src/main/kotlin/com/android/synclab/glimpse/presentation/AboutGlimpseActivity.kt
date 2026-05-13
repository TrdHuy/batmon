package com.android.synclab.glimpse.presentation

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.android.synclab.glimpse.BuildConfig
import com.android.synclab.glimpse.R
import com.android.synclab.glimpse.utils.LogCompat
import com.google.android.material.button.MaterialButton

class AboutGlimpseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about_glimpse)

        findViewById<TextView>(R.id.aboutVersionText).text = getString(
            R.string.about_glimpse_version,
            BuildConfig.VERSION_NAME
        )

        findViewById<android.view.View>(R.id.aboutBackButton).setOnClickListener {
            LogCompat.d("About GLIMPSE back clicked")
            finish()
        }
        findViewById<android.view.View>(R.id.aboutInfoButton).setOnClickListener {
            LogCompat.d("About GLIMPSE app info clicked")
            openAppInfo()
        }
        findViewById<MaterialButton>(R.id.reportProblemButton).setOnClickListener {
            LogCompat.d("About GLIMPSE report problem clicked")
            showToast(R.string.toast_report_problem_selected)
        }
        findViewById<MaterialButton>(R.id.checkUpdateButton).setOnClickListener {
            LogCompat.d("About GLIMPSE check update clicked")
            showToast(R.string.toast_check_for_update_selected)
        }
    }

    private fun openAppInfo() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
        runCatching {
            startActivity(intent)
        }.onFailure { throwable ->
            LogCompat.e("Failed to open app info", throwable)
            showToast(R.string.toast_action_failed)
        }
    }

    private fun showToast(messageResId: Int) {
        Toast.makeText(this, messageResId, Toast.LENGTH_SHORT).show()
    }
}
