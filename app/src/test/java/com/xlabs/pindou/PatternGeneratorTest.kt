package com.xlabs.pindou

import com.xlabs.pindou.pattern.BeadPalette
import com.xlabs.pindou.pattern.ColorMatcher
import com.xlabs.pindou.pattern.PatternGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternGeneratorTest {
    @Test
    fun exactPaletteColorMatchesItself() {
        BeadPalette.colors.forEach { color ->
            assertEquals(color, ColorMatcher.nearest(color.rgb))
        }
    }

    @Test
    fun generatesRowsFromImageAspectRatio() {
        val pattern = PatternGenerator.generate(IntArray(8) { 0xFF336699.toInt() }, 4, 2, 8)

        assertEquals(8, pattern.columns)
        assertEquals(4, pattern.rows)
        assertEquals(32, pattern.totalBeads)
    }

    @Test
    fun removesOnlyDarkBackgroundConnectedToEdge() {
        val pixels = IntArray(49) { 0xFF202126.toInt() }
        for (y in 1..5) {
            for (x in 1..5) {
                pixels[y * 7 + x] = 0xFFE8E8F0.toInt()
            }
        }
        for (y in 3..4) {
            for (x in 3..4) {
                pixels[y * 7 + x] = 0xFF202126.toInt()
            }
        }

        val pattern = PatternGenerator.generate(pixels, 7, 7, 7, removeDarkBackground = true)

        assertNull(pattern.colorAt(0, 0))
        assertNotNull(pattern.colorAt(3, 3))
        assertEquals(25, pattern.totalBeads)
    }

    @Test
    fun transparentPixelsBecomeEmptyCells() {
        val pixels = intArrayOf(0, 0xFFFFFFFF.toInt())

        val pattern = PatternGenerator.generate(pixels, 2, 1, 2, removeDarkBackground = false)

        assertNull(pattern.colorAt(0, 0))
        assertNotNull(pattern.colorAt(1, 0))
        assertEquals(1, pattern.totalBeads)
    }

    @Test
    fun legendCountsEqualNumberOfBeads() {
        val pixels = intArrayOf(
            0xFFFFFFFF.toInt(), 0xFF000000.toInt(),
            0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(),
        )

        val pattern = PatternGenerator.generate(pixels, 2, 2, 2, removeDarkBackground = false)

        assertEquals(4, pattern.totalBeads)
        assertEquals(4, pattern.legend.sumOf { it.count })
        assertTrue(pattern.legend.all { it.count > 0 })
    }

    @Test
    fun goldenFurUsesWarmTransitionColors() {
        val matched = ColorMatcher.nearest(0xC9A56A)

        assertTrue(
            "Expected a golden transition color but was ${matched.code}",
            matched.code in setOf("P48", "P49", "P50", "P57", "P58"),
        )
    }

    @Test
    fun detailEnhancementRetainsColorTransitions() {
        val pixels = IntArray(32) { 0xFFD2AF6B.toInt() }

        val plain = PatternGenerator.generate(
            pixels, 32, 1, 16, removeDarkBackground = false, enhanceDetails = false,
        )
        val enhanced = PatternGenerator.generate(
            pixels, 32, 1, 16, removeDarkBackground = false, enhanceDetails = true,
        )

        assertTrue(enhanced.legend.size > plain.legend.size)
        assertEquals(plain.totalBeads, enhanced.totalBeads)
    }

    @Test
    fun removesIsolatedColorNoise() {
        val pixels = IntArray(9) { 0xFFC99C79.toInt() }
        pixels[4] = 0xFFD77C40.toInt()

        val pattern = PatternGenerator.generate(pixels, 3, 3, 3, removeDarkBackground = false)

        assertEquals("P43", pattern.colorAt(1, 1)?.code)
    }

    @Test
    fun preservesIsolatedHighContrastDetail() {
        val pixels = IntArray(9) { 0xFFFFFFFF.toInt() }
        pixels[4] = 0xFF17181D.toInt()

        val pattern = PatternGenerator.generate(pixels, 3, 3, 3, removeDarkBackground = false)

        assertEquals("P09", pattern.colorAt(1, 1)?.code)
    }

    @Test
    fun supportsTwoHundredColumns() {
        val pattern = PatternGenerator.generate(
            IntArray(400) { 0xFFC9A463.toInt() },
            200,
            2,
            200,
            removeDarkBackground = false,
        )

        assertEquals(200, pattern.columns)
        assertEquals(2, pattern.rows)
        assertEquals(400, pattern.totalBeads)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMismatchedPixelArray() {
        PatternGenerator.generate(IntArray(3), 2, 2, 8)
    }
}
