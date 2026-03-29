package com.android.synclab.glimpse.infra.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.android.synclab.glimpse.R
import com.android.synclab.glimpse.utils.LogCompat
import kotlin.math.roundToInt

class OverlayWindowController(
    private val context: Context
) {
    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var overlayView: View? = null
    private var overlayTextView: TextView? = null

    fun show(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            LogCompat.w("Overlay not supported below Android O")
            return false
        }

        if (!Settings.canDrawOverlays(context)) {
            LogCompat.w("Overlay permission missing, cannot show overlay")
            return false
        }

        if (overlayView != null) {
            return true
        }

        val view = LayoutInflater.from(context).inflate(R.layout.overlay_battery, null)
        val textView = view.findViewById<TextView>(R.id.overlayBatteryText)

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dpToPx(12)
            y = dpToPx(72)
        }

        return try {
            windowManager.addView(view, params)
            overlayView = view
            overlayTextView = textView
            LogCompat.i("Overlay attached")
            true
        } catch (exception: Exception) {
            LogCompat.w("Failed to attach overlay view", exception)
            overlayView = null
            overlayTextView = null
            false
        }
    }

    fun hide() {
        val currentView = overlayView ?: return
        try {
            windowManager.removeView(currentView)
            LogCompat.i("Overlay removed")
        } catch (exception: Exception) {
            LogCompat.w("Failed to remove overlay view", exception)
        }
        overlayView = null
        overlayTextView = null
    }

    fun updateText(text: String) {
        overlayTextView?.text = text
    }

    fun isVisible(): Boolean {
        return overlayView != null
    }

    private fun dpToPx(dp: Int): Int {
        val density = context.resources.displayMetrics.density
        return (dp * density).roundToInt()
    }
}
