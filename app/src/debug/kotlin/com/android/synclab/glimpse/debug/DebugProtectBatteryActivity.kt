package com.android.synclab.glimpse.debug

import android.Manifest
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.android.synclab.glimpse.R
import com.android.synclab.glimpse.di.AppContainer
import com.android.synclab.glimpse.domain.manager.DeveloperOptionManager
import com.android.synclab.glimpse.infra.notification.AppNotificationDispatcher
import com.android.synclab.glimpse.presentation.feature.ProtectBatteryPlanner

class DebugProtectBatteryActivity : AppCompatActivity() {
    private lateinit var statusView: TextView
    private lateinit var developerOptionManager: DeveloperOptionManager
    private lateinit var protectBatteryPlanner: ProtectBatteryPlanner
    private var sendAlertAfterNotificationPermission = false
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted && sendAlertAfterNotificationPermission) {
                postControllerThresholdAlert()
            }
            sendAlertAfterNotificationPermission = false
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug_protect_battery)

        val appContainer = AppContainer.from(applicationContext)
        developerOptionManager = appContainer.provideDeveloperOptionManager()
        protectBatteryPlanner = appContainer.provideProtectBatteryPlanner()
        statusView = findViewById(R.id.debugProtectBatteryStatus)
        findViewById<Button>(R.id.debugMockControllersEnableButton).setOnClickListener {
            setMockControllerPagesEnabled(true)
        }
        findViewById<Button>(R.id.debugMockControllersDisableButton).setOnClickListener {
            setMockControllerPagesEnabled(false)
        }
        findViewById<Button>(R.id.debugProtectBatteryFakeDetectionEnableButton).setOnClickListener {
            setProtectBatteryFakeThresholdDetectionEnabled(true)
        }
        findViewById<Button>(R.id.debugProtectBatteryFakeDetectionDisableButton).setOnClickListener {
            setProtectBatteryFakeThresholdDetectionEnabled(false)
        }
        findViewById<Button>(R.id.debugProtectBatterySendAlertButton).setOnClickListener {
            sendControllerThresholdAlert()
        }

        renderStatus()
    }

    override fun onResume() {
        super.onResume()
        renderStatus()
    }

    private fun setMockControllerPagesEnabled(enabled: Boolean) {
        developerOptionManager.setMockControllerPagesEnabled(enabled)
        renderStatus()
    }

    private fun setProtectBatteryFakeThresholdDetectionEnabled(enabled: Boolean) {
        developerOptionManager.setProtectBatteryFakeThresholdDetectionEnabled(enabled)
        renderStatus()
    }

    private fun renderStatus() {
        val mockControllersStatus = getString(
            if (developerOptionManager.isMockControllerPagesEnabled()) {
                R.string.debug_mock_controllers_status_enabled
            } else {
                R.string.debug_mock_controllers_status_disabled
            }
        )
        val fakeProtectBatteryDetectionStatus = getString(
            if (developerOptionManager.isProtectBatteryFakeThresholdDetectionEnabled()) {
                R.string.debug_mock_controllers_status_enabled
            } else {
                R.string.debug_mock_controllers_status_disabled
            }
        )
        statusView.text = getString(
            R.string.debug_developer_options_status,
            mockControllersStatus,
            fakeProtectBatteryDetectionStatus
        )
    }

    private fun sendControllerThresholdAlert() {
        if (!AppNotificationDispatcher.canPostNotifications(this)) {
            sendAlertAfterNotificationPermission = true
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            Toast.makeText(
                this,
                R.string.toast_protect_battery_notification_permission_required,
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        postControllerThresholdAlert()
    }

    private fun postControllerThresholdAlert() {
        protectBatteryPlanner.postDevControllerThresholdAlert()
        Toast.makeText(
            this,
            R.string.toast_protect_battery_test_alert_sent,
            Toast.LENGTH_SHORT
        ).show()
    }
}
