package com.android.synclab.glimpse.presentation.model

import android.content.Context
import android.os.Build
import com.android.synclab.glimpse.BuildConfig
import com.android.synclab.glimpse.R
import com.android.synclab.glimpse.di.AppContainer
import com.android.synclab.glimpse.utils.LogCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReportProblemDiagnosticsCollector(
    private val context: Context
) {
    fun collect(): ReportProblemDiagnostics {
        val appContext = context.applicationContext
        val appContainer = AppContainer.from(appContext)
        val monitoringState = appContainer.provideMonitoringStateProvider()
        val controllers = runCatching {
            appContainer
                .provideConnectedPs4ControllersUseCase()
                .invoke(appContext.getString(R.string.unknown_controller_name))
                .map { controller ->
                    ReportProblemDiagnostics.ControllerDiagnostics(
                        name = controller.name,
                        deviceId = controller.deviceId,
                        vendorId = controller.vendorId,
                        productId = controller.productId,
                        descriptor = controller.descriptor,
                        batteryPercent = controller.batteryPercent,
                        batteryStatus = controller.batteryStatus?.name ?: "UNKNOWN"
                    )
                }
        }.getOrElse { throwable ->
            LogCompat.e("Failed to collect report controller diagnostics", throwable)
            emptyList()
        }

        return ReportProblemDiagnostics(
            appVersion = BuildConfig.VERSION_NAME,
            appVersionCode = BuildConfig.VERSION_CODE,
            packageName = appContext.packageName,
            androidRelease = Build.VERSION.RELEASE ?: "unknown",
            sdkInt = Build.VERSION.SDK_INT,
            manufacturer = Build.MANUFACTURER ?: "unknown",
            model = Build.MODEL ?: "unknown",
            timestamp = SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss Z",
                Locale.US
            ).format(Date()),
            serviceRunning = monitoringState.isServiceRunning,
            monitoringEnabled = monitoringState.isMonitoringEnabled,
            overlayVisible = monitoringState.isOverlayVisible,
            controllers = controllers
        )
    }
}
