package com.android.synclab.glimpse.presentation.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.Gravity
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.core.graphics.ColorUtils
import kotlin.math.roundToInt

class GlimpseToggleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.checkboxStyle
) : AppCompatCheckBox(context, attrs, defStyleAttr) {

    companion object {
        private val CHECKED_ATTR = intArrayOf(android.R.attr.checked)
    }

    private val density = resources.displayMetrics.density
    private val trackRect = RectF()
    private val trackStrokeRect = RectF()
    private val thumbRect = RectF()
    private val trackFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val trackStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val defaultWidthPx = dpToPx(36f)
    private val defaultHeightPx = dpToPx(18f)
    private val thumbSizePx = dpToPx(14f)
    private val thumbInsetPx = dpToPx(2f)
    private val trackStrokeWidthPx = dpToPxF(1.5f)

    private val checkedTrackColor = 0xFF2C64FF.toInt()
    private val uncheckedTrackStrokeColor = 0xFF2C64FF.toInt()
    private val thumbColor = 0xFFF2F2F2.toInt()

    private var thumbProgress = if (isChecked) 1f else 0f
    private var progressAnimator: ValueAnimator? = null

    init {
        buttonDrawable = null
        background = null
        text = null
        gravity = Gravity.CENTER
        minWidth = 0
        minHeight = 0
        minimumWidth = 0
        minimumHeight = 0
        setPadding(0, 0, 0, 0)
        includeFontPadding = false
        isClickable = true
        isFocusable = true

        val checkedFromXml = if (attrs != null) {
            val typedArray = context.obtainStyledAttributes(attrs, CHECKED_ATTR, defStyleAttr, 0)
            try {
                typedArray.getBoolean(0, isChecked)
            } finally {
                typedArray.recycle()
            }
        } else {
            isChecked
        }

        super.setChecked(checkedFromXml)
        thumbProgress = if (checkedFromXml) 1f else 0f
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val measuredWidth = resolveSize(defaultWidthPx, widthMeasureSpec)
        val measuredHeight = resolveSize(defaultHeightPx, heightMeasureSpec)
        setMeasuredDimension(measuredWidth, measuredHeight)
    }

    override fun setChecked(checked: Boolean) {
        val wasChecked = isChecked
        super.setChecked(checked)

        if (wasChecked == checked) {
            return
        }

        val target = if (checked) 1f else 0f
        if (!isAttachedToWindow || !isLaidOut) {
            progressAnimator?.cancel()
            thumbProgress = target
            invalidate()
            return
        }

        animateThumbTo(target)
    }

    override fun jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState()
        progressAnimator?.cancel()
        thumbProgress = if (isChecked) 1f else 0f
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Keep rendered state in sync with checked state restored by the framework.
        thumbProgress = if (isChecked) 1f else 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val width = width.toFloat()
        val height = height.toFloat()
        val radius = height / 2f

        trackRect.set(0f, 0f, width, height)
        val checkedAlpha = (thumbProgress * 255f).roundToInt().coerceIn(0, 255)
        if (checkedAlpha > 0) {
            trackFillPaint.color = ColorUtils.setAlphaComponent(checkedTrackColor, checkedAlpha)
            canvas.drawRoundRect(trackRect, radius, radius, trackFillPaint)
        }

        val uncheckedAlpha = ((1f - thumbProgress) * 255f).roundToInt().coerceIn(0, 255)
        if (uncheckedAlpha > 0) {
            val inset = trackStrokeWidthPx / 2f
            trackStrokeRect.set(
                inset,
                inset,
                width - inset,
                height - inset
            )
            trackStrokePaint.strokeWidth = trackStrokeWidthPx
            trackStrokePaint.color =
                ColorUtils.setAlphaComponent(uncheckedTrackStrokeColor, uncheckedAlpha)
            canvas.drawRoundRect(trackStrokeRect, radius, radius, trackStrokePaint)
        }

        val travel = width - (2f * thumbInsetPx) - thumbSizePx
        val thumbLeft = thumbInsetPx + (travel * thumbProgress)
        val thumbTop = (height - thumbSizePx) / 2f
        thumbRect.set(thumbLeft, thumbTop, thumbLeft + thumbSizePx, thumbTop + thumbSizePx)
        thumbPaint.color = thumbColor
        canvas.drawOval(thumbRect, thumbPaint)
    }

    private fun animateThumbTo(target: Float) {
        progressAnimator?.cancel()
        progressAnimator = ValueAnimator.ofFloat(thumbProgress, target).apply {
            duration = 240L
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener { animator ->
                thumbProgress = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun dpToPx(dp: Float): Int {
        return (dp * density).toInt()
    }

    private fun dpToPxF(dp: Float): Float {
        return dp * density
    }
}
