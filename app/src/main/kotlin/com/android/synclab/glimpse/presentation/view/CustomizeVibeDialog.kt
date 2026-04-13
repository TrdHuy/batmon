package com.android.synclab.glimpse.presentation.view

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.LayoutInflater
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import com.android.synclab.glimpse.R
import com.android.synclab.glimpse.data.model.ControllerLightCommandResult
import com.android.synclab.glimpse.data.model.ControllerLightCommandStatus
import com.android.synclab.glimpse.domain.usecase.SetPs4ControllerLightColorUseCase
import com.android.synclab.glimpse.utils.LogCompat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CustomizeVibeDialog(
    private val context: Context,
    private val initialColor: Int,
    private val setPs4ControllerLightColorUseCase: SetPs4ControllerLightColorUseCase,
    private val onColorApplied: (Int) -> Unit = {},
    private val onDismiss: () -> Unit = {}
) {
    companion object {
        private const val APPLY_THROTTLE_MS = 16L
        private const val FAILURE_TOAST_COOLDOWN_MS = 1_200L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()

    private var dialog: AlertDialog? = null
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

    val isShowing: Boolean
        get() = dialog?.isShowing == true

    fun show() {
        if (isShowing) {
            return
        }

        val root = LayoutInflater.from(context).inflate(R.layout.dialog_customize_vibe, null)
        val colorPreview = root.findViewById<android.view.View>(R.id.colorPreview)
        val colorHexText = root.findViewById<TextView>(R.id.colorHexText)
        val redValueText = root.findViewById<TextView>(R.id.redValueText)
        val greenValueText = root.findViewById<TextView>(R.id.greenValueText)
        val blueValueText = root.findViewById<TextView>(R.id.blueValueText)
        val redSeekBar = root.findViewById<SeekBar>(R.id.redSeekBar)
        val greenSeekBar = root.findViewById<SeekBar>(R.id.greenSeekBar)
        val blueSeekBar = root.findViewById<SeekBar>(R.id.blueSeekBar)
        val closeButton = root.findViewById<Button>(R.id.closeButton)

        val previewDrawable = (colorPreview.background?.mutate() as? GradientDrawable)
        fun updatePreview(color: Int) {
            if (previewDrawable != null) {
                previewDrawable.setColor(color)
            } else {
                colorPreview.setBackgroundColor(color)
            }
            colorHexText.text = toHexColor(color)
            redValueText.text = Color.red(color).toString()
            greenValueText.text = Color.green(color).toString()
            blueValueText.text = Color.blue(color).toString()
        }

        val seekBarListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) {
                    return
                }
                val color = Color.rgb(
                    redSeekBar.progress.coerceIn(0, 255),
                    greenSeekBar.progress.coerceIn(0, 255),
                    blueSeekBar.progress.coerceIn(0, 255)
                )
                updatePreview(color)
                scheduleColorApply(color)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                flushPendingColorApply()
            }
        }

        redSeekBar.max = 255
        greenSeekBar.max = 255
        blueSeekBar.max = 255

        redSeekBar.setOnSeekBarChangeListener(null)
        greenSeekBar.setOnSeekBarChangeListener(null)
        blueSeekBar.setOnSeekBarChangeListener(null)

        redSeekBar.progress = Color.red(initialColor).coerceIn(0, 255)
        greenSeekBar.progress = Color.green(initialColor).coerceIn(0, 255)
        blueSeekBar.progress = Color.blue(initialColor).coerceIn(0, 255)
        updatePreview(initialColor)

        redSeekBar.setOnSeekBarChangeListener(seekBarListener)
        greenSeekBar.setOnSeekBarChangeListener(seekBarListener)
        blueSeekBar.setOnSeekBarChangeListener(seekBarListener)

        val alertDialog = AlertDialog.Builder(context)
            .setView(root)
            .setCancelable(true)
            .create()

        closeButton.setOnClickListener {
            alertDialog.dismiss()
        }

        alertDialog.setOnDismissListener {
            dispose()
            onDismiss.invoke()
        }

        dialog = alertDialog
        alertDialog.show()
        alertDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        LogCompat.d("CustomizeVibeDialog shown")
    }

    fun dismiss() {
        dialog?.dismiss()
    }

    private fun scheduleColorApply(color: Int) {
        pendingColor = color
        if (isDisposed || isRequestInFlight) {
            return
        }
        mainHandler.removeCallbacks(applyColorRunnable)
        val now = SystemClock.uptimeMillis()
        val elapsed = now - lastDispatchUptimeMs
        val delayMs = if (lastDispatchUptimeMs == 0L || elapsed >= APPLY_THROTTLE_MS) {
            0L
        } else {
            APPLY_THROTTLE_MS - elapsed
        }
        if (delayMs == 0L) {
            mainHandler.post(applyColorRunnable)
        } else {
            mainHandler.postDelayed(applyColorRunnable, delayMs)
        }
    }

    private fun flushPendingColorApply() {
        mainHandler.removeCallbacks(applyColorRunnable)
        if (isRequestInFlight) {
            return
        }
        val color = pendingColor ?: return
        sendColorToController(color)
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
            return
        }

        pendingColor = null
        isRequestInFlight = true
        lastDispatchUptimeMs = SystemClock.uptimeMillis()
        val colorHex = toHexColor(color)
        LogCompat.d("CustomizeVibeDialog apply color=$colorHex")

        worker.execute {
            val result = runCatching {
                setPs4ControllerLightColorUseCase(color)
            }.getOrElse { throwable ->
                LogCompat.e("CustomizeVibeDialog apply failed", throwable)
                ControllerLightCommandResult(
                    status = ControllerLightCommandStatus.FAILED,
                    colorHex = colorHex,
                    detail = throwable.javaClass.simpleName
                )
            }

            mainHandler.post {
                if (isDisposed) {
                    return@post
                }

                isRequestInFlight = false
                handleApplyResult(color = color, result = result)

                val queued = pendingColor
                if (queued != null && queued != color) {
                    scheduleColorApply(queued)
                }
            }
        }
    }

    private fun handleApplyResult(color: Int, result: ControllerLightCommandResult) {
        LogCompat.d(
            "CustomizeVibeDialog result status=${result.status} " +
                    "color=${result.colorHex ?: toHexColor(color)} " +
                    "deviceId=${result.targetDeviceId} detail=${result.detail}"
        )

        if (result.status == ControllerLightCommandStatus.SUCCESS) {
            lastSentColor = color
            lastFailureStatus = null
            onColorApplied.invoke(color)
            return
        }

        if (!shouldShowFailureToast(result.status)) {
            return
        }
        showStatusToast(result.status)
    }

    private fun shouldShowFailureToast(status: ControllerLightCommandStatus): Boolean {
        val now = SystemClock.elapsedRealtime()
        val shouldShow = status != lastFailureStatus ||
                (now - lastFailureToastTime) >= FAILURE_TOAST_COOLDOWN_MS
        if (shouldShow) {
            lastFailureStatus = status
            lastFailureToastTime = now
        }
        return shouldShow
    }

    private fun showStatusToast(status: ControllerLightCommandStatus) {
        @StringRes val messageRes = when (status) {
            ControllerLightCommandStatus.SUCCESS -> return
            ControllerLightCommandStatus.UNSUPPORTED_API -> R.string.toast_customize_vibe_not_supported
            ControllerLightCommandStatus.NO_CONTROLLER -> R.string.toast_customize_vibe_no_controller
            ControllerLightCommandStatus.NO_LIGHT -> R.string.toast_customize_vibe_no_lights
            ControllerLightCommandStatus.PERMISSION_DENIED -> R.string.toast_customize_vibe_permission_denied
            ControllerLightCommandStatus.FAILED -> R.string.toast_customize_vibe_failed
        }
        Toast.makeText(context, messageRes, Toast.LENGTH_SHORT).show()
    }

    private fun toHexColor(color: Int): String {
        return String.format(Locale.US, "#%06X", 0xFFFFFF and color)
    }

    private fun dispose() {
        if (isDisposed) {
            return
        }
        isDisposed = true
        pendingColor = null
        mainHandler.removeCallbacks(applyColorRunnable)
        worker.shutdownNow()
        dialog = null
        LogCompat.d("CustomizeVibeDialog disposed")
    }
}
