package com.android.synclab.glimpse.domain.usecase

import com.android.synclab.glimpse.base.contracts.GamepadRepository
import com.android.synclab.glimpse.base.contracts.ProtectBatteryAlertNotifier
import com.android.synclab.glimpse.base.contracts.ProtectBatteryCheckScheduler
import com.android.synclab.glimpse.base.contracts.ProtectBatteryPreferenceStore
import com.android.synclab.glimpse.data.model.ControllerInfo

open class ProtectBatteryUseCases(
    private val preferenceStore: ProtectBatteryPreferenceStore? = null,
    private val gamepadRepository: GamepadRepository? = null,
    private val checkScheduler: ProtectBatteryCheckScheduler? = null,
    private val alertNotifier: ProtectBatteryAlertNotifier? = null
) {
    open fun isEnabled(): Boolean {
        return requirePreferenceStore().getBoolean(
            prefsName = PREFS_NAME,
            key = KEY_ENABLED,
            defaultValue = false
        )
    }

    open fun setEnabled(enabled: Boolean) {
        requirePreferenceStore().putBoolean(
            prefsName = PREFS_NAME,
            key = KEY_ENABLED,
            value = enabled
        )
    }

    open fun getAlertedControllerIds(): Set<String> {
        return requirePreferenceStore().getStringSet(
            prefsName = PREFS_NAME,
            key = KEY_ALERTED_CONTROLLER_IDS,
            defaultValue = emptySet()
        )
    }

    open fun setAlertedControllerIds(controllerIds: Set<String>) {
        requirePreferenceStore().putStringSet(
            prefsName = PREFS_NAME,
            key = KEY_ALERTED_CONTROLLER_IDS,
            value = controllerIds
        )
    }

    open fun getConnectedPs4Controllers(defaultDeviceName: String): List<ControllerInfo> {
        return requireGamepadRepository().getConnectedPs4Controllers(defaultDeviceName)
    }

    open fun scheduleNextCheck() {
        requireCheckScheduler().scheduleNextCheck(CHECK_INTERVAL_MS)
    }

    open fun cancelNextCheck() {
        requireCheckScheduler().cancelNextCheck()
    }

    open fun postThresholdAlert(
        controllerId: String,
        controllerName: String,
        percent: Int
    ) {
        requireAlertNotifier().postThresholdAlert(
            controllerId = controllerId,
            controllerName = controllerName,
            percent = percent
        )
    }

    open fun postDevControllerThresholdAlert(percent: Int) {
        requireAlertNotifier().postDevControllerThresholdAlert(percent)
    }

    private fun requirePreferenceStore(): ProtectBatteryPreferenceStore {
        return requireNotNull(preferenceStore) {
            "ProtectBatteryPreferenceStore is required for production ProtectBatteryUseCases"
        }
    }

    private fun requireGamepadRepository(): GamepadRepository {
        return requireNotNull(gamepadRepository) {
            "GamepadRepository is required for production ProtectBatteryUseCases"
        }
    }

    private fun requireCheckScheduler(): ProtectBatteryCheckScheduler {
        return requireNotNull(checkScheduler) {
            "ProtectBatteryCheckScheduler is required for production ProtectBatteryUseCases"
        }
    }

    private fun requireAlertNotifier(): ProtectBatteryAlertNotifier {
        return requireNotNull(alertNotifier) {
            "ProtectBatteryAlertNotifier is required for production ProtectBatteryUseCases"
        }
    }

    companion object {
        private const val PREFS_NAME = "protect_battery"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_ALERTED_CONTROLLER_IDS = "alerted_controller_ids"
        private const val CHECK_INTERVAL_MS = 60_000L
    }
}
