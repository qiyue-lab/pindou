package com.xlabs.pindou.pattern

import java.util.ArrayDeque
import kotlin.math.roundToInt
import kotlin.math.sqrt

object PatternGenerator {
    fun generate(
        pixels: IntArray,
        width: Int,
        height: Int,
        columns: Int,
        removeDarkBackground: Boolean = true,
        backgroundTolerance: Double = 24.0,
        enhanceDetails: Boolean = true,
    ): BeadPattern {
        require(width > 0 && height > 0) { "Image dimensions must be positive" }
        require(pixels.size == width * height) { "Pixel array does not match image dimensions" }
        val targetColumns = columns.coerceIn(1, 100)
        val targetRows = (targetColumns * height.toDouble() / width).roundToInt().coerceIn(1, 120)
        val sampled = Array(targetColumns * targetRows) { index ->
            val column = index % targetColumns
            val row = index / targetColumns
            sampleArea(pixels, width, height, column, row, targetColumns, targetRows)
        }
        val background = if (removeDarkBackground) {
            darkBackgroundMask(sampled, targetColumns, targetRows, backgroundTolerance)
        } else {
            BooleanArray(sampled.size)
        }
        val cells = quantizeColors(sampled, background, targetColumns, targetRows, enhanceDetails)
        val counts = cells.filterNotNull().groupingBy { it }.eachCount()
        val legend = BeadPalette.colors.mapNotNull { color ->
            counts[color]?.let { PatternLegendEntry(color, it) }
        }
        return BeadPattern(targetColumns, targetRows, cells, legend)
    }

    private fun quantizeColors(
        sampled: Array<SampledPixel>,
        background: BooleanArray,
        columns: Int,
        rows: Int,
        enhanceDetails: Boolean,
    ): List<BeadColor?> {
        if (!enhanceDetails) {
            return sampled.mapIndexed { index, sample ->
                if (!sample.opaque || background[index]) null else ColorMatcher.nearest(sample.rgb)
            }
        }
        val cells = MutableList<BeadColor?>(sampled.size) { null }
        val adjusted = Array(sampled.size) { index ->
            val rgb = sampled[index].rgb
            doubleArrayOf(
                (rgb shr 16 and 0xFF).toDouble(),
                (rgb shr 8 and 0xFF).toDouble(),
                (rgb and 0xFF).toDouble(),
            )
        }

        fun diffuse(index: Int, red: Double, green: Double, blue: Double, weight: Double) {
            if (index !in adjusted.indices || background[index] || !sampled[index].opaque) return
            adjusted[index][0] += red * weight
            adjusted[index][1] += green * weight
            adjusted[index][2] += blue * weight
        }

        for (row in 0 until rows) {
            val direction = if (row % 2 == 0) 1 else -1
            val columnRange = if (direction == 1) 0 until columns else columns - 1 downTo 0
            for (column in columnRange) {
                val index = row * columns + column
                if (background[index] || !sampled[index].opaque) continue
                val current = adjusted[index]
                val color = ColorMatcher.nearest(current[0], current[1], current[2])
                cells[index] = color
                val strength = 0.25
                val redError = (current[0] - (color.rgb shr 16 and 0xFF)) * strength
                val greenError = (current[1] - (color.rgb shr 8 and 0xFF)) * strength
                val blueError = (current[2] - (color.rgb and 0xFF)) * strength
                if (column + direction in 0 until columns) {
                    diffuse(index + direction, redError, greenError, blueError, 7 / 16.0)
                }
                if (row + 1 < rows) {
                    if (column - direction in 0 until columns) {
                        diffuse(index + columns - direction, redError, greenError, blueError, 3 / 16.0)
                    }
                    diffuse(index + columns, redError, greenError, blueError, 5 / 16.0)
                    if (column + direction in 0 until columns) {
                        diffuse(index + columns + direction, redError, greenError, blueError, 1 / 16.0)
                    }
                }
            }
        }
        return removeIsolatedColors(cells, columns, rows)
    }

