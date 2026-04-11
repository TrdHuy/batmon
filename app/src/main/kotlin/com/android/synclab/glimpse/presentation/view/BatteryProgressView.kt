package com.android.synclab.glimpse.presentation.view

import android.animation.ValueAnimator
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
import android.view.animation.LinearInterpolator
import com.android.synclab.glimpse.R
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
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
    private var coreDiameterPx: Float = resources.getDimension(R.dimen.ui_battery_circle_size)
    private var trackThicknessPx: Float = dp(9f)
    private var glowThicknessPx: Float = dp(20f)
    private var glowBlurRadiusPx: Float = dp(4f)
    private var glowOutsetPx: Float = glowBlurRadiusPx + dp(2f)
    private var pulseBlurRadiusPx: Float = dp(8f)
    private var pulseCoreBlurRadiusPx: Float = dp(6f)
    private var pulseGrowRadiusExtraPx: Float = dp(12f)
    private var pulseDecayRadiusExtraPx: Float = dp(9f)
    private var pulseGrowBaseRadiusPx: Float = trackThicknessPx * 0.5f
    private var pulseDecayBaseRadiusPx: Float = trackThicknessPx * 0.75f
    private var pulseMaxRadiusPx: Float = max(
        pulseGrowBaseRadiusPx + pulseGrowRadiusExtraPx,
        pulseDecayBaseRadiusPx + pulseDecayRadiusExtraPx
    )
    private var pulseOutsetPx: Float = pulseMaxRadiusPx + pulseBlurRadiusPx + dp(12f)
    private var drawOutsetPx: Float = max(glowOutsetPx, pulseOutsetPx)

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
        alpha = 160
        maskFilter = BlurMaskFilter(glowBlurRadiusPx, BlurMaskFilter.Blur.NORMAL)
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
        alpha = 160
        maskFilter = BlurMaskFilter(glowBlurRadiusPx, BlurMaskFilter.Blur.NORMAL)
    }
    private val chargingStreakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#FFFFFF")
        strokeWidth = trackThicknessPx + dp(2f)
        alpha = 255
        maskFilter = BlurMaskFilter(dp(6f), BlurMaskFilter.Blur.NORMAL)
    }
    private val chargingPulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FFFFFF")
        alpha = 0
        maskFilter = BlurMaskFilter(pulseBlurRadiusPx, BlurMaskFilter.Blur.NORMAL)
    }
    private val chargingPulseCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FFFFFF")
        alpha = 0
        maskFilter = BlurMaskFilter(pulseCoreBlurRadiusPx, BlurMaskFilter.Blur.NORMAL)
    }

    private var chargingAnimationEnabled = false
    private var chargingAnimationPhase = 0f
    private var chargingAnimator: ValueAnimator? = null

    fun setProgressCompat(value: Int, animated: Boolean) {
        progress = value.coerceIn(0, max)
        invalidate()
    }

    fun setChargingAnimationEnabled(enabled: Boolean) {
        if (chargingAnimationEnabled == enabled) {
            return
        }
        chargingAnimationEnabled = enabled
        if (enabled) {
            startChargingAnimator()
        } else {
            stopChargingAnimator()
        }
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (chargingAnimationEnabled) {
            startChargingAnimator()
        }
    }

    override fun onDetachedFromWindow() {
        stopChargingAnimator()
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desired = (coreDiameterPx + (2f * drawOutsetPx)).roundToInt()
        val measuredWidth = resolveSize(desired, widthMeasureSpec)
        val measuredHeight = resolveSize(desired, heightMeasureSpec)
        val side = min(measuredWidth, measuredHeight)
        setMeasuredDimension(side, side)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height).toFloat()
        if (size <= 0f) {
            return
        }

        val cx = width / 2f
        val cy = height / 2f
        val minStroke = min(trackThicknessPx, glowThicknessPx)
        val availableCoreDiameter = (size - (2f * drawOutsetPx)).coerceAtLeast(minStroke + dp(2f))
        val coreDiameter = min(coreDiameterPx, availableCoreDiameter)
        val radius = (coreDiameter / 2f) - (trackThicknessPx / 2f)
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

            // Draw inner value layer on top so the end is not cut by glow.
            canvas.drawArc(arcRect, progressStartAngleDegrees, sweep, false, progressPaint)
            if (progress < max) {
                val progressFraction = progress.toFloat() / max.toFloat()
                progressEndCapPaint.color = colorAt(progressFraction)
                canvas.drawCircle(endX, endY, trackThicknessPx / 2f, progressEndCapPaint)
            }

            // Draw outer glow layer completely first.
            canvas.drawArc(arcRect, progressStartAngleDegrees, sweep, false, progressGlowPaint)
            if (progress < max) {
                val progressFraction = progress.toFloat() / max.toFloat()
                progressGlowEndCapPaint.color = colorAtGlow(progressFraction)
                canvas.drawCircle(endX, endY, glowThicknessPx / 2f, progressGlowEndCapPaint)
            }

            drawChargingAnimation(canvas, sweep, endX, endY)
        }
    }

    private fun drawChargingAnimation(
        canvas: Canvas,
        sweep: Float,
        endX: Float,
        endY: Float
    ) {
        if (!chargingAnimationEnabled || sweep <= 0f) {
            return
        }

        val phase = chargingAnimationPhase.coerceIn(0f, 1f)
        val startAngle = progressStartAngleDegrees
        val endAngle = progressStartAngleDegrees + sweep
        val streakLength = min(44f, sweep)
        val streakStartDelayPhase = 0.10f
        val streakTravelEndPhase = 0.62f
        val tunnelEndPhase = 0.70f
        val pulseDecayEndPhase = 0.99f

        if (phase < streakStartDelayPhase) {
            // Delay a little before streak appears so the cycle feels less abrupt.
            return
        }

        if (phase < streakTravelEndPhase) {
            // Normal run: streak advances with fixed length.
            val normalized =
                ((phase - streakStartDelayPhase) / (streakTravelEndPhase - streakStartDelayPhase))
                    .coerceIn(0f, 1f)
            val streakHead = startAngle + (sweep * normalized)
            val streakTail = max(startAngle, streakHead - streakLength)
            val streakSweep = (streakHead - streakTail).coerceAtLeast(0f)
            if (streakSweep > 0f) {
                canvas.drawArc(arcRect, streakTail, streakSweep, false, chargingStreakPaint)
            }
            return
        }

        val progressFraction = progress.toFloat() / max.toFloat()
        chargingPulsePaint.color = colorAtGlow(progressFraction)

        if (phase < tunnelEndPhase) {
            // Tunnel phase near endpoint: visible streak shrinks smoothly into endpoint.
            val tunnelProgressLinear =
                ((phase - streakTravelEndPhase) / (tunnelEndPhase - streakTravelEndPhase))
                    .coerceIn(0f, 1f)
            val tunnelProgress = 1f - ((1f - tunnelProgressLinear) * (1f - tunnelProgressLinear))
            val visibleStartAtTunnel = max(startAngle, endAngle - streakLength)
            val shrinkingStart = visibleStartAtTunnel + ((endAngle - visibleStartAtTunnel) * tunnelProgress)
            val shrinkingSweep = (endAngle - shrinkingStart).coerceAtLeast(0f)
            if (shrinkingSweep > 0f) {
                canvas.drawArc(arcRect, shrinkingStart, shrinkingSweep, false, chargingStreakPaint)
            }

            // Pulse grows while streak reaches endpoint.
            val pulseGrow = 1f - ((1f - tunnelProgressLinear) * (1f - tunnelProgressLinear))
            val pulseFade = 1f - (pulseGrow * 0.35f)

            chargingPulsePaint.alpha = (185f * pulseFade).toInt().coerceIn(0, 255)
            val pulseRadius = pulseGrowBaseRadiusPx + (pulseGrowRadiusExtraPx * pulseGrow)
            canvas.drawCircle(endX, endY, pulseRadius, chargingPulsePaint)

            chargingPulseCorePaint.alpha = (120f * pulseFade).toInt().coerceIn(0, 255)
            val coreRadius = (trackThicknessPx * 0.25f) + (dp(3f) * pulseGrow)
            canvas.drawCircle(endX, endY, coreRadius, chargingPulseCorePaint)
            return
        }

        if (phase >= pulseDecayEndPhase) {
            // Quiet gap before loop restart to avoid visible hard cut at phase wrap.
            return
        }

        // Decay phase: pulse shrinks and disappears completely before quiet gap.
        val decayProgress =
            ((phase - tunnelEndPhase) / (pulseDecayEndPhase - tunnelEndPhase)).coerceIn(0f, 1f)
        val decayEaseIn = decayProgress * decayProgress * decayProgress
        val decayRemain = 1f - decayProgress

        chargingPulsePaint.alpha = (155f * decayRemain * decayRemain).toInt().coerceIn(0, 255)
        val decayRadius = pulseDecayBaseRadiusPx + (pulseDecayRadiusExtraPx * (1f - decayEaseIn))
        canvas.drawCircle(endX, endY, decayRadius, chargingPulsePaint)

        chargingPulseCorePaint.alpha = (95f * decayRemain * decayRemain).toInt().coerceIn(0, 255)
        val decayCoreRadius = (trackThicknessPx * 0.38f) + (dp(2.5f) * (1f - decayEaseIn))
        canvas.drawCircle(endX, endY, decayCoreRadius, chargingPulseCorePaint)
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

    private fun startChargingAnimator() {
        if (chargingAnimator?.isRunning == true) {
            return
        }
        chargingAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1600L
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            addUpdateListener { animator ->
                chargingAnimationPhase = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopChargingAnimator() {
        chargingAnimator?.cancel()
        chargingAnimator = null
        chargingAnimationPhase = 0f
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
