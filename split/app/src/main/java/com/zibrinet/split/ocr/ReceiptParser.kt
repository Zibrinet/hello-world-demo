package com.zibrinet.split.ocr

import com.zibrinet.split.domain.Money
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

/**
 * Heuristic extraction from raw OCR text. This is deliberately best-effort:
 * ML Kit returns unstructured text, receipt layouts vary wildly, and email
 * screenshots are the worst case. Every result is a *guess* that the UI must
 * present for human review — never auto-save from here.
 */
data class ParsedReceipt(
    val amountMinor: Long? = null,
    val currency: String? = null,
    val dateMillis: Long? = null,
    val merchant: String? = null,
) {
    val foundAnything: Boolean
        get() = amountMinor != null || dateMillis != null || merchant != null
}

object ReceiptParser {

    // Total-like keywords across the languages this household actually uses
    // (English, Italian, Thai). Order does not matter; matching is per line.
    private val totalKeywords = listOf(
        "grand total", "amount due", "total due", "balance due", "to pay",
        "total", "amount", "sum",
        "totale", "importo", "da pagare",
        "รวมทั้งสิ้น", "ยอดรวม", "ยอดสุทธิ", "รวมเงิน", "รวม", "สุทธิ",
    )

    // Lines that look like totals but are not what we want.
    private val antiKeywords = listOf(
        "subtotal", "sub-total", "sub total", "vat", "tax", "change", "cash",
        "tip", "service", "discount", "เงินทอน", "เงินสด", "ภาษี", "ส่วนลด",
        "resto", "sconto", "contanti", "iva",
    )

    private val currencyHints = listOf(
        "฿" to "THB", "บาท" to "THB", "baht" to "THB", "thb" to "THB",
        "€" to "EUR", "eur" to "EUR", "euro" to "EUR",
        "£" to "GBP", "gbp" to "GBP",
        "¥" to "JPY", "jpy" to "JPY", "yen" to "JPY",
        "usd" to "USD", "us$" to "USD", "$" to "USD",
    )

    /**
     * Candidate money token: digits with optional thousands/decimal separators.
     * Handles 1,234.56 / 1.234,56 / 1234.56 / 1 234,56 / 1234.
     */
    private val numberRegex = Regex("""\d{1,3}(?:[.,]\d{3})+(?:[.,]\d{1,2})?|\d+(?:[.,]\d{1,2})?""")

    fun parse(rawText: String, homeCurrency: String): ParsedReceipt {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return ParsedReceipt()

        val currency = detectCurrency(rawText) ?: homeCurrency
        val amount = detectTotal(lines, currency)
        val date = detectDate(lines)
        val merchant = detectMerchant(lines)

        return ParsedReceipt(
            amountMinor = amount,
            currency = if (amount != null) currency else detectCurrency(rawText),
            dateMillis = date,
            merchant = merchant,
        )
    }

    private fun detectCurrency(text: String): String? {
        val lower = text.lowercase(Locale.ROOT)
        // Word-ish hints first (more specific than '$').
        for ((hint, code) in currencyHints) {
            if (hint == "$") continue
            if (lower.contains(hint)) return code
        }
        return if (lower.contains("$")) "USD" else null
    }

    private fun detectTotal(lines: List<String>, currency: String): Long? {
        data class Candidate(val minor: Long, val keywordScore: Int)

        val candidates = mutableListOf<Candidate>()
        lines.forEachIndexed { index, line ->
            val lower = line.lowercase(Locale.ROOT)
            if (antiKeywords.any { lower.contains(it) } &&
                !lower.contains("grand total") && !lower.contains("รวมทั้งสิ้น")
            ) {
                return@forEachIndexed
            }
            val keywordHere = totalKeywords.any { lower.contains(it) }
            // OCR often splits a label and its number across adjacent lines.
            val keywordAbove = index > 0 &&
                totalKeywords.any { lines[index - 1].lowercase(Locale.ROOT).contains(it) } &&
                antiKeywords.none { lines[index - 1].lowercase(Locale.ROOT).contains(it) }

            val score = when {
                keywordHere -> 2
                keywordAbove -> 1
                else -> 0
            }
            for (match in numberRegex.findAll(line)) {
                val minor = parseAmountToken(match.value, currency) ?: continue
                if (minor <= 0L) continue
                if (looksLikeNonAmount(match.value, line)) continue
                candidates += Candidate(minor, score)
            }
        }
        if (candidates.isEmpty()) return null

        // Strongest keyword evidence wins first (a number ON a total line
        // beats a bigger number merely near one — cash tendered is the classic
        // trap); size only breaks ties. No keywords at all? Fall back to the
        // largest plausible number in the document.
        return candidates
            .sortedWith(
                compareByDescending<Candidate> { it.keywordScore }
                    .thenByDescending { it.minor }
            )
            .first().minor
    }

