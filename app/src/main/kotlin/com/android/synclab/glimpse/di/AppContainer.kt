package com.android.synclab.glimpse.di

import android.app.Service
import android.content.Context
import com.android.synclab.glimpse.base.contracts.ControllerProfileRepository
import com.android.synclab.glimpse.base.contracts.DeveloperOptionSource
import com.android.synclab.glimpse.base.contracts.GamepadRepository
import com.android.synclab.glimpse.base.contracts.MonitoringStateProvider
import com.android.synclab.glimpse.data.state.MonitoringStateStore
import com.android.synclab.glimpse.domain.manager.DeveloperOptionManager
import com.android.synclab.glimpse.domain.usecase.ClosePs4ControllerLightSessionUseCase
import com.android.synclab.glimpse.domain.usecase.DeleteControllerProfileUseCase
import com.android.synclab.glimpse.domain.usecase.GetConnectedPs4ControllersUseCase
import com.android.synclab.glimpse.domain.usecase.GetControllerProfileUseCase
import com.android.synclab.glimpse.domain.usecase.GetPrimaryGamepadBatteryUseCase
import com.android.synclab.glimpse.domain.usecase.SetPs4ControllerLightColorUseCase
import com.android.synclab.glimpse.domain.usecase.UpsertControllerProfileUseCase
import com.android.synclab.glimpse.infra.input.InputDeviceGateway
import com.android.synclab.glimpse.infra.developer.AndroidDeveloperOptionSource
import com.android.synclab.glimpse.infra.notification.MonitoringNotificationController
import com.android.synclab.glimpse.infra.overlay.OverlayWindowController
import com.android.synclab.glimpse.infra.repository.GamepadRepositoryImpl
import com.android.synclab.glimpse.infra.repository.roomdb.RoomControllerProfileRepository
import com.android.synclab.glimpse.infra.repository.roomdb.core.GlimpseDatabase
import com.android.synclab.glimpse.utils.LogCompat

class AppContainer private constructor(
    appContext: Context
) {
    private val inputDeviceGateway: InputDeviceGateway = InputDeviceGateway(appContext)
    private val glimpseDatabase: GlimpseDatabase = GlimpseDatabase.create(appContext)
    private val developerOptionSource: DeveloperOptionSource =
        AndroidDeveloperOptionSource(
            isDebuggableApp = (appContext.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        )
    private val developerOptionManager: DeveloperOptionManager =
        DeveloperOptionManager(developerOptionSource)

    private val gamepadRepository: GamepadRepository =
        GamepadRepositoryImpl(inputDeviceGateway)
    private val controllerProfileRepository: ControllerProfileRepository =
        RoomControllerProfileRepository(glimpseDatabase.controllerProfileDao())
    private val monitoringStateProvider: MonitoringStateProvider = MonitoringStateStore

    fun provideInputDeviceGateway(): InputDeviceGateway {
        return inputDeviceGateway
    }

    fun provideMonitoringStateProvider(): MonitoringStateProvider {
        return monitoringStateProvider
    }

    fun provideDeveloperOptionManager(): DeveloperOptionManager {
        return developerOptionManager
    }

    fun provideConnectedPs4ControllersUseCase(): GetConnectedPs4ControllersUseCase {
        return GetConnectedPs4ControllersUseCase(gamepadRepository)
    }

    fun providePrimaryGamepadBatteryUseCase(): GetPrimaryGamepadBatteryUseCase {
        return GetPrimaryGamepadBatteryUseCase(gamepadRepository)
    }

    fun provideSetPs4ControllerLightColorUseCase(): SetPs4ControllerLightColorUseCase {
        return SetPs4ControllerLightColorUseCase(gamepadRepository)
    }

    fun provideClosePs4ControllerLightSessionUseCase(): ClosePs4ControllerLightSessionUseCase {
        return ClosePs4ControllerLightSessionUseCase(gamepadRepository)
    }

    fun provideGetControllerProfileUseCase(): GetControllerProfileUseCase {
        return GetControllerProfileUseCase(controllerProfileRepository)
    }

    fun provideUpsertControllerProfileUseCase(): UpsertControllerProfileUseCase {
        return UpsertControllerProfileUseCase(controllerProfileRepository)
    }

    fun provideDeleteControllerProfileUseCase(): DeleteControllerProfileUseCase {
        return DeleteControllerProfileUseCase(controllerProfileRepository)
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
                instance ?: AppContainer(context.applicationContext).also {
                    LogCompat.i("AppContainer created")
                    instance = it
                }
            }
        }
    }
}
