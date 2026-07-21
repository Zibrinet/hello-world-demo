package com.zibrinet.split.domain

/**
 * Pure split arithmetic. No storage, UI, or Android dependencies.
 * All amounts are integer minor units; shares always sum exactly to the total.
 */
data class Shares(val selfMinor: Long, val otherMinor: Long) {
    val total: Long get() = selfMinor + otherMinor
}

/**
 * Percent split with deterministic rounding: self's share is rounded half-up
 * to the nearest minor unit, and the other participant receives exactly the
 * remainder, so no minor unit is ever lost or invented.
 */
fun percentShares(amountMinor: Long, selfPercent: Int): Shares {
    require(selfPercent in 0..100) { "selfPercent must be 0..100, was $selfPercent" }
    require(amountMinor >= 0) { "amountMinor must be >= 0, was $amountMinor" }
    val selfMinor = (amountMinor * selfPercent + 50) / 100
    return Shares(selfMinor = selfMinor, otherMinor = amountMinor - selfMinor)
}

/** Exact split: both shares are entered directly and must sum to the total. */
fun exactShares(amountMinor: Long, selfExactMinor: Long, otherExactMinor: Long): Shares {
    require(selfExactMinor >= 0 && otherExactMinor >= 0) { "shares must be >= 0" }
    require(selfExactMinor + otherExactMinor == amountMinor) {
        "exact shares ($selfExactMinor + $otherExactMinor) must sum to $amountMinor"
    }
    return Shares(selfMinor = selfExactMinor, otherMinor = otherExactMinor)
}
