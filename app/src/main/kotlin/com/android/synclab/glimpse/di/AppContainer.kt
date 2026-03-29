package com.android.synclab.glimpse.di

import android.app.Service
import android.content.Context
import com.android.synclab.glimpse.base.contracts.GamepadRepository
import com.android.synclab.glimpse.domain.usecase.GetConnectedPs4ControllersUseCase
import com.android.synclab.glimpse.domain.usecase.GetPrimaryGamepadBatteryUseCase
import com.android.synclab.glimpse.infra.input.InputDeviceGateway
import com.android.synclab.glimpse.infra.notification.MonitoringNotificationController
import com.android.synclab.glimpse.infra.overlay.OverlayWindowController
import com.android.synclab.glimpse.infra.repository.GamepadRepositoryImpl

class AppContainer private constructor(
    appContext: Context
) {
    private val inputDeviceGateway: InputDeviceGateway = InputDeviceGateway(appContext)

    private val gamepadRepository: GamepadRepository =
        GamepadRepositoryImpl(inputDeviceGateway)

    fun provideInputDeviceGateway(): InputDeviceGateway {
        return inputDeviceGateway
    }

    fun provideConnectedPs4ControllersUseCase(): GetConnectedPs4ControllersUseCase {
        return GetConnectedPs4ControllersUseCase(gamepadRepository)
    }

    fun providePrimaryGamepadBatteryUseCase(): GetPrimaryGamepadBatteryUseCase {
        return GetPrimaryGamepadBatteryUseCase(gamepadRepository)
    }

    fun provideOverlayWindowController(context: Context): OverlayWindowController {
        return OverlayWindowController(context)
    }

    fun provideMonitoringNotificationController(
        service: Service,
        channelId: String,
        stopAction: String
    ): MonitoringNotificationController {
        return MonitoringNotificationController(service, channelId, stopAction)
    }

    companion object {
        @Volatile
        private var instance: AppContainer? = null

        fun from(context: Context): AppContainer {
            return instance ?: synchronized(this) {
                instance ?: AppContainer(context.applicationContext).also { instance = it }
            }
        }
    }
}
