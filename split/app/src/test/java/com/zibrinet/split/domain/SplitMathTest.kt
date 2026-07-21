package com.zibrinet.split.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitMathTest {

    @Test
    fun `even fifty fifty splits cleanly`() {
        assertEquals(Shares(5000, 5000), percentShares(10000, 50))
    }

    @Test
    fun `odd amount fifty fifty rounds half up to self`() {
        assertEquals(Shares(51, 50), percentShares(101, 50))
    }

    @Test
    fun `zero percent gives self nothing`() {
        assertEquals(Shares(0, 10000), percentShares(10000, 0))
    }

    @Test
    fun `hundred percent gives self everything`() {
        assertEquals(Shares(10000, 0), percentShares(10000, 100))
    }

    @Test
    fun `uneven percent keeps exact total`() {
        val shares = percentShares(1000, 33)
        assertEquals(330, shares.selfMinor)
        assertEquals(670, shares.otherMinor)
    }

    @Test
    fun `tiny amounts never lose a minor unit`() {
        assertEquals(Shares(0, 1), percentShares(1, 1))
        assertEquals(Shares(1, 0), percentShares(1, 50))
        assertEquals(Shares(1, 1), percentShares(2, 50))
    }

    @Test
    fun `sum is always preserved and shares never negative`() {
        for (amount in longArrayOf(0, 1, 2, 3, 99, 100, 101, 999, 12345, 999999999999)) {
            for (percent in 0..100) {
                val shares = percentShares(amount, percent)
                assertEquals("amount=$amount percent=$percent", amount, shares.total)
                assertTrue(shares.selfMinor >= 0 && shares.otherMinor >= 0)
            }
        }
    }

    @Test
    fun `rounding is deterministic for same inputs`() {
        assertEquals(percentShares(101, 50), percentShares(101, 50))
    }

    @Test
    fun `exact shares pass through when they sum`() {
        assertEquals(Shares(1234, 8766), exactShares(10000, 1234, 8766))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `exact shares must sum to total`() {
        exactShares(10000, 1234, 1234)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `percent outside range rejected`() {
        percentShares(100, 101)
    }
}
