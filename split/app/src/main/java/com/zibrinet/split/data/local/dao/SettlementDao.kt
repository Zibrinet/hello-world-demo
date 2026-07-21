package com.zibrinet.split.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.zibrinet.split.data.model.Settlement
import kotlinx.coroutines.flow.Flow

@Dao
interface SettlementDao {
    @Upsert
    suspend fun upsert(settlement: Settlement)

    @Query("SELECT * FROM settlements WHERE id = :id")
    suspend fun getById(id: String): Settlement?

    @Query("SELECT * FROM settlements WHERE isDeleted = 0 ORDER BY date DESC, createdAt DESC")
    fun observeActive(): Flow<List<Settlement>>

    @Query(
        "UPDATE settlements SET isDeleted = 1, deletedAt = :now, updatedAt = :now WHERE id = :id"
    )
    suspend fun softDelete(id: String, now: Long)

    @Query(
        "UPDATE settlements SET isDeleted = 0, deletedAt = NULL, updatedAt = :now WHERE id = :id"
    )
    suspend fun restore(id: String, now: Long)
}
