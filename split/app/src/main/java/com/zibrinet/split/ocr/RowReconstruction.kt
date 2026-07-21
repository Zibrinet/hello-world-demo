package com.zibrinet.split.ocr

import kotlin.math.abs

/** One OCR line with its position on the image (pixels). */
data class OcrLine(val text: String, val x: Int, val y: Int, val height: Int)

/**
 * ML Kit emits text in *block* order, which on receipts and email screenshots
 * frequently separates a label column ("TOTAL") from its amount column
 * ("45.50") by many lines. Rebuilding visual rows from bounding boxes — group
 * lines whose vertical centers are within ~half a line height, left-to-right
 * within a row — puts labels and their numbers back on the same line, which is
 * what the parsing heuristics need.
 */
fun reconstructRows(lines: List<OcrLine>): String {
    if (lines.isEmpty()) return ""
    val medianHeight = lines.map { it.height }.sorted()[lines.size / 2].coerceAtLeast(1)
    val tolerance = medianHeight * 0.6

    val rows = mutableListOf<MutableList<OcrLine>>()
    for (line in lines.sortedBy { it.y }) {
        val current = rows.lastOrNull()
        val rowCenter = current?.map { it.y }?.average()
        if (current != null && rowCenter != null && abs(rowCenter - line.y) <= tolerance) {
            current.add(line)
        } else {
            rows.add(mutableListOf(line))
        }
    }
    return rows.joinToString("\n") { row ->
        row.sortedBy { it.x }.joinToString("  ") { it.text }
    }
}
