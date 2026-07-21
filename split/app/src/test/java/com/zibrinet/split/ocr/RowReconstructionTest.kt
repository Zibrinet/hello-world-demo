package com.zibrinet.split.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class RowReconstructionTest {

    @Test
    fun `label and amount columns rejoin on one visual row`() {
        val lines = listOf(
            OcrLine("Mega Mart", 10, 10, 20),
            // Block order puts the whole label column before the amount column.
            OcrLine("Items 3", 10, 100, 20),
            OcrLine("TOTAL", 10, 200, 20),
            OcrLine("45.50", 300, 202, 20),
        )
        assertEquals("Mega Mart\nItems 3\nTOTAL  45.50", reconstructRows(lines))
    }

    @Test
    fun `rows sort top to bottom and left to right`() {
        val lines = listOf(
            OcrLine("right", 200, 50, 20),
            OcrLine("left", 10, 52, 20),
            OcrLine("below", 10, 120, 20),
        )
        assertEquals("left  right\nbelow", reconstructRows(lines))
    }

    @Test
    fun `distinct lines stay distinct`() {
        val lines = listOf(
            OcrLine("a", 10, 0, 20),
            OcrLine("b", 10, 40, 20),
        )
        assertEquals("a\nb", reconstructRows(lines))
    }

    @Test
    fun `empty input yields empty text`() {
        assertEquals("", reconstructRows(emptyList()))
    }

    @Test
    fun `reconstructed columns parse to the right total`() {
        val rows = reconstructRows(
            listOf(
                OcrLine("Coffee Corner", 10, 10, 20),
                OcrLine("Subtotal", 10, 100, 20),
                OcrLine("Total", 10, 140, 20),
                OcrLine("90.00", 300, 101, 20),
                OcrLine("97.00", 300, 141, 20),
            )
        )
        val parsed = ReceiptParser.parse(rows, "THB")
        assertEquals(9700L, parsed.amountMinor)
    }
}
