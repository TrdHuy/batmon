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

    // Chỉ cho phép một async getprop refresh chạy tại một thời điểm.
    private val mockControllerRefreshInFlight = AtomicBoolean(false)

    init {
        refreshMockControllerPagesEnabledAsync()
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
                LogCompat.dDebug {
                    "UI_VERIFY DevOptions getprop start " +
                            "property=$MOCK_CONTROLLER_PAGES_PROPERTY " +
                            "thread=${Thread.currentThread().name}"
                }
                try {
                    val propertyValue = runCatching {
                        source.getSystemProperty(MOCK_CONTROLLER_PAGES_PROPERTY)
                    }.getOrNull()
                    mockControllerPagesEnabled = propertyValue == "1"
                    val elapsedMs = (System.nanoTime() - startedAtNanos) / NANOS_PER_MILLISECOND
                    LogCompat.dDebug {
                        "UI_VERIFY DevOptions getprop done " +
                                "value=${propertyValue ?: "null"} " +
                                "enabled=$mockControllerPagesEnabled " +
                                "elapsedMs=$elapsedMs " +
                                "thread=${Thread.currentThread().name}"
                    }
                } finally {
                    mockControllerRefreshInFlight.set(false)
                }
            }
        }.onFailure { throwable ->
            mockControllerRefreshInFlight.set(false)
            LogCompat.wDebug(throwable) {
                "UI_VERIFY DevOptions getprop schedule failed"
            }
        }
    }

    companion object {
        private const val MOCK_CONTROLLER_PAGES_PROPERTY = "debug.glimpse.mock_controllers"
        private const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
