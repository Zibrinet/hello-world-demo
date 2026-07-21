package com.zibrinet.split.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchMergeTest {

    @Test
    fun `totals from separate photos add up`() {
        val merged = BatchMerge.mergeSeparatePhotos(
            listOf(
                ParsedReceipt(amountMinor = 12000, currency = "THB"),
                ParsedReceipt(amountMinor = 5600, currency = "THB"),
                ParsedReceipt(amountMinor = 30000, currency = "THB"),
            )
        )
        assertEquals(47600L, merged.parsed.amountMinor)
        assertEquals("THB", merged.parsed.currency)
        assertTrue(merged.summed)
        assertEquals(3, merged.amountsFound)
    }

    @Test
    fun `photo without a total contributes nothing but does not block the sum`() {
        val merged = BatchMerge.mergeSeparatePhotos(
            listOf(
                ParsedReceipt(amountMinor = 12000, currency = "THB"),
                ParsedReceipt(),
                ParsedReceipt(amountMinor = 5600, currency = "THB"),
            )
        )
        assertEquals(17600L, merged.parsed.amountMinor)
        assertEquals(2, merged.amountsFound)
    }

    @Test
    fun `mixed currencies are never summed`() {
        val merged = BatchMerge.mergeSeparatePhotos(
            listOf(
                ParsedReceipt(amountMinor = 12000, currency = "THB"),
                ParsedReceipt(amountMinor = 5600, currency = "EUR"),
            )
        )
        assertEquals(12000L, merged.parsed.amountMinor)
        assertTrue(merged.mixedCurrencies)
        assertFalse(merged.summed)
    }

    @Test
    fun `single photo passes straight through`() {
        val merged = BatchMerge.mergeSeparatePhotos(
            listOf(ParsedReceipt(amountMinor = 4550, currency = "THB", merchant = "7-Eleven"))
        )
        assertEquals(4550L, merged.parsed.amountMinor)
        assertEquals("7-Eleven", merged.parsed.merchant)
        assertFalse(merged.summed)
    }

    @Test
    fun `photos with unknown currency still sum`() {
        val merged = BatchMerge.mergeSeparatePhotos(
            listOf(
                ParsedReceipt(amountMinor = 1000),
                ParsedReceipt(amountMinor = 2000),
            )
        )
        assertEquals(3000L, merged.parsed.amountMinor)
        assertFalse(merged.mixedCurrencies)
    }

    @Test
    fun `date and merchant come from the earliest photo that has them`() {
        val merged = BatchMerge.mergeSeparatePhotos(
            listOf(
                ParsedReceipt(amountMinor = 1000),
                ParsedReceipt(amountMinor = 2000, dateMillis = 42L, merchant = "Cafe"),
                ParsedReceipt(amountMinor = 3000, dateMillis = 99L, merchant = "Other"),
            )
        )
        assertEquals(42L, merged.parsed.dateMillis)
        assertEquals("Cafe", merged.parsed.merchant)
    }

    @Test
    fun `nothing found anywhere yields empty result`() {
        val merged = BatchMerge.mergeSeparatePhotos(listOf(ParsedReceipt(), ParsedReceipt()))
        assertNull(merged.parsed.amountMinor)
        assertEquals(0, merged.amountsFound)
    }
}
