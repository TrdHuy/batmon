package com.android.synclab.glimpse.presentation.feature

import com.android.synclab.glimpse.data.model.ControllerLightCommandResult
import com.android.synclab.glimpse.data.model.ControllerLightCommandStatus

class CustomizeVibeApplyController(
    private val applyColor: (Int) -> ControllerLightCommandResult,
    private val onColorApplied: (Int) -> Unit,
    private val onApplyResult: (Int, ControllerLightCommandResult) -> Unit = { _, _ -> },
    private val onApplyFailure: (ControllerLightCommandStatus) -> Unit,
    private val scheduler: Scheduler,
    private val worker: Worker,
    private val clock: Clock,
    private val applyThrottleMs: Long = DEFAULT_APPLY_THROTTLE_MS,
    private val failureToastCooldownMs: Long = DEFAULT_FAILURE_TOAST_COOLDOWN_MS
) {
    interface Scheduler {
        fun post(runnable: Runnable)
        fun postDelayed(runnable: Runnable, delayMs: Long)
        fun removeCallbacks(runnable: Runnable)
    }

    interface Worker {
        fun execute(block: () -> Unit)
        fun shutdownNow()
    }

    interface Clock {
        fun uptimeMillis(): Long
        fun elapsedRealtime(): Long
    }

    private var pendingColor: Int? = null
    private var isRequestInFlight = false
    private var lastSentColor: Int? = null
    private var lastDispatchUptimeMs: Long = 0L
    private var isDisposed = false
    private var lastFailureStatus: ControllerLightCommandStatus? = null
    private var lastFailureToastTime: Long = 0L

    private val applyColorRunnable = Runnable {
        val targetColor = pendingColor ?: return@Runnable
        sendColorToController(targetColor)
    }

    fun scheduleColorApply(color: Int) {
        if (isDisposed) {
            return
        }
        pendingColor = color
        if (isRequestInFlight) {
            return
        }

        scheduler.removeCallbacks(applyColorRunnable)
        val now = clock.uptimeMillis()
        val elapsed = now - lastDispatchUptimeMs
        val delayMs = if (lastDispatchUptimeMs == 0L || elapsed >= applyThrottleMs) {
            0L
        } else {
            applyThrottleMs - elapsed
        }
        if (delayMs == 0L) {
            scheduler.post(applyColorRunnable)
        } else {
            scheduler.postDelayed(applyColorRunnable, delayMs)
        }
    }

    fun flushPendingColorApply() {
        scheduler.removeCallbacks(applyColorRunnable)
        if (isRequestInFlight) {
            return
        }
        val color = pendingColor ?: return
        sendColorToController(color)
    }

    fun dispose() {
        if (isDisposed) {
            return
        }
        isDisposed = true
        pendingColor = null
        scheduler.removeCallbacks(applyColorRunnable)
        worker.shutdownNow()
    }

    private fun sendColorToController(color: Int) {
        if (isDisposed) {
            return
        }
        if (isRequestInFlight) {
            pendingColor = color
            return
        }
        if (lastSentColor == color) {
            pendingColor = null
            return
        }

        pendingColor = null
        isRequestInFlight = true
        lastDispatchUptimeMs = clock.uptimeMillis()

        worker.execute {
            val result = runCatching {
                applyColor(color)
            }.getOrElse { throwable ->
                ControllerLightCommandResult(
                    status = ControllerLightCommandStatus.FAILED,
                    detail = throwable.message ?: throwable::class.java.simpleName
                )
            }
            scheduler.post(
                Runnable {
                    if (isDisposed) {
                        return@Runnable
                    }

                    isRequestInFlight = false
                    handleApplyResult(color = color, result = result)

                    val queued = pendingColor
                    if (queued != null && queued != color) {
                        scheduleColorApply(queued)
                    }
                }
            )
        }
    }

    private fun handleApplyResult(color: Int, result: ControllerLightCommandResult) {
        onApplyResult(color, result)

        if (result.status == ControllerLightCommandStatus.SUCCESS) {
            lastSentColor = color
            lastFailureStatus = null
            onColorApplied(color)
            return
        }

        if (shouldShowFailureToast(result.status)) {
            onApplyFailure(result.status)
        }
    }

    private fun shouldShowFailureToast(status: ControllerLightCommandStatus): Boolean {
        val now = clock.elapsedRealtime()
        val shouldShow = status != lastFailureStatus ||
                (now - lastFailureToastTime) >= failureToastCooldownMs
        if (shouldShow) {
            lastFailureStatus = status
            lastFailureToastTime = now
        }
        return shouldShow
    }

    companion object {
        const val DEFAULT_APPLY_THROTTLE_MS = 16L
        const val DEFAULT_FAILURE_TOAST_COOLDOWN_MS = 1_200L
    }
}
