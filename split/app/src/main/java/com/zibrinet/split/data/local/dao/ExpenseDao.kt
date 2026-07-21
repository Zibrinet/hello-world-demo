package com.zibrinet.split.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.zibrinet.split.data.model.Expense
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Upsert
    suspend fun upsert(expense: Expense)

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getById(id: String): Expense?

    @Query("SELECT * FROM expenses WHERE id = :id")
    fun observeById(id: String): Flow<Expense?>

    @Query("SELECT * FROM expenses WHERE isDeleted = 0 ORDER BY date DESC, createdAt DESC")
    fun observeActive(): Flow<List<Expense>>

    @Query(
        "UPDATE expenses SET isDeleted = 1, deletedAt = :now, updatedAt = :now WHERE id = :id"
    )
    suspend fun softDelete(id: String, now: Long)

    @Query(
        "UPDATE expenses SET isDeleted = 0, deletedAt = NULL, updatedAt = :now WHERE id = :id"
    )
    suspend fun restore(id: String, now: Long)
}
