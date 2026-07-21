package com.zibrinet.split.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "participants")
data class Participant(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val isSelf: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val syncStatus: SyncStatus = SyncStatus.LOCAL,
)
