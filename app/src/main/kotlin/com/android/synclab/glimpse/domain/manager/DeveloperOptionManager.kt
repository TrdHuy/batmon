package com.android.synclab.glimpse.domain.manager

import com.android.synclab.glimpse.base.contracts.DeveloperOptionSource
import com.android.synclab.glimpse.utils.LogCompat
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Đọc các runtime switch chỉ dùng cho developer.
 *
 * Bật mock controller pages trên device đang kết nối bằng:
 *
 * ```
 * adb shell setprop debug.glimpse.mock_controllers 1
 * ```
 *
 * Bật Protect Battery dev tools bằng:
 *
 * ```
 * adb shell setprop debug.glimpse.protect_battery_tools 1
 * ```
 *
 * Tắt mock controller pages bằng:
 *
 * ```
 * adb shell setprop debug.glimpse.mock_controllers 0
 * ```
 *
 * Nếu cần target một device cụ thể, truyền thêm serial, ví dụ:
 *
 * ```
 * adb -s 192.168.1.9:5555 shell setprop debug.glimpse.mock_controllers 1
 * ```
 *
 * Việc đọc system property chạy bất đồng bộ để UI refresh không bị block bởi
 * `getprop`/`waitFor()`. `isMockControllerPagesEnabled()` trả về giá trị cache
 * gần nhất rồi schedule refresh; sau khi `setprop`, lần đọc đầu tiên vẫn có thể
 * trả về giá trị cũ cho đến khi background refresh hoàn tất.
 */
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
    // Cache dùng giữa các thread cho debug.glimpse.mock_controllers == "1".
    private var mockControllerPagesEnabled: Boolean = false

    @Volatile
    // Cache dùng giữa các thread cho debug.glimpse.protect_battery_tools == "1".
    private var protectBatteryToolsEnabled: Boolean = false

    // Chỉ cho phép một async getprop refresh chạy tại một thời điểm.
    private val mockControllerRefreshInFlight = AtomicBoolean(false)
    private val protectBatteryToolsRefreshInFlight = AtomicBoolean(false)

    init {
        refreshMockControllerPagesEnabledAsync()
        refreshProtectBatteryToolsEnabledAsync()
    }

    fun isMockControllerPagesEnabled(): Boolean {
        refreshMockControllerPagesEnabledAsync()
        val enabled = developerModeEnabled && mockControllerPagesEnabled
        LogCompat.dDebug {
            "UI_VERIFY DevOptions mock cache read " +
                    "enabled=$enabled cached=$mockControllerPagesEnabled " +
                    "inFlight=${mockControllerRefreshInFlight.get()} " +
                    "thread=${Thread.currentThread().name}"
        }
        return enabled
    }

    fun isProtectBatteryToolsEnabled(): Boolean {
        refreshProtectBatteryToolsEnabledAsync()
        val enabled = developerModeEnabled && protectBatteryToolsEnabled
        LogCompat.dDebug {
            "UI_VERIFY DevOptions protectBatteryTools cache read " +
                    "enabled=$enabled cached=$protectBatteryToolsEnabled " +
                    "inFlight=${protectBatteryToolsRefreshInFlight.get()} " +
                    "thread=${Thread.currentThread().name}"
        }
        return enabled
    }

    private fun refreshMockControllerPagesEnabledAsync() {
        refreshBooleanPropertyAsync(
            propertyName = MOCK_CONTROLLER_PAGES_PROPERTY,
            label = "mock_controllers",
            inFlight = mockControllerRefreshInFlight,
            setEnabled = { mockControllerPagesEnabled = it }
        )
    }

    private fun refreshProtectBatteryToolsEnabledAsync() {
        refreshBooleanPropertyAsync(
            propertyName = PROTECT_BATTERY_TOOLS_PROPERTY,
            label = "protect_battery_tools",
            inFlight = protectBatteryToolsRefreshInFlight,
            setEnabled = { protectBatteryToolsEnabled = it }
        )
    }

    private fun refreshBooleanPropertyAsync(
        propertyName: String,
        label: String,
        inFlight: AtomicBoolean,
        setEnabled: (Boolean) -> Unit
    ) {
        if (!developerModeEnabled) {
            setEnabled(false)
            return
        }
        if (!inFlight.compareAndSet(false, true)) {
            return
        }

        runCatching {
            propertyExecutor.execute {
                val startedAtNanos = System.nanoTime()
                LogCompat.dDebug {
                    "UI_VERIFY DevOptions getprop start " +
                            "label=$label property=$propertyName " +
                            "thread=${Thread.currentThread().name}"
                }
                try {
                    val propertyValue = runCatching {
                        source.getSystemProperty(propertyName)
                    }.getOrNull()
                    val enabled = propertyValue == "1"
                    setEnabled(enabled)
                    val elapsedMs = (System.nanoTime() - startedAtNanos) / NANOS_PER_MILLISECOND
                    LogCompat.dDebug {
                        "UI_VERIFY DevOptions getprop done " +
                                "label=$label " +
                                "value=${propertyValue ?: "null"} " +
                                "enabled=$enabled " +
                                "elapsedMs=$elapsedMs " +
                                "thread=${Thread.currentThread().name}"
                    }
                } finally {
                    inFlight.set(false)
                }
            }
        }.onFailure { throwable ->
            inFlight.set(false)
            LogCompat.wDebug(throwable) {
                "UI_VERIFY DevOptions getprop schedule failed label=$label"
            }
        }
    }

    companion object {
        private const val MOCK_CONTROLLER_PAGES_PROPERTY = "debug.glimpse.mock_controllers"
        private const val PROTECT_BATTERY_TOOLS_PROPERTY = "debug.glimpse.protect_battery_tools"
        private const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
