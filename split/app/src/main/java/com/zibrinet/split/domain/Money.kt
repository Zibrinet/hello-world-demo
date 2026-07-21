package com.zibrinet.split.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Minor-unit money helpers. Pure JVM (java.util/java.math only) so they are
 * unit-testable without Android.
 */
object Money {

    /** Fraction digits for an ISO 4217 code; unknown codes fall back to 2. */
    fun fractionDigits(currencyCode: String): Int = try {
        val digits = Currency.getInstance(currencyCode).defaultFractionDigits
        if (digits < 0) 2 else digits
    } catch (_: IllegalArgumentException) {
        2
    }

    /** 123456 minor + THB -> "1234.56" (plain, no symbol or grouping). */
    fun toPlainString(amountMinor: Long, currencyCode: String): String =
        BigDecimal(amountMinor).movePointLeft(fractionDigits(currencyCode)).toPlainString()

    /**
     * Parses user/OCR-entered major-unit text ("1234.56") into minor units.
     * Returns null for blank, malformed, negative, or too-precise input.
     */
    fun parseToMinor(text: String, currencyCode: String): Long? {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return null
        val value = try {
            BigDecimal(cleaned)
        } catch (_: NumberFormatException) {
            return null
        }
        if (value.signum() < 0) return null
        return try {
            value.movePointRight(fractionDigits(currencyCode)).setScale(0).longValueExact()
        } catch (_: ArithmeticException) {
            null
        }
    }

    /** Locale-aware display with currency symbol, e.g. "฿1,234.56". */
    fun format(
        amountMinor: Long,
        currencyCode: String,
        locale: Locale = Locale.getDefault(),
    ): String {
        val digits = fractionDigits(currencyCode)
        val value = BigDecimal(amountMinor).movePointLeft(digits)
        return try {
            val nf = NumberFormat.getCurrencyInstance(locale)
            nf.currency = Currency.getInstance(currencyCode)
            nf.minimumFractionDigits = digits
            nf.maximumFractionDigits = digits
            nf.format(value)
        } catch (_: IllegalArgumentException) {
            "$currencyCode ${value.setScale(digits, RoundingMode.UNNECESSARY).toPlainString()}"
        }
    }

    /** Absolute-value variant for "You owe her X" phrasing. */
    fun formatAbs(
        amountMinor: Long,
        currencyCode: String,
        locale: Locale = Locale.getDefault(),
    ): String = format(if (amountMinor < 0) -amountMinor else amountMinor, currencyCode, locale)
}
