package com.android.synclab.glimpse.presentation.feature

import com.android.synclab.glimpse.data.model.ControllerLightCommandResult
import com.android.synclab.glimpse.data.model.ControllerLightCommandStatus
import java.util.ArrayDeque
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomizeVibeApplyControllerTest {
    private val clock = FakeClock()
    private val scheduler = FakeScheduler()
    private val worker = ManualWorker()
    private val appliedColors = mutableListOf<Int>()
    private val persistedColors = mutableListOf<Int>()
    private val results = mutableListOf<ControllerLightCommandResult>()
    private val failures = mutableListOf<ControllerLightCommandStatus>()

    @Test
    fun scheduleColorApply_successAppliesColorAndCallsCallback() {
        val controller = controller()

        controller.scheduleColorApply(COLOR_RED)
        worker.runNext()

        assertEquals(listOf(COLOR_RED), appliedColors)
        assertEquals(listOf(COLOR_RED), persistedColors)
        assertEquals(listOf(ControllerLightCommandStatus.SUCCESS), results.map { it.status })
    }

    @Test
    fun scheduleColorApply_duplicateSuccessfulColorDoesNotDispatchAgain() {
        val controller = controller()

        controller.scheduleColorApply(COLOR_RED)
        worker.runNext()

        clock.advance(16L)
        controller.scheduleColorApply(COLOR_RED)
        controller.flushPendingColorApply()

        assertEquals(listOf(COLOR_RED), appliedColors)
        assertTrue(worker.isIdle())
    }

    @Test
    fun scheduleColorApply_whileInFlightQueuesLatestColor() {
        val controller = controller()

        controller.scheduleColorApply(COLOR_RED)
        controller.scheduleColorApply(COLOR_GREEN)
        controller.scheduleColorApply(COLOR_BLUE)

        worker.runNext()
        clock.advance(16L)
        scheduler.runDelayed()
        worker.runNext()

        assertEquals(listOf(COLOR_RED, COLOR_BLUE), appliedColors)
        assertEquals(listOf(COLOR_RED, COLOR_BLUE), persistedColors)
    }

    @Test
    fun flushPendingColorApply_dispatchesImmediatelyWithoutWaitingForThrottle() {
        val controller = controller()

        controller.scheduleColorApply(COLOR_RED)
        worker.runNext()

        clock.advance(1L)
        controller.scheduleColorApply(COLOR_GREEN)
        assertTrue(worker.isIdle())

        controller.flushPendingColorApply()
        worker.runNext()

        assertEquals(listOf(COLOR_RED, COLOR_GREEN), appliedColors)
    }

    @Test
    fun failureCallback_respectsCooldownForSameStatus() {
        val controller = controller(
            applyResult = ControllerLightCommandResult(
                status = ControllerLightCommandStatus.NO_CONTROLLER
            )
        )

        controller.scheduleColorApply(COLOR_RED)
        worker.runNext()

        clock.advance(16L)
        controller.scheduleColorApply(COLOR_GREEN)
        worker.runNext()

        clock.advance(1_200L)
        controller.scheduleColorApply(COLOR_BLUE)
        worker.runNext()

        assertEquals(
            listOf(
                ControllerLightCommandStatus.NO_CONTROLLER,
                ControllerLightCommandStatus.NO_CONTROLLER
            ),
            failures
        )
    }

    @Test
    fun disposeClearsPendingWorkAndStopsWorker() {
        val controller = controller()

        controller.scheduleColorApply(COLOR_RED)
        controller.scheduleColorApply(COLOR_GREEN)

        controller.dispose()
        worker.runAll()
        scheduler.runDelayed()

        assertTrue(worker.wasShutdown)
        assertEquals(emptyList<Int>(), appliedColors)
        assertEquals(emptyList<Int>(), persistedColors)
    }

    private fun controller(
        applyResult: ControllerLightCommandResult = ControllerLightCommandResult(
            status = ControllerLightCommandStatus.SUCCESS
        )
    ): CustomizeVibeApplyController {
        return CustomizeVibeApplyController(
            applyColor = { color ->
                appliedColors += color
                applyResult
            },
            onColorApplied = { color -> persistedColors += color },
            onApplyResult = { _, result -> results += result },
            onApplyFailure = { status -> failures += status },
            scheduler = scheduler,
            worker = worker,
            clock = clock
        )
    }

    private class FakeClock : CustomizeVibeApplyController.Clock {
        private var uptimeMs = 1_000L
        private var elapsedMs = 1_000L

        override fun uptimeMillis(): Long = uptimeMs

        override fun elapsedRealtime(): Long = elapsedMs

        fun advance(ms: Long) {
            uptimeMs += ms
            elapsedMs += ms
        }
    }

    private class FakeScheduler : CustomizeVibeApplyController.Scheduler {
        private val delayedTasks = mutableListOf<Pair<Runnable, Long>>()

        override fun post(runnable: Runnable) {
            runnable.run()
        }

        override fun postDelayed(runnable: Runnable, delayMs: Long) {
            delayedTasks += runnable to delayMs
        }

        override fun removeCallbacks(runnable: Runnable) {
            delayedTasks.removeAll { it.first === runnable }
        }

        fun runDelayed() {
            val tasks = delayedTasks.toList()
            delayedTasks.clear()
            tasks.forEach { it.first.run() }
        }
    }

    private class ManualWorker : CustomizeVibeApplyController.Worker {
        private val tasks = ArrayDeque<() -> Unit>()
        var wasShutdown = false
            private set

        override fun execute(block: () -> Unit) {
            if (!wasShutdown) {
                tasks.add(block)
            }
        }

        override fun shutdownNow() {
            wasShutdown = true
            tasks.clear()
        }

        fun runNext() {
            tasks.removeFirst().invoke()
        }

        fun runAll() {
            while (tasks.isNotEmpty()) {
                runNext()
            }
        }

        fun isIdle(): Boolean = tasks.isEmpty()
    }

    private companion object {
        const val COLOR_RED = 0x00FF0000
        const val COLOR_GREEN = 0x0000FF00
        const val COLOR_BLUE = 0x000000FF
    }
}
