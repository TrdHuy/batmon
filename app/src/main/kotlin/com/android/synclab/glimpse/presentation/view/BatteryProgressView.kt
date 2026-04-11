package com.android.synclab.glimpse.presentation.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class BatteryProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val progressStartAngleDegrees = -90f

    var max: Int = 100
        set(value) {
            field = value.coerceAtLeast(1)
            progress = progress.coerceIn(0, field)
            invalidate()
        }

    private var progress: Int = 0
    private var trackThicknessPx: Float = dp(9f)
    private var glowThicknessPx: Float = dp(29f)

    private val arcRect = RectF()
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#E8ECF3")
        strokeWidth = trackThicknessPx
    }
    private val progressGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
        color = Color.parseColor("#B000FF")
        alpha = 255
        strokeWidth = glowThicknessPx
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
        color = Color.parseColor("#2C64FF")
        strokeWidth = trackThicknessPx
    }
    private val progressEndCapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#2C64FF")
    }
    private val progressGlowEndCapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#B000FF")
        alpha = 255
    }

    fun setProgressCompat(value: Int, animated: Boolean) {
        progress = value.coerceIn(0, max)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height).toFloat()
        if (size <= 0f) {
            return
        }

        val cx = width / 2f
        val cy = height / 2f
        // Keep the thick debug glow stroke fully inside the view bounds
        // to avoid square-edge clipping on the circle's outer sides.
        val maxStroke = max(trackThicknessPx, glowThicknessPx)
        val radius = (size / 2f) - (maxStroke / 2f)
        arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius)

        canvas.drawArc(arcRect, progressStartAngleDegrees, 360f, false, trackPaint)

        val sweep = (360f * progress.toFloat()) / max.toFloat()
        if (sweep > 0f) {
            var endX = 0f
            var endY = 0f
            if (progress < max) {
                val endAngleRad = Math.toRadians((progressStartAngleDegrees + sweep).toDouble())
                endX = cx + (radius * cos(endAngleRad)).toFloat()
                endY = cy + (radius * sin(endAngleRad)).toFloat()
            }

            // Draw outer glow layer completely first.
            canvas.drawArc(arcRect, progressStartAngleDegrees, sweep, false, progressGlowPaint)
            if (progress < max) {
                canvas.drawCircle(endX, endY, glowThicknessPx / 2f, progressGlowEndCapPaint)
            }

            // Draw inner value layer on top so the end is not cut by glow.
            canvas.drawArc(arcRect, progressStartAngleDegrees, sweep, false, progressPaint)
            if (progress < max) {
                canvas.drawCircle(endX, endY, trackThicknessPx / 2f, progressEndCapPaint)
            }
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
