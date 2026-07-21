package com.zibrinet.split.domain

import com.zibrinet.split.data.model.Expense
import com.zibrinet.split.data.model.SplitType

/** Bridges the stored expense to the pure share math. Shares are derived, never stored. */
fun Expense.shares(): Shares = when (splitType) {
    SplitType.PERCENT -> percentShares(amountMinor, selfPercent)
    SplitType.EXACT -> exactShares(
        amountMinor = amountMinor,
        selfExactMinor = selfExactMinor ?: amountMinor,
        otherExactMinor = otherExactMinor ?: 0L,
    )
}
