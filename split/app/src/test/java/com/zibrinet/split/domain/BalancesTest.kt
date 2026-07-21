package com.zibrinet.split.domain

import com.zibrinet.split.data.model.Expense
import com.zibrinet.split.data.model.Settlement
import com.zibrinet.split.data.model.SplitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BalancesTest {

    private val self = "self-id"
    private val other = "other-id"

    private fun expense(
        amount: Long,
        currency: String = "THB",
        paidBy: String = self,
        selfPercent: Int = 50,
        splitType: SplitType = SplitType.PERCENT,
        selfExact: Long? = null,
        otherExact: Long? = null,
    ) = Expense(
        amountMinor = amount,
        currency = currency,
        date = 0L,
        paidById = paidBy,
        splitType = splitType,
        selfPercent = selfPercent,
        otherPercent = 100 - selfPercent,
        selfExactMinor = selfExact,
        otherExactMinor = otherExact,
        createdAt = 0L,
        updatedAt = 0L,
    )

    private fun settlement(amount: Long, from: String, currency: String = "THB") = Settlement(
        fromId = from,
        toId = if (from == self) other else self,
        amountMinor = amount,
        currency = currency,
        date = 0L,
        createdAt = 0L,
        updatedAt = 0L,
    )

    @Test
    fun `self pays fifty fifty - other owes half`() {
        val balances = computeBalances(listOf(expense(10000)), emptyList(), self)
        assertEquals(5000L, balances["THB"])
    }

    @Test
    fun `other pays fifty fifty - self owes half`() {
        val balances = computeBalances(listOf(expense(10000, paidBy = other)), emptyList(), self)
        assertEquals(-5000L, balances["THB"])
    }

    @Test
    fun `currencies are tracked separately, never netted`() {
        val balances = computeBalances(
            listOf(expense(10000, currency = "THB"), expense(4000, currency = "EUR", paidBy = other)),
            emptyList(),
            self,
        )
        assertEquals(5000L, balances["THB"])
        assertEquals(-2000L, balances["EUR"])
    }

    @Test
    fun `settlement from other pays down their debt`() {
        val balances = computeBalances(
            listOf(expense(10000)),
            listOf(settlement(5000, from = other)),
            self,
        )
        assertEquals(0L, balances["THB"])
    }

    @Test
    fun `settlement from self pays down own debt`() {
        val balances = computeBalances(
            listOf(expense(10000, paidBy = other)),
            listOf(settlement(5000, from = self)),
            self,
        )
        assertEquals(0L, balances["THB"])
    }

    @Test
    fun `exact split respected in balance`() {
        val balances = computeBalances(
            listOf(
                expense(
                    10000,
                    splitType = SplitType.EXACT,
                    selfExact = 2500,
                    otherExact = 7500,
                )
            ),
            emptyList(),
            self,
        )
        assertEquals(7500L, balances["THB"])
    }

    @Test
    fun `uneven percent balance uses derived shares`() {
        // 70/30 split, other pays: self owes 70% of 1000.
        val balances = computeBalances(
            listOf(expense(1000, paidBy = other, selfPercent = 70)),
            emptyList(),
            self,
        )
        assertEquals(-700L, balances["THB"])
    }

    @Test
    fun `settled currencies drop from outstanding`() {
        val balances = computeBalances(
            listOf(expense(10000)),
            listOf(settlement(5000, from = other)),
            self,
        )
        assertTrue(outstandingBalances(balances).isEmpty())
    }

    @Test
    fun `empty ledger has no balances`() {
        assertTrue(computeBalances(emptyList(), emptyList(), self).isEmpty())
    }
}
