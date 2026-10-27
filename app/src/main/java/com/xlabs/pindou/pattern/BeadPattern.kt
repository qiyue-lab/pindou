package com.xlabs.pindou.pattern

data class PatternLegendEntry(
    val color: BeadColor,
    val count: Int,
)

data class BeadPattern(
    val columns: Int,
    val rows: Int,
    val cells: List<BeadColor?>,
    val legend: List<PatternLegendEntry>,
) {
    val totalBeads: Int = legend.sumOf { it.count }

    fun colorAt(column: Int, row: Int): BeadColor? = cells[row * columns + column]
}
