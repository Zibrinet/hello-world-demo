package com.zibrinet.split.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptPathsTest {

    private fun expense(paths: String?) = Expense(
        amountMinor = 100,
        currency = "THB",
        date = 0L,
        paidById = "x",
        receiptImagePaths = paths,
        createdAt = 0L,
        updatedAt = 0L,
    )

    @Test
    fun `round trips multiple paths`() {
        val paths = listOf("/a/1.jpg", "/a/2.jpg", "/a/3.jpg")
        val joined = joinReceiptPaths(paths)
        assertEquals(paths, expense(joined).receiptPathList())
    }

    @Test
    fun `single legacy path still reads as one-item list`() {
        assertEquals(listOf("/a/old.jpg"), expense("/a/old.jpg").receiptPathList())
    }

    @Test
    fun `null and blank collapse to empty`() {
        assertTrue(expense(null).receiptPathList().isEmpty())
        assertNull(joinReceiptPaths(emptyList()))
        assertNull(joinReceiptPaths(listOf("", " ")))
    }
}
