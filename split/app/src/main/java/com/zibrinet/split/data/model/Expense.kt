package com.zibrinet.split.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A shared expense. Money is always integer minor units (satang, cents) —
 * never floats. For [SplitType.PERCENT] the split is [selfPercent] /
 * [otherPercent] (sums to 100); for [SplitType.EXACT] it is
 * [selfExactMinor] / [otherExactMinor] (sums to [amountMinor]).
 */
@Entity(tableName = "expenses", indices = [Index("date"), Index("isDeleted")])
data class Expense(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val amountMinor: Long,
    val currency: String,
    val date: Long,
    val paidById: String,
    val splitType: SplitType = SplitType.PERCENT,
    val selfPercent: Int = 50,
    val otherPercent: Int = 50,
    val selfExactMinor: Long? = null,
    val otherExactMinor: Long? = null,
    val category: String? = null,
    val receiptImagePath: String? = null,
    val rawOcrText: String? = null,
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val syncStatus: SyncStatus = SyncStatus.LOCAL,
)
