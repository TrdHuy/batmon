package com.android.synclab.glimpse.debug

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.android.synclab.glimpse.R
import com.android.synclab.glimpse.infra.developer.DeveloperOptionPrefs
import com.android.synclab.glimpse.infra.notification.AppNotificationDispatcher
import com.android.synclab.glimpse.presentation.ProtectBatteryReceiver

class DebugProtectBatteryActivity : AppCompatActivity() {
    private lateinit var statusView: TextView
    private var sendAlertAfterNotificationPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug_protect_battery)

        statusView = findViewById(R.id.debugProtectBatteryStatus)
        findViewById<Button>(R.id.debugProtectBatteryEnableButton).setOnClickListener {
            setProtectBatteryToolsEnabled(true)
        }
        findViewById<Button>(R.id.debugProtectBatteryDisableButton).setOnClickListener {
            setProtectBatteryToolsEnabled(false)
        }
        findViewById<Button>(R.id.debugProtectBatterySendAlertButton).setOnClickListener {
            sendControllerThresholdAlert()
        }

        renderStatus()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_CODE_POST_NOTIFICATIONS) {
            return
        }
        val granted = grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
        if (granted && sendAlertAfterNotificationPermission) {
            sendAlertAfterNotificationPermission = false
            postControllerThresholdAlert()
        } else {
            sendAlertAfterNotificationPermission = false
        }
    }

    private fun setProtectBatteryToolsEnabled(enabled: Boolean) {
        DeveloperOptionPrefs.setProtectBatteryToolsEnabled(
            context = this,
            enabled = enabled
        )
        sendBroadcast(DeveloperOptionPrefs.developerOptionsChangedIntent(this))
        renderStatus()
    }

    private fun renderStatus() {
        val statusRes = if (DeveloperOptionPrefs.isProtectBatteryToolsEnabled(this)) {
            R.string.debug_protect_battery_status_enabled
        } else {
            R.string.debug_protect_battery_status_disabled
        }
        statusView.setText(statusRes)
    }

    private fun sendControllerThresholdAlert() {
        if (!AppNotificationDispatcher.canPostNotifications(this)) {
            sendAlertAfterNotificationPermission = true
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_CODE_POST_NOTIFICATIONS
            )
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
        ProtectBatteryReceiver.postDevControllerThresholdAlert(this)
        Toast.makeText(
            this,
            R.string.toast_protect_battery_test_alert_sent,
            Toast.LENGTH_SHORT
        ).show()
    }

    companion object {
        private const val REQUEST_CODE_POST_NOTIFICATIONS = 41001
    }
}
