package com.zibrinet.split.data

import com.zibrinet.split.data.local.SplitDatabase
import com.zibrinet.split.data.model.Expense
import com.zibrinet.split.data.model.Participant
import com.zibrinet.split.data.model.Settlement
import kotlinx.coroutines.flow.Flow

class RoomSplitRepository(
    private val db: SplitDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) : SplitRepository {

    override val participants: Flow<List<Participant>> = db.participantDao().observeActive()
    override val expenses: Flow<List<Expense>> = db.expenseDao().observeActive()
    override val settlements: Flow<List<Settlement>> = db.settlementDao().observeActive()

    override suspend fun hasParticipants(): Boolean = db.participantDao().countActive() > 0

    override suspend fun seedParticipants(selfName: String, otherName: String) {
        val now = clock()
        db.participantDao().upsertAll(
            listOf(
                Participant(name = selfName.trim(), isSelf = true, createdAt = now, updatedAt = now),
                Participant(name = otherName.trim(), isSelf = false, createdAt = now, updatedAt = now),
            )
        )
    }

    override suspend fun renameParticipants(selfName: String, otherName: String) {
        val now = clock()
        val updated = db.participantDao().getActive().map { p ->
            val newName = if (p.isSelf) selfName.trim() else otherName.trim()
            p.copy(name = newName, updatedAt = now)
        }
        db.participantDao().upsertAll(updated)
    }

    override suspend fun getExpense(id: String): Expense? = db.expenseDao().getById(id)

    override fun observeExpense(id: String): Flow<Expense?> = db.expenseDao().observeById(id)

    override suspend fun saveExpense(expense: Expense) {
        db.expenseDao().upsert(expense.copy(updatedAt = clock()))
    }

    override suspend fun deleteExpense(id: String) = db.expenseDao().softDelete(id, clock())

    override suspend fun restoreExpense(id: String) = db.expenseDao().restore(id, clock())

    override suspend fun getSettlement(id: String): Settlement? = db.settlementDao().getById(id)

    override suspend fun saveSettlement(settlement: Settlement) {
        db.settlementDao().upsert(settlement.copy(updatedAt = clock()))
    }

    override suspend fun deleteSettlement(id: String) = db.settlementDao().softDelete(id, clock())

    override suspend fun restoreSettlement(id: String) = db.settlementDao().restore(id, clock())
}
