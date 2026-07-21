package com.zibrinet.split.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/** A "paid you back" record: [fromId] paid [amountMinor] to [toId]. */
@Entity(tableName = "settlements", indices = [Index("date"), Index("isDeleted")])
data class Settlement(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val fromId: String,
    val toId: String,
    val amountMinor: Long,
    val currency: String,
    val date: Long,
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val syncStatus: SyncStatus = SyncStatus.LOCAL,
)
