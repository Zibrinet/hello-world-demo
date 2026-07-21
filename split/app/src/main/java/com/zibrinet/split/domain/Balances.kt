package com.zibrinet.split.domain

import com.zibrinet.split.data.model.Expense
import com.zibrinet.split.data.model.Settlement

/**
 * Net balance per currency, from self's point of view:
 * positive = the other person owes self, negative = self owes them.
 * Currencies are never netted against each other — no invented FX rates.
 */
fun computeBalances(
    expenses: List<Expense>,
    settlements: List<Settlement>,
    selfId: String,
): Map<String, Long> {
    val net = mutableMapOf<String, Long>()

    for (expense in expenses) {
        val shares = expense.shares()
        val delta = if (expense.paidById == selfId) shares.otherMinor else -shares.selfMinor
        net.merge(expense.currency, delta, Long::plus)
    }

    // A settlement from X reduces X's debt: self paying raises the net
    // (self owes less), the other person paying lowers it.
    for (settlement in settlements) {
        val delta = if (settlement.fromId == selfId) settlement.amountMinor else -settlement.amountMinor
        net.merge(settlement.currency, delta, Long::plus)
    }

    return net
}

/** Balances worth showing: zero (settled) currencies removed. */
fun outstandingBalances(balances: Map<String, Long>): Map<String, Long> =
    balances.filterValues { it != 0L }
