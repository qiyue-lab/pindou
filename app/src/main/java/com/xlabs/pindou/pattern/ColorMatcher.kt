package com.xlabs.pindou.pattern

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object ColorMatcher {
    private val paletteLabs = BeadPalette.colors.map { rgbToLab(it.rgb) }

    fun nearest(rgb: Int): BeadColor {
        val lab = rgbToLab(rgb)
        var nearestIndex = 0
        var nearestDistance = Double.MAX_VALUE
        for (index in paletteLabs.indices) {
            val distance = lab.deltaE2000Squared(paletteLabs[index])
            if (distance < nearestDistance) {
                nearestDistance = distance
                nearestIndex = index
            }
        }
        return BeadPalette.colors[nearestIndex]
    }

    fun nearest(red: Double, green: Double, blue: Double): BeadColor {
        val r = red.roundToInt().coerceIn(0, 255)
        val g = green.roundToInt().coerceIn(0, 255)
        val b = blue.roundToInt().coerceIn(0, 255)
        return nearest((r shl 16) or (g shl 8) or b)
    }

    internal fun rgbToLab(rgb: Int): LabColor {
        val red = srgbChannel((rgb shr 16) and 0xFF)
        val green = srgbChannel((rgb shr 8) and 0xFF)
        val blue = srgbChannel(rgb and 0xFF)
        val x = (red * 0.4124564 + green * 0.3575761 + blue * 0.1804375) / 0.95047
        val y = red * 0.2126729 + green * 0.7151522 + blue * 0.0721750
        val z = (red * 0.0193339 + green * 0.1191920 + blue * 0.9503041) / 1.08883
        val fx = labPivot(x)
        val fy = labPivot(y)
        val fz = labPivot(z)
        return LabColor(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
    }

    internal fun distance(first: Int, second: Int): Double =
        rgbToLab(first).distanceSquaredTo(rgbToLab(second))

    private fun srgbChannel(value: Int): Double {
        val channel = value / 255.0
        return if (channel <= 0.04045) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)
    }

    private fun labPivot(value: Double): Double =
        if (value > 0.008856) value.pow(1.0 / 3.0) else 7.787 * value + 16.0 / 116.0
}

internal data class LabColor(val l: Double, val a: Double, val b: Double) {
    fun distanceSquaredTo(other: LabColor): Double {
        val dl = l - other.l
        val da = a - other.a
        val db = b - other.b
        return dl * dl + da * da + db * db
    }

    fun deltaE2000Squared(other: LabColor): Double {
        val c1 = sqrt(a * a + b * b)
        val c2 = sqrt(other.a * other.a + other.b * other.b)
        val averageC = (c1 + c2) / 2
        val c7 = averageC.pow(7)
        val g = 0.5 * (1 - sqrt(c7 / (c7 + 25.0.pow(7))))
        val a1Prime = (1 + g) * a
        val a2Prime = (1 + g) * other.a
        val c1Prime = sqrt(a1Prime * a1Prime + b * b)
        val c2Prime = sqrt(a2Prime * a2Prime + other.b * other.b)
        val h1Prime = hue(a1Prime, b)
        val h2Prime = hue(a2Prime, other.b)
        val deltaL = other.l - l
        val deltaC = c2Prime - c1Prime
        val deltaHue = when {
            c1Prime * c2Prime == 0.0 -> 0.0
            else -> (h2Prime - h1Prime).let { difference ->
                when {
                    difference > 180 -> difference - 360
                    difference < -180 -> difference + 360
                    else -> difference
                }
            }
        }
        val deltaH = 2 * sqrt(c1Prime * c2Prime) * sin(Math.toRadians(deltaHue / 2))
        val averageL = (l + other.l) / 2
        val averageCPrime = (c1Prime + c2Prime) / 2
        val averageHue = when {
            c1Prime * c2Prime == 0.0 -> h1Prime + h2Prime
            kotlin.math.abs(h1Prime - h2Prime) <= 180 -> (h1Prime + h2Prime) / 2
            h1Prime + h2Prime < 360 -> (h1Prime + h2Prime + 360) / 2
            else -> (h1Prime + h2Prime - 360) / 2
        }
        val t = 1 - 0.17 * cosDegrees(averageHue - 30) +
            0.24 * cosDegrees(2 * averageHue) + 0.32 * cosDegrees(3 * averageHue + 6) -
            0.20 * cosDegrees(4 * averageHue - 63)
        val deltaTheta = 30 * exp(-((averageHue - 275) / 25).pow(2))
        val averageC7 = averageCPrime.pow(7)
        val rc = 2 * sqrt(averageC7 / (averageC7 + 25.0.pow(7)))
        val sl = 1 + 0.015 * (averageL - 50).pow(2) / sqrt(20 + (averageL - 50).pow(2))
        val sc = 1 + 0.045 * averageCPrime
        val sh = 1 + 0.015 * averageCPrime * t
        val rt = -sinDegrees(2 * deltaTheta) * rc
        val lightTerm = deltaL / sl
        val chromaTerm = deltaC / sc
        val hueTerm = deltaH / sh
        return lightTerm * lightTerm + chromaTerm * chromaTerm + hueTerm * hueTerm +
            rt * chromaTerm * hueTerm
    }

    private fun hue(a: Double, b: Double): Double {
        if (a == 0.0 && b == 0.0) return 0.0
        val degrees = Math.toDegrees(atan2(b, a))
        return if (degrees >= 0) degrees else degrees + 360
    }

    private fun cosDegrees(value: Double): Double = cos(Math.toRadians(value))

    private fun sinDegrees(value: Double): Double = sin(Math.toRadians(value))
}
