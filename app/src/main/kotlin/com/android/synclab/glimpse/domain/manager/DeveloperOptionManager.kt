package com.android.synclab.glimpse.domain.manager

import com.android.synclab.glimpse.base.contracts.DeveloperOptionSource
import com.android.synclab.glimpse.utils.LogCompat
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class DeveloperOptionManager(
    private val source: DeveloperOptionSource,
    private val propertyExecutor: Executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "glimpse-dev-options").apply {
            isDaemon = true
        }
    }
) {
    private val developerModeEnabled: Boolean by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        source.isDebuggableApp
    }

    @Volatile
    private var mockControllerPagesEnabled: Boolean = false

    private val mockControllerRefreshInFlight = AtomicBoolean(false)

    init {
        refreshMockControllerPagesEnabledAsync()
    }

    fun isMockControllerPagesEnabled(): Boolean {
        refreshMockControllerPagesEnabledAsync()
        val enabled = developerModeEnabled && mockControllerPagesEnabled
        LogCompat.d(
            "UI_VERIFY DevOptions mock cache read " +
                    "enabled=$enabled cached=$mockControllerPagesEnabled " +
                    "inFlight=${mockControllerRefreshInFlight.get()} " +
                    "thread=${Thread.currentThread().name}"
        )
        return enabled
    }

    private fun refreshMockControllerPagesEnabledAsync() {
        if (!developerModeEnabled) {
            mockControllerPagesEnabled = false
            return
        }
        if (!mockControllerRefreshInFlight.compareAndSet(false, true)) {
            return
        }

        runCatching {
            propertyExecutor.execute {
                val startedAtNanos = System.nanoTime()
                LogCompat.d(
                    "UI_VERIFY DevOptions getprop start " +
                            "property=$MOCK_CONTROLLER_PAGES_PROPERTY " +
                            "thread=${Thread.currentThread().name}"
                )
                try {
                    val propertyValue = runCatching {
                        source.getSystemProperty(MOCK_CONTROLLER_PAGES_PROPERTY)
                    }.getOrNull()
                    mockControllerPagesEnabled = propertyValue == "1"
                    val elapsedMs = (System.nanoTime() - startedAtNanos) / NANOS_PER_MILLISECOND
                    LogCompat.d(
                        "UI_VERIFY DevOptions getprop done " +
                                "value=${propertyValue ?: "null"} " +
                                "enabled=$mockControllerPagesEnabled " +
                                "elapsedMs=$elapsedMs " +
                                "thread=${Thread.currentThread().name}"
                    )
                } finally {
                    mockControllerRefreshInFlight.set(false)
                }
            }
        }.onFailure { throwable ->
            mockControllerRefreshInFlight.set(false)
            LogCompat.w("UI_VERIFY DevOptions getprop schedule failed", throwable)
        }
    }

    companion object {
        private const val MOCK_CONTROLLER_PAGES_PROPERTY = "debug.glimpse.mock_controllers"
        private const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
