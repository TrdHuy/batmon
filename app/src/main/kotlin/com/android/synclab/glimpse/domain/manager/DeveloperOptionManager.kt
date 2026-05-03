package com.android.synclab.glimpse.domain.manager

import com.android.synclab.glimpse.base.contracts.DeveloperOptionSource

class DeveloperOptionManager(
    private val source: DeveloperOptionSource
) {
    private val developerModeEnabled: Boolean by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        source.isDebuggableApp
    }

    private val mockControllerPagesEnabled: Boolean by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        developerModeEnabled && source.getSystemProperty(MOCK_CONTROLLER_PAGES_PROPERTY) == "1"
    }

    fun isDeveloperModeEnabled(): Boolean {
        return developerModeEnabled
    }

    fun isMockControllerPagesEnabled(): Boolean {
        return mockControllerPagesEnabled
    }

    companion object {
        private const val MOCK_CONTROLLER_PAGES_PROPERTY = "debug.glimpse.mock_controllers"
    }
}
