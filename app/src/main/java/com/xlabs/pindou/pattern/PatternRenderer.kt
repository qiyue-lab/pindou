package com.xlabs.pindou.pattern

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.ceil
import kotlin.math.max

object PatternRenderer {
    private const val CELL_SIZE = 48f
    private const val MARGIN = 56f
    private const val HEADER = 112f
    private const val LEGEND_GAP = 28f
    private const val LEGEND_ROW_HEIGHT = 68f
    private const val MAX_EXPORT_DIMENSION = 6500f

    data class Size(val width: Float, val height: Float)

    fun sizeOf(pattern: BeadPattern): Size {
        val width = MARGIN * 2 + pattern.columns * CELL_SIZE
        val legendRows = ceil(pattern.legend.size / legendColumns(width).toDouble()).toInt()
        val height = HEADER + pattern.rows * CELL_SIZE + LEGEND_GAP + 32 +
            legendRows * LEGEND_ROW_HEIGHT + MARGIN
        return Size(width, height)
    }

    fun createBitmap(pattern: BeadPattern, showGrid: Boolean, showSymbols: Boolean): Bitmap {
        val size = sizeOf(pattern)
        val exportScale = minOf(1f, MAX_EXPORT_DIMENSION / max(size.width, size.height))
        val bitmap = Bitmap.createBitmap(
            (size.width * exportScale).toInt(),
            (size.height * exportScale).toInt(),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        canvas.scale(exportScale, exportScale)
        draw(canvas, pattern, showGrid, showSymbols)
        return bitmap
    }

    fun draw(canvas: Canvas, pattern: BeadPattern, showGrid: Boolean, showSymbols: Boolean) {
        val size = sizeOf(pattern)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(54, 50, 63)
            textSize = 24f
        }
        val titlePaint = Paint(textPaint).apply {
            textSize = 30f
            isFakeBoldText = true
        }
        val cellPaint = Paint()
        val gridPaint = Paint().apply { strokeWidth = 1f }
        val symbolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = 19f
            isFakeBoldText = true
        }

        canvas.drawColor(Color.WHITE)
        canvas.drawText("拼豆设计图", MARGIN, 38f, titlePaint)
        canvas.drawText("${pattern.columns} × ${pattern.rows} 颗", MARGIN, 70f, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("共 ${pattern.totalBeads} 颗 · ${pattern.legend.size} 色", size.width - MARGIN, 54f, textPaint)
        textPaint.textAlign = Paint.Align.LEFT

        val gridLeft = MARGIN
        val gridTop = HEADER
        val gridRight = gridLeft + pattern.columns * CELL_SIZE
        val gridBottom = gridTop + pattern.rows * CELL_SIZE
        val coordinatePaint = Paint(textPaint).apply {
            textSize = 17f
            textAlign = Paint.Align.CENTER
            color = Color.rgb(105, 100, 113)
        }

        for (row in 0 until pattern.rows) {
            for (column in 0 until pattern.columns) {
                val color = pattern.colorAt(column, row)
                val left = gridLeft + column * CELL_SIZE
                val top = gridTop + row * CELL_SIZE
                cellPaint.color = color?.let { Color.rgb(it.rgb shr 16 and 0xFF, it.rgb shr 8 and 0xFF, it.rgb and 0xFF) }
                    ?: Color.WHITE
                canvas.drawRect(left, top, left + CELL_SIZE, top + CELL_SIZE, cellPaint)
                if (showSymbols && color != null) {
                    symbolPaint.color = if (relativeLuminance(color.rgb) > 0.48) Color.rgb(45, 42, 52) else Color.WHITE
                    canvas.drawText(color.symbol, left + CELL_SIZE / 2, top + 31f, symbolPaint)
                }
            }
        }

        if (showGrid) {
            for (column in 0..pattern.columns) {
                val x = gridLeft + column * CELL_SIZE
                gridPaint.color = if (column % 5 == 0) Color.rgb(126, 117, 137) else Color.rgb(215, 211, 219)
                gridPaint.strokeWidth = if (column % 5 == 0) 1.8f else 1f
                canvas.drawLine(x, gridTop, x, gridBottom, gridPaint)
            }
            for (row in 0..pattern.rows) {
                val y = gridTop + row * CELL_SIZE
                gridPaint.color = if (row % 5 == 0) Color.rgb(126, 117, 137) else Color.rgb(215, 211, 219)
                gridPaint.strokeWidth = if (row % 5 == 0) 1.8f else 1f
                canvas.drawLine(gridLeft, y, gridRight, y, gridPaint)
            }
        }
        gridPaint.color = Color.rgb(91, 81, 108)
        gridPaint.strokeWidth = 2.5f
        gridPaint.style = Paint.Style.STROKE
        canvas.drawRect(gridLeft, gridTop, gridRight, gridBottom, gridPaint)
        gridPaint.style = Paint.Style.FILL

        for (column in 0 until pattern.columns) {
            canvas.drawText((column + 1).toString(), gridLeft + (column + 0.5f) * CELL_SIZE, gridTop - 10f, coordinatePaint)
        }
        for (row in 0 until pattern.rows) {
            canvas.drawText((row + 1).toString(), gridLeft - 26f, gridTop + (row + 0.65f) * CELL_SIZE, coordinatePaint)
        }

        val legendTop = gridBottom + LEGEND_GAP
        textPaint.textSize = 22f
        textPaint.isFakeBoldText = true
        canvas.drawText("色号 / 用量", MARGIN, legendTop + 20f, textPaint)
        textPaint.isFakeBoldText = false
        val columns = legendColumns(size.width)
        val itemWidth = (size.width - MARGIN * 2) / columns
        pattern.legend.forEachIndexed { index, entry ->
            val column = index % columns
            val row = index / columns
            val left = MARGIN + column * itemWidth
            val top = legendTop + 38 + row * LEGEND_ROW_HEIGHT
            cellPaint.color = Color.rgb(
                entry.color.rgb shr 16 and 0xFF,
                entry.color.rgb shr 8 and 0xFF,
                entry.color.rgb and 0xFF,
            )
            val swatch = RectF(left, top, left + 34f, top + 34f)
            canvas.drawRoundRect(swatch, 5f, 5f, cellPaint)
            gridPaint.color = Color.rgb(180, 175, 185)
            gridPaint.strokeWidth = 1f
            gridPaint.style = Paint.Style.STROKE
            canvas.drawRoundRect(swatch, 5f, 5f, gridPaint)
            gridPaint.style = Paint.Style.FILL
            textPaint.textSize = 17f
            canvas.drawText("${entry.color.code}  ${entry.color.symbol}", left + 43f, top + 16f, textPaint)
            textPaint.textSize = 16f
            canvas.drawText("× ${entry.count}", left + 43f, top + 38f, textPaint)
        }
    }

    private fun legendColumns(width: Float): Int = if (width < 720f) 4 else 6

    private fun relativeLuminance(rgb: Int): Double {
        val red = (rgb shr 16 and 0xFF) / 255.0
        val green = (rgb shr 8 and 0xFF) / 255.0
        val blue = (rgb and 0xFF) / 255.0
        return max(0.0, 0.2126 * red + 0.7152 * green + 0.0722 * blue)
    }
}
