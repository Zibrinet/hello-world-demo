package com.zibrinet.split.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.zibrinet.split.data.model.Participant
import kotlinx.coroutines.flow.Flow

@Dao
interface ParticipantDao {
    @Upsert
    suspend fun upsert(participant: Participant)

    @Upsert
    suspend fun upsertAll(participants: List<Participant>)

    @Query("SELECT * FROM participants WHERE isDeleted = 0 ORDER BY isSelf DESC")
    fun observeActive(): Flow<List<Participant>>

    @Query("SELECT * FROM participants WHERE isDeleted = 0 ORDER BY isSelf DESC")
    suspend fun getActive(): List<Participant>

    @Query("SELECT COUNT(*) FROM participants WHERE isDeleted = 0")
    suspend fun countActive(): Int
}