    /** Rejects tokens that are clearly ids, phone numbers, or dates. */
    private fun looksLikeNonAmount(token: String, line: String): Boolean {
        val digitsOnly = token.filter { it.isDigit() }
        if (digitsOnly.length > 9) return true // phone / receipt ids
        val lower = line.lowercase(Locale.ROOT)
        if (lower.contains("tel") || lower.contains("phone") || lower.contains("tax id")) return true
        // Bare integer that is actually part of a date like 21/07/2026.
        val idx = line.indexOf(token)
        val before = line.getOrNull(idx - 1)
        val after = line.getOrNull(idx + token.length)
        if (before == '/' || after == '/' || before == '-' && after == '-') return true
        return false
    }

    /**
     * "1.234,56" vs "1,234.56": when both separators appear, the last one is
     * the decimal mark. A single separator followed by exactly two digits is
     * a decimal; followed by three, a thousands separator.
     */
    internal fun parseAmountToken(token: String, currency: String): Long? {
        var t = token.replace(" ", "")
        if (t.isEmpty()) return null
        val lastDot = t.lastIndexOf('.')
        val lastComma = t.lastIndexOf(',')
        val normalized: String = when {
            lastDot >= 0 && lastComma >= 0 ->
                if (lastDot > lastComma) {
                    t.replace(",", "")
                } else {
                    t.replace(".", "").replace(',', '.')
                }

            lastComma >= 0 -> {
                val decimals = t.length - lastComma - 1
                if (decimals in 1..2 && t.count { it == ',' } == 1) {
                    t.replace(',', '.')
                } else {
                    t.replace(",", "")
                }
            }

            lastDot >= 0 -> {
                val decimals = t.length - lastDot - 1
                if (decimals in 1..2 && t.count { it == '.' } == 1) t else t.replace(".", "")
            }

            else -> t
        }
        val value = try {
            BigDecimal(normalized)
        } catch (_: NumberFormatException) {
            return null
        }
        return Money.parseToMinor(value.toPlainString(), currency)
    }

    private val datePatterns: List<DateTimeFormatter> = listOf(
        DateTimeFormatter.ofPattern("d/M/uuuu"),
        DateTimeFormatter.ofPattern("d-M-uuuu"),
        DateTimeFormatter.ofPattern("d.M.uuuu"),
        DateTimeFormatter.ofPattern("uuuu-M-d"),
        DateTimeFormatter.ofPattern("d/M/uu"),
        // US ordering as a fallback: only parses when day-first failed
        // (e.g. 07/21/2026 has no valid month 21).
        DateTimeFormatter.ofPattern("M/d/uuuu"),
        DateTimeFormatter.ofPattern("M-d-uuuu"),
        DateTimeFormatterBuilder().parseCaseInsensitive()
            .appendPattern("d MMM uuuu").toFormatter(Locale.ENGLISH),
        DateTimeFormatterBuilder().parseCaseInsensitive()
            .appendPattern("d MMMM uuuu").toFormatter(Locale.ENGLISH),
        DateTimeFormatterBuilder().parseCaseInsensitive()
            .appendPattern("MMM d, uuuu").toFormatter(Locale.ENGLISH),
        DateTimeFormatterBuilder().parseCaseInsensitive()
            .appendPattern("MMMM d, uuuu").toFormatter(Locale.ENGLISH),
        DateTimeFormatterBuilder().parseCaseInsensitive()
            .appendPattern("d MMMM uuuu").toFormatter(Locale.ITALIAN),
    )

    private val dateTokenRegex = Regex(
        """\b(\d{1,2}[/.\-]\d{1,2}[/.\-]\d{2,4}|\d{4}-\d{1,2}-\d{1,2}|\d{1,2} [A-Za-zÀ-ÿ]{3,} \d{4}|[A-Za-z]{3,} \d{1,2}, \d{4})\b"""
    )

    private fun detectDate(lines: List<String>): Long? {
        for (line in lines) {
            for (match in dateTokenRegex.findAll(line)) {
                for (formatter in datePatterns) {
                    var parsed = try {
                        LocalDate.parse(match.value, formatter)
                    } catch (_: Exception) {
                        null
                    } ?: continue
                    // Thai receipts print Buddhist Era years (2569 = 2026).
                    if (parsed.year in 2400..2700) {
                        parsed = parsed.minusYears(543)
                    }
                    if (parsed.year in 2000..2100) {
                        return parsed.atStartOfDay(ZoneId.systemDefault())
                            .toInstant().toEpochMilli()
                    }
                }
            }
        }
        return null
    }

    /** First prominent top line that reads like a name, not numbers/noise. */
    private fun detectMerchant(lines: List<String>): String? {
        return lines.take(4).firstOrNull { line ->
            val letters = line.count { it.isLetter() }
            letters >= 3 &&
                letters.toFloat() / line.length >= 0.5f &&
                !dateTokenRegex.containsMatchIn(line) &&
                totalKeywords.none { line.lowercase(Locale.ROOT).contains(it) } &&
                !line.lowercase(Locale.ROOT).let { l ->
                    l.contains("receipt") || l.contains("invoice") || l.contains("tax") ||
                        l.contains("ricevuta") || l.contains("ใบเสร็จ")
                }
        }?.take(60)
    }
}
