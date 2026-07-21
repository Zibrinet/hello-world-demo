package com.zibrinet.split.data

import com.zibrinet.split.data.model.Expense
import com.zibrinet.split.data.model.Participant
import com.zibrinet.split.data.model.Settlement
import kotlinx.coroutines.flow.Flow

/**
 * Single seam between the app and persistence. ViewModels talk only to this
 * interface; a future sync engine slots in as another implementation (or a
 * decorator) without touching callers. Deletes are always soft.
 */
interface SplitRepository {
    val participants: Flow<List<Participant>>
    val expenses: Flow<List<Expense>>
    val settlements: Flow<List<Settlement>>

    suspend fun hasParticipants(): Boolean
    suspend fun seedParticipants(selfName: String, otherName: String)
    suspend fun renameParticipants(selfName: String, otherName: String)

    suspend fun getExpense(id: String): Expense?
    fun observeExpense(id: String): Flow<Expense?>
    suspend fun saveExpense(expense: Expense)
    suspend fun deleteExpense(id: String)
    suspend fun restoreExpense(id: String)

    suspend fun getSettlement(id: String): Settlement?
    suspend fun saveSettlement(settlement: Settlement)
    suspend fun deleteSettlement(id: String)
    suspend fun restoreSettlement(id: String)
}
