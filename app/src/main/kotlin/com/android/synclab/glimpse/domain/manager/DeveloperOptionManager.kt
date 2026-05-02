package com.android.synclab.glimpse.domain.manager

import com.android.synclab.glimpse.base.contracts.DeveloperOptionSource

class DeveloperOptionManager(
    private val source: DeveloperOptionSource
) {
    fun isDeveloperModeEnabled(): Boolean {
        return source.isDebuggableApp
    }

    fun isMockControllerPagesEnabled(): Boolean {
        if (!source.isDebuggableApp) {
            return false
        }
        return source.getSystemProperty(MOCK_CONTROLLER_PAGES_PROPERTY) == "1"
    }

    companion object {
        private const val MOCK_CONTROLLER_PAGES_PROPERTY = "debug.glimpse.mock_controllers"
    }
}
