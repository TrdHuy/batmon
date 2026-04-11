package com.android.synclab.glimpse.presentation.view

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
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
    private val progressGradientStops = floatArrayOf(0.17f, 0.50f, 0.73f, 1.0f)
    private val progressGradientColors = intArrayOf(
        Color.parseColor("#A0B9FF"),
        Color.parseColor("#9FF2F0"),
        Color.parseColor("#F9D3A3"),
        Color.parseColor("#A0B9FF")
    )
    private val glowGradientStops = floatArrayOf(0.17f, 0.50f, 0.66f, 1.0f)
    private val glowGradientColors = intArrayOf(
        Color.parseColor("#2C64FF"),
        Color.parseColor("#2FE6DE"),
        Color.parseColor("#F39327"),
        Color.parseColor("#C18A37")
    )

    init {
        // Needed so blurred glow renders consistently in this custom view.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    var max: Int = 100
        set(value) {
            field = value.coerceAtLeast(1)
            progress = progress.coerceIn(0, field)
            invalidate()
        }

    private var progress: Int = 0
    private var trackThicknessPx: Float = dp(9f)
    private var glowThicknessPx: Float = dp(29f)
    private var glowBlurPaddingPx: Float = dp(6f)

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
        strokeWidth = glowThicknessPx
        alpha = 220
        maskFilter = BlurMaskFilter(dp(4f), BlurMaskFilter.Blur.NORMAL)
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
        alpha = 220
        maskFilter = BlurMaskFilter(dp(4f), BlurMaskFilter.Blur.NORMAL)
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
        val radius = (size / 2f) - (maxStroke / 2f) - glowBlurPaddingPx
        arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        progressPaint.shader = SweepGradient(
            cx,
            cy,
            progressGradientColors,
            progressGradientStops
        ).also { shader ->
            val matrix = Matrix()
            matrix.postRotate(progressStartAngleDegrees, cx, cy)
            shader.setLocalMatrix(matrix)
        }
        progressGlowPaint.shader = SweepGradient(
            cx,
            cy,
            glowGradientColors,
            glowGradientStops
        ).also { shader ->
            val matrix = Matrix()
            matrix.postRotate(progressStartAngleDegrees, cx, cy)
            shader.setLocalMatrix(matrix)
        }

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
                val progressFraction = progress.toFloat() / max.toFloat()
                progressGlowEndCapPaint.color = colorAtGlow(progressFraction)
                canvas.drawCircle(endX, endY, glowThicknessPx / 2f, progressGlowEndCapPaint)
            }

            // Draw inner value layer on top so the end is not cut by glow.
            canvas.drawArc(arcRect, progressStartAngleDegrees, sweep, false, progressPaint)
            if (progress < max) {
                val progressFraction = progress.toFloat() / max.toFloat()
                progressEndCapPaint.color = colorAt(progressFraction)
                canvas.drawCircle(endX, endY, trackThicknessPx / 2f, progressEndCapPaint)
            }
        }
    }

    private fun colorAt(fraction: Float): Int {
        val t = fraction.coerceIn(0f, 1f)
        for (i in 0 until progressGradientStops.size - 1) {
            val start = progressGradientStops[i]
            val end = progressGradientStops[i + 1]
            if (t <= end) {
                val local = if (end > start) (t - start) / (end - start) else 0f
                return blend(progressGradientColors[i], progressGradientColors[i + 1], local)
            }
        }
        return progressGradientColors.last()
    }

    private fun colorAtGlow(fraction: Float): Int {
        val t = fraction.coerceIn(0f, 1f)
        for (i in 0 until glowGradientStops.size - 1) {
            val start = glowGradientStops[i]
            val end = glowGradientStops[i + 1]
            if (t <= end) {
                val local = if (end > start) (t - start) / (end - start) else 0f
                return blend(glowGradientColors[i], glowGradientColors[i + 1], local)
            }
        }
        return glowGradientColors.last()
    }

    private fun blend(from: Int, to: Int, t: Float): Int {
        val clamped = t.coerceIn(0f, 1f)
        val a = (Color.alpha(from) + ((Color.alpha(to) - Color.alpha(from)) * clamped)).toInt()
        val r = (Color.red(from) + ((Color.red(to) - Color.red(from)) * clamped)).toInt()
        val g = (Color.green(from) + ((Color.green(to) - Color.green(from)) * clamped)).toInt()
        val b = (Color.blue(from) + ((Color.blue(to) - Color.blue(from)) * clamped)).toInt()
        return Color.argb(a, r, g, b)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
