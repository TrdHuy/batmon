package com.android.synclab.glimpse.presentation.view

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.text.TextUtils
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.LinearInterpolator
import androidx.appcompat.widget.AppCompatTextView
import kotlin.math.abs
import kotlin.math.max

class AutoScrollEllipsizeTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private val restartRunnable = Runnable {
        startScrollIfNeeded()
    }

    private var animator: ValueAnimator? = null
    private var isScrolling = false
    private var scrollOffset = 0f
    private var ignoreAnimationEnd = false
    private var isTrackingTap = false
    private var downX = 0f
    private var downY = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    init {
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }

    override fun onTextChanged(
        text: CharSequence?,
        start: Int,
        lengthBefore: Int,
        lengthAfter: Int
    ) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
        resetAndSchedule()
    }

    override fun onSizeChanged(
        width: Int,
        height: Int,
        oldWidth: Int,
        oldHeight: Int
    ) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        resetAndSchedule()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        resetAndSchedule()
    }

    override fun onDetachedFromWindow() {
        stopScroll()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) {
            resetAndSchedule()
        } else {
            stopScroll()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isOverflowing()) {
            return super.onTouchEvent(event)
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isTrackingTap = true
                downX = event.x
                downY = event.y
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (isBeyondTapSlop(event)) {
                    isTrackingTap = false
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val shouldHandleTap = isTrackingTap && !isBeyondTapSlop(event)
                isTrackingTap = false
                if (shouldHandleTap) {
                    performClick()
                    return true
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                isTrackingTap = false
            }
        }

        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        val handled = super.performClick()
        if (!isOverflowing()) {
            return handled
        }

        resetAndSchedule(USER_PAUSE_DELAY_MS)
        return true
    }

    override fun onDraw(canvas: Canvas) {
        if (!isScrolling || !isOverflowing()) {
            super.onDraw(canvas)
            return
        }

        val value = text?.toString().orEmpty()
        if (value.isEmpty()) {
            return
        }

        val saveCount = canvas.save()
        val left = compoundPaddingLeft
        val right = width - compoundPaddingRight
        canvas.clipRect(left, 0, right, height)

        val fontMetrics = paint.fontMetrics
        val baseline = (height - fontMetrics.bottom - fontMetrics.top) / 2f
        val primaryX = left - scrollOffset
        val repeatedX = primaryX + scrollCycleDistance()
        canvas.drawText(value, primaryX, baseline, paint)
        canvas.drawText(value, repeatedX, baseline, paint)
        canvas.restoreToCount(saveCount)
    }

    private fun resetAndSchedule() {
        resetAndSchedule(INITIAL_DELAY_MS)
    }

    private fun resetAndSchedule(delayMs: Long) {
        stopScroll()
        if (!isAttachedToWindow || visibility != VISIBLE || width <= 0) {
            return
        }
        if (!isOverflowing()) {
            return
        }
        postDelayed(restartRunnable, delayMs)
    }

    private fun startScrollIfNeeded() {
        if (!isAttachedToWindow || visibility != VISIBLE || !isOverflowing()) {
            return
        }

        val cycleDistance = scrollCycleDistance()
        if (cycleDistance <= 0f) {
            return
        }

        isScrolling = true
        scrollOffset = 0f
        animator = ValueAnimator.ofFloat(0f, cycleDistance).apply {
            duration = max(MIN_SCROLL_DURATION_MS, (cycleDistance / pixelsPerMs()).toLong())
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            addUpdateListener { animation ->
                scrollOffset = animation.animatedValue as Float
                invalidate()
            }
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) = Unit

                override fun onAnimationEnd(animation: Animator) {
                    animator = null
                    isScrolling = false
                    scrollOffset = 0f
                    invalidate()
                    if (ignoreAnimationEnd) {
                        ignoreAnimationEnd = false
                        return
                    }
                }

                override fun onAnimationCancel(animation: Animator) = Unit

                override fun onAnimationRepeat(animation: Animator) = Unit
            })
            start()
        }
    }

    private fun stopScroll() {
        removeCallbacks(restartRunnable)
        animator?.let {
            ignoreAnimationEnd = true
            it.cancel()
        }
        animator = null
        ignoreAnimationEnd = false
        isScrolling = false
        scrollOffset = 0f
        invalidate()
    }

    private fun isOverflowing(): Boolean {
        val availableWidth = availableTextWidth()
        if (availableWidth <= 0f) {
            return false
        }
        return paint.measureText(text?.toString().orEmpty()) > availableWidth
    }

    private fun scrollCycleDistance(): Float {
        return paint.measureText(text?.toString().orEmpty()) + scrollGapPx()
    }

    private fun availableTextWidth(): Float {
        return (width - compoundPaddingLeft - compoundPaddingRight).toFloat()
    }

    private fun scrollGapPx(): Float {
        return SCROLL_GAP_DP * resources.displayMetrics.density
    }

    private fun pixelsPerMs(): Float {
        return SCROLL_DP_PER_SECOND * resources.displayMetrics.density / MILLIS_PER_SECOND
    }

    private fun isBeyondTapSlop(event: MotionEvent): Boolean {
        return abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop
    }

    companion object {
        private const val INITIAL_DELAY_MS = 1_000L
        private const val USER_PAUSE_DELAY_MS = 3_000L
        private const val MIN_SCROLL_DURATION_MS = 2_400L
        private const val SCROLL_DP_PER_SECOND = 32f
        private const val SCROLL_GAP_DP = 32f
        private const val MILLIS_PER_SECOND = 1_000f
    }
}