    private fun removeIsolatedColors(
        cells: List<BeadColor?>,
        columns: Int,
        rows: Int,
    ): List<BeadColor?> {
        val cleaned = cells.toMutableList()
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                val index = row * columns + column
                val color = cells[index] ?: continue
                val neighbors = buildList {
                    for (y in (row - 1).coerceAtLeast(0)..(row + 1).coerceAtMost(rows - 1)) {
                        for (x in (column - 1).coerceAtLeast(0)..(column + 1).coerceAtMost(columns - 1)) {
                            if (x != column || y != row) cells[y * columns + x]?.let(::add)
                        }
                    }
                }
                if (neighbors.any { it == color }) continue
                val majority = neighbors.groupingBy { it }.eachCount().maxByOrNull { it.value } ?: continue
                val required = (neighbors.size * 2 + 2) / 3
                if (majority.value >= required &&
                    sqrt(ColorMatcher.distance(color.rgb, majority.key.rgb)) < 42.0
                ) {
                    cleaned[index] = majority.key
                }
            }
        }
        return cleaned
    }

    private fun sampleArea(
        pixels: IntArray,
        width: Int,
        height: Int,
        column: Int,
        row: Int,
        columns: Int,
        rows: Int,
    ): SampledPixel {
        val startX = (column * width / columns).coerceAtMost(width - 1)
        val endX = ((column + 1) * width / columns).coerceIn(startX + 1, width)
        val startY = (row * height / rows).coerceAtMost(height - 1)
        val endY = ((row + 1) * height / rows).coerceIn(startY + 1, height)
        var red = 0L
        var green = 0L
        var blue = 0L
        var alpha = 0L
        var count = 0
        for (y in startY until endY) {
            for (x in startX until endX) {
                val pixel = pixels[y * width + x]
                val pixelAlpha = (pixel ushr 24) and 0xFF
                red += ((pixel shr 16) and 0xFF) * pixelAlpha.toLong()
                green += ((pixel shr 8) and 0xFF) * pixelAlpha.toLong()
                blue += (pixel and 0xFF) * pixelAlpha.toLong()
                alpha += pixelAlpha
                count++
            }
        }
        if (alpha < count * 32L) return SampledPixel(0, false)
        val weightedAlpha = alpha.coerceAtLeast(1)
        val compositeAlpha = count * 255.0
        val averageRed = (red / weightedAlpha.toDouble() * (alpha / compositeAlpha) + 255 * (1 - alpha / compositeAlpha)).roundToInt()
        val averageGreen = (green / weightedAlpha.toDouble() * (alpha / compositeAlpha) + 255 * (1 - alpha / compositeAlpha)).roundToInt()
        val averageBlue = (blue / weightedAlpha.toDouble() * (alpha / compositeAlpha) + 255 * (1 - alpha / compositeAlpha)).roundToInt()
        return SampledPixel((averageRed shl 16) or (averageGreen shl 8) or averageBlue, true)
    }

    private fun darkBackgroundMask(
        sampled: Array<SampledPixel>,
        columns: Int,
        rows: Int,
        tolerance: Double,
    ): BooleanArray {
        val border = buildList {
            for (column in 0 until columns) {
                add(column)
                add((rows - 1) * columns + column)
            }
            for (row in 1 until rows - 1) {
                add(row * columns)
                add(row * columns + columns - 1)
            }
        }.distinct()
        val darkBorder = border.filter { sampled[it].opaque && luminance(sampled[it].rgb) < 85.0 }
        if (darkBorder.size < border.size / 3) return BooleanArray(sampled.size)
        val backgroundRgb = averageRgb(darkBorder.map { sampled[it].rgb })
        val candidate = BooleanArray(sampled.size) { index ->
            sampled[index].opaque && luminance(sampled[index].rgb) < 95.0 &&
                sqrt(ColorMatcher.distance(sampled[index].rgb, backgroundRgb)) <= tolerance
        }
        val background = BooleanArray(sampled.size)
        val queue = ArrayDeque<Int>()
        border.forEach { index ->
            if (candidate[index] && !background[index]) {
                background[index] = true
                queue.add(index)
            }
        }
        while (queue.isNotEmpty()) {
            val index = queue.removeFirst()
            val column = index % columns
            val row = index / columns
            val neighbors = buildList {
                if (column > 0) add(index - 1)
                if (column < columns - 1) add(index + 1)
                if (row > 0) add(index - columns)
                if (row < rows - 1) add(index + columns)
            }
            neighbors.forEach { neighbor ->
                if (candidate[neighbor] && !background[neighbor]) {
                    background[neighbor] = true
                    queue.add(neighbor)
                }
            }
        }
        return background
    }

    private fun averageRgb(colors: List<Int>): Int {
        var red = 0L
        var green = 0L
        var blue = 0L
        colors.forEach { color ->
            red += (color shr 16) and 0xFF
            green += (color shr 8) and 0xFF
            blue += color and 0xFF
        }
        return ((red / colors.size).toInt() shl 16) or
            ((green / colors.size).toInt() shl 8) or (blue / colors.size).toInt()
    }

    private fun luminance(rgb: Int): Double =
        0.2126 * ((rgb shr 16) and 0xFF) + 0.7152 * ((rgb shr 8) and 0xFF) + 0.0722 * (rgb and 0xFF)

    private data class SampledPixel(val rgb: Int, val opaque: Boolean)
}
