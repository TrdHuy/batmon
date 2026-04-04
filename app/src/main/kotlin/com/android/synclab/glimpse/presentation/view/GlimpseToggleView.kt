package com.android.synclab.glimpse.presentation.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.Gravity
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.core.graphics.ColorUtils

class GlimpseToggleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.checkboxStyle
) : AppCompatCheckBox(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val trackRect = RectF()
    private val thumbRect = RectF()
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val defaultWidthPx = dpToPx(46f)
    private val defaultHeightPx = dpToPx(26f)
    private val thumbSizePx = dpToPx(18f)
    private val thumbInsetPx = dpToPx(4f)

    private val checkedTrackColor = 0xFF33608A.toInt()
    private val uncheckedTrackColor = 0xFF273234.toInt()
    private val checkedThumbColor = 0xFFF8FCFF.toInt()
    private val uncheckedThumbColor = 0xFFF4F7FA.toInt()

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
        thumbProgress = if (isChecked) 1f else 0f
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

    override fun onDraw(canvas: Canvas) {
        val width = width.toFloat()
        val height = height.toFloat()
        val radius = height / 2f

        trackRect.set(0f, 0f, width, height)
        trackPaint.color = ColorUtils.blendARGB(uncheckedTrackColor, checkedTrackColor, thumbProgress)
        canvas.drawRoundRect(trackRect, radius, radius, trackPaint)

        val travel = width - (2f * thumbInsetPx) - thumbSizePx
        val thumbLeft = thumbInsetPx + (travel * thumbProgress)
        val thumbTop = (height - thumbSizePx) / 2f
        thumbRect.set(thumbLeft, thumbTop, thumbLeft + thumbSizePx, thumbTop + thumbSizePx)
        thumbPaint.color = ColorUtils.blendARGB(uncheckedThumbColor, checkedThumbColor, thumbProgress)
        canvas.drawOval(thumbRect, thumbPaint)
    }

    private fun animateThumbTo(target: Float) {
        progressAnimator?.cancel()
        progressAnimator = ValueAnimator.ofFloat(thumbProgress, target).apply {
            duration = 170L
            interpolator = AccelerateDecelerateInterpolator()
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
}
