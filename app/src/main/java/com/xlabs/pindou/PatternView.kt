package com.xlabs.pindou

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.xlabs.pindou.pattern.BeadPattern
import com.xlabs.pindou.pattern.PatternRenderer
import kotlin.math.min

class PatternView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private var pattern: BeadPattern? = null
    private var showGrid = true
    private var showSymbols = true
    private var zoom = 1f
    private var offsetX = 0f
    private var offsetY = 0f
    private var fitScale = 1f
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(105, 100, 113)
        textSize = 18f * resources.displayMetrics.density
        textAlign = Paint.Align.CENTER
    }
    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                zoom = (zoom * detector.scaleFactor).coerceIn(1f, 8f)
                invalidate()
                return true
            }
        })
    private val gestureDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(event: MotionEvent): Boolean {
                resetZoom()
                return true
            }

            override fun onScroll(
                first: MotionEvent?,
                second: MotionEvent,
                distanceX: Float,
                distanceY: Float,
            ): Boolean {
                if (zoom > 1f) {
                    offsetX -= distanceX
                    offsetY -= distanceY
                    invalidate()
                }
                return true
            }
        })

    init {
        setBackgroundColor(Color.rgb(247, 245, 249))
    }

    fun setPattern(value: BeadPattern?) {
        pattern = value
        resetZoom()
    }

    fun setOptions(showGrid: Boolean, showSymbols: Boolean) {
        this.showGrid = showGrid
        this.showSymbols = showSymbols
        invalidate()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        updateFitScale()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val current = pattern
        if (current == null) {
            canvas.drawText("拍照或从相册选择图片", width / 2f, height / 2f - 12f, emptyPaint)
            emptyPaint.textSize = 14f * resources.displayMetrics.density
            canvas.drawText("生成可缩放的拼豆网格图纸", width / 2f, height / 2f + 28f, emptyPaint)
            return
        }
        val size = PatternRenderer.sizeOf(current)
        val scale = fitScale * zoom
        canvas.save()
        canvas.translate(width / 2f + offsetX, height / 2f + offsetY)
        canvas.scale(scale, scale)
        canvas.translate(-size.width / 2f, -size.height / 2f)
        PatternRenderer.draw(canvas, current, showGrid, showSymbols)
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        parent?.requestDisallowInterceptTouchEvent(pattern != null)
        return true
    }

    private fun resetZoom() {
        zoom = 1f
        offsetX = 0f
        offsetY = 0f
        updateFitScale()
        invalidate()
    }

    private fun updateFitScale() {
        val current = pattern ?: return
        val size = PatternRenderer.sizeOf(current)
        if (width > 0 && height > 0) {
            fitScale = min(width / size.width, height / size.height) * 0.96f
        }
    }
}
