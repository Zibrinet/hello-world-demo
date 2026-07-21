package com.zibrinet.split.ocr

data class MergedPhotos(
    val parsed: ParsedReceipt,
    val amountsFound: Int,
    val mixedCurrencies: Boolean,
    val summed: Boolean,
)

/**
 * Combining rules for a batch of *separate* photos (gallery multi-pick):
 * each image is its own receipt, so their totals add up — but never across
 * different detected currencies, and a photo with no readable total simply
 * contributes nothing rather than blocking the rest. (Multi-page scans are
 * NOT summed; they are parsed as one joined document instead.)
 */
object BatchMerge {
    fun mergeSeparatePhotos(results: List<ParsedReceipt>): MergedPhotos {
        val withAmount = results.filter { it.amountMinor != null }
        val currencies = withAmount.mapNotNull { it.currency }.distinct()
        val mixed = currencies.size > 1

        val (amount, summed) = when {
            withAmount.isEmpty() -> null to false
            mixed || withAmount.size == 1 -> withAmount.first().amountMinor to false
            else -> withAmount.sumOf { it.amountMinor!! } to true
        }

        return MergedPhotos(
            parsed = ParsedReceipt(
                amountMinor = amount,
                currency = withAmount.firstNotNullOfOrNull { it.currency }
                    ?: results.firstNotNullOfOrNull { it.currency },
                dateMillis = results.firstNotNullOfOrNull { it.dateMillis },
                merchant = results.firstNotNullOfOrNull { it.merchant },
            ),
            amountsFound = withAmount.size,
            mixedCurrencies = mixed,
            summed = summed,
        )
    }
}
