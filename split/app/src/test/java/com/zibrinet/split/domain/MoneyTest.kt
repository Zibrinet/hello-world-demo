package com.zibrinet.split.domain

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {

    @Test
    fun `two digit currencies use two fraction digits`() {
        assertEquals(2, Money.fractionDigits("THB"))
        assertEquals(2, Money.fractionDigits("EUR"))
    }

    @Test
    fun `yen has zero fraction digits`() {
        assertEquals(0, Money.fractionDigits("JPY"))
    }

    @Test
    fun `unknown currency falls back to two digits`() {
        assertEquals(2, Money.fractionDigits("???"))
    }

    @Test
    fun `parse major to minor`() {
        assertEquals(1234L, Money.parseToMinor("12.34", "EUR"))
        assertEquals(1200L, Money.parseToMinor("12", "EUR"))
        assertEquals(12L, Money.parseToMinor("12", "JPY"))
        assertEquals(50L, Money.parseToMinor("0.50", "THB"))
    }

    @Test
    fun `parse rejects bad input`() {
        assertNull(Money.parseToMinor("", "EUR"))
        assertNull(Money.parseToMinor("abc", "EUR"))
        assertNull(Money.parseToMinor("-5", "EUR"))
        assertNull(Money.parseToMinor("12.345", "EUR"))
        assertNull(Money.parseToMinor("1.5", "JPY"))
    }

    @Test
    fun `plain string round trips`() {
        assertEquals("1234.56", Money.toPlainString(123456, "THB"))
        assertEquals("1234", Money.toPlainString(1234, "JPY"))
        assertEquals("0.05", Money.toPlainString(5, "EUR"))
    }

    @Test
    fun `format contains the numeric value`() {
        val formatted = Money.format(123456, "EUR", Locale.US)
        assertTrue(formatted, formatted.contains("1,234.56"))
    }

    @Test
    fun `format abs drops the sign`() {
        val formatted = Money.formatAbs(-123456, "EUR", Locale.US)
        assertTrue(formatted, formatted.contains("1,234.56") && !formatted.contains("-"))
    }
}
