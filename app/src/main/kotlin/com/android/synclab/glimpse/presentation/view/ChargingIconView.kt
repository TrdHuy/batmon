package com.android.synclab.glimpse.presentation.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatImageView
import com.android.synclab.glimpse.R
import com.android.synclab.glimpse.utils.LogCompat
import kotlin.math.max
import kotlin.math.min

class ChargingIconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val iconWidthPx = resources.getDimensionPixelSize(R.dimen.ui_charging_icon_width)
    private val iconHeightPx = resources.getDimensionPixelSize(R.dimen.ui_charging_icon_height)

    private val glowLayerView = GlowLayerView(context).apply {
        layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        )
    }

    private val iconView = AppCompatImageView(context).apply {
        layoutParams = LayoutParams(iconWidthPx, iconHeightPx, Gravity.CENTER)
        scaleType = ImageView.ScaleType.FIT_CENTER
    }

    private var iconResId: Int = R.drawable.ic_ui_charging

    init {
        clipChildren = false
        clipToPadding = false
        addView(glowLayerView)
        addView(iconView)
        setIconResource(iconResId)
        setGlowEnabled(false)
    }

    fun setGlowEnabled(enabled: Boolean) {
        glowLayerView.setGlowEnabled(enabled)
    }

    fun setGlowStyle(color: Int, radiusPx: Float) {
        glowLayerView.setGlowStyle(color = color, radiusPx = radiusPx)
    }

    fun setIconResource(@DrawableRes resId: Int) {
        iconResId = resId
        iconView.setImageResource(resId)
        glowLayerView.setIconDrawable(
            AppCompatResources.getDrawable(context, resId),
            iconWidthPx,
            iconHeightPx
        )
    }

    private class GlowLayerView(context: Context) : View(context) {
        private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ambientBlurPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val coreBlurPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        private var sourceBitmap: Bitmap? = null
        private var ambientGlowBitmap: Bitmap? = null
        private var coreGlowBitmap: Bitmap? = null
        private var iconDrawable: Drawable? = null

        private var glowEnabled: Boolean = false
        private var glowColor: Int = 0
        private var glowRadiusPx: Float = 0f
        private var iconWidthPx: Int = 0
        private var iconHeightPx: Int = 0
        private var drawLeft: Float = 0f
        private var drawTop: Float = 0f
        private var ambientGlowLeft: Float = 0f
        private var ambientGlowTop: Float = 0f
        private var coreGlowLeft: Float = 0f
        private var coreGlowTop: Float = 0f

        init {
            setLayerType(LAYER_TYPE_SOFTWARE, null)
        }

        fun setGlowEnabled(enabled: Boolean) {
            if (glowEnabled == enabled) {
                if (enabled) {
                    ensureBitmapsReady(reason = "enable-noop")
                }
                return
            }
            glowEnabled = enabled
            if (enabled) {
                ensureBitmapsReady(reason = "enable")
            }
            LogCompat.dDebug { "UI_VERIFY ChargingIcon glowEnabled=$enabled" }
            invalidate()
        }

        fun setGlowStyle(color: Int, radiusPx: Float) {
            val safeRadius = radiusPx.coerceAtLeast(1f)
            if (glowColor == color && glowRadiusPx == safeRadius) {
                return
            }
            glowColor = color
            glowRadiusPx = safeRadius
            rebuildGlowBitmaps()
            LogCompat.dDebug {
                "UI_VERIFY ChargingIcon glowStyle color=${color.toHexColor()} radius=${safeRadius.toInt()}"
            }
            invalidate()
        }

        fun setIconDrawable(drawable: Drawable?, iconWidthPx: Int, iconHeightPx: Int) {
            iconDrawable = drawable?.mutate()
            this.iconWidthPx = iconWidthPx
            this.iconHeightPx = iconHeightPx
            rebuildBitmaps()
            invalidate()
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            rebuildBitmaps()
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            ensureBitmapsReady(reason = "attach")
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (!glowEnabled) {
                return
            }

            ensureBitmapsReady(reason = "draw")

            if (glowRadiusPx <= 0f || glowColor == 0) {
                return
            }

            glowPaint.color = glowColor
            ambientGlowBitmap?.let { ambient ->
                glowPaint.alpha = AMBIENT_GLOW_ALPHA
                canvas.drawBitmap(ambient, ambientGlowLeft, ambientGlowTop, glowPaint)
            }
            coreGlowBitmap?.let { core ->
                glowPaint.alpha = CORE_GLOW_ALPHA
                canvas.drawBitmap(core, coreGlowLeft, coreGlowTop, glowPaint)
            }
            glowPaint.alpha = 255
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            LogCompat.dDebug {
                "UI_VERIFY ChargingIcon detach recycle glowEnabled=$glowEnabled"
            }
            recycleBitmaps()
        }

        private fun ensureBitmapsReady(reason: String) {
            if (width <= 0 || height <= 0 || iconDrawable == null) {
                return
            }
            val sourceReady = sourceBitmap?.isRecycled == false
            val glowReady = ambientGlowBitmap?.isRecycled == false &&
                    coreGlowBitmap?.isRecycled == false
            val shouldBuildGlow = glowEnabled && glowRadiusPx > 0f
            if (sourceReady && (!shouldBuildGlow || glowReady)) {
                return
            }

            rebuildBitmaps()
            LogCompat.dDebug {
                "UI_VERIFY ChargingIcon ensureBitmaps reason=$reason " +
                        "glowEnabled=$glowEnabled sourceReady=${sourceBitmap?.isRecycled == false} " +
                        "ambientReady=${ambientGlowBitmap?.isRecycled == false} " +
                        "coreReady=${coreGlowBitmap?.isRecycled == false}"
            }
        }

        private fun rebuildBitmaps() {
            recycleBitmaps()

            val viewWidth = width
            val viewHeight = height
            val drawable = iconDrawable ?: return
            if (viewWidth <= 0 || viewHeight <= 0) {
                return
            }

            val targetWidth = max(1, min(iconWidthPx, viewWidth))
            val targetHeight = max(1, min(iconHeightPx, viewHeight))
            drawLeft = ((viewWidth - targetWidth) / 2f)
            drawTop = ((viewHeight - targetHeight) / 2f)

            sourceBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val source = sourceBitmap ?: return
            val sourceCanvas = Canvas(source)
            drawable.setBounds(0, 0, sourceCanvas.width, sourceCanvas.height)
            drawable.draw(sourceCanvas)
            rebuildGlowBitmaps()
            LogCompat.dDebug {
                "UI_VERIFY ChargingIcon bitmaps view=${viewWidth}x$viewHeight " +
                        "icon=${targetWidth}x$targetHeight " +
                        "ambient=${ambientGlowBitmap?.width}x${ambientGlowBitmap?.height} " +
                        "core=${coreGlowBitmap?.width}x${coreGlowBitmap?.height}"
            }
        }

        private fun rebuildGlowBitmaps() {
            recycleGlowBitmaps()

            val source = sourceBitmap ?: return
            if (glowRadiusPx <= 0f) {
                return
            }

            ambientBlurPaint.maskFilter = BlurMaskFilter(
                glowRadiusPx * AMBIENT_GLOW_RADIUS_MULTIPLIER,
                BlurMaskFilter.Blur.NORMAL
            )
            coreBlurPaint.maskFilter = BlurMaskFilter(
                glowRadiusPx * CORE_GLOW_RADIUS_MULTIPLIER,
                BlurMaskFilter.Blur.NORMAL
            )

            val ambientOffset = IntArray(2)
            ambientGlowBitmap = source.extractAlpha(ambientBlurPaint, ambientOffset)
            ambientGlowLeft = drawLeft + ambientOffset[0]
            ambientGlowTop = drawTop + ambientOffset[1]

            val coreOffset = IntArray(2)
            coreGlowBitmap = source.extractAlpha(coreBlurPaint, coreOffset)
            coreGlowLeft = drawLeft + coreOffset[0]
            coreGlowTop = drawTop + coreOffset[1]
        }

        private fun recycleBitmaps() {
            sourceBitmap?.recycle()
            recycleGlowBitmaps()
            sourceBitmap = null
        }

        private fun recycleGlowBitmaps() {
            ambientGlowBitmap?.recycle()
            coreGlowBitmap?.recycle()
            ambientGlowBitmap = null
            coreGlowBitmap = null
        }

        private fun Int.toHexColor(): String {
            return "#${toUInt().toString(16).uppercase().padStart(8, '0')}"
        }

        companion object {
            private const val AMBIENT_GLOW_RADIUS_MULTIPLIER = 1.35f
            private const val CORE_GLOW_RADIUS_MULTIPLIER = 0.55f
            private const val AMBIENT_GLOW_ALPHA = 150
            private const val CORE_GLOW_ALPHA = 215
        }
    }
}
