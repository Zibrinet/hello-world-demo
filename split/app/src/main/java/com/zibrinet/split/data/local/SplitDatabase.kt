package com.zibrinet.split.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.zibrinet.split.data.local.dao.ExpenseDao
import com.zibrinet.split.data.local.dao.ParticipantDao
import com.zibrinet.split.data.local.dao.SettlementDao
import com.zibrinet.split.data.model.Expense
import com.zibrinet.split.data.model.Participant
import com.zibrinet.split.data.model.Settlement

@Database(
    entities = [Participant::class, Expense::class, Settlement::class],
    version = 1,
    exportSchema = true,
)
abstract class SplitDatabase : RoomDatabase() {
    abstract fun participantDao(): ParticipantDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun settlementDao(): SettlementDao

    companion object {
        fun build(context: Context): SplitDatabase =
            Room.databaseBuilder(context, SplitDatabase::class.java, "split.db").build()
    }
}
