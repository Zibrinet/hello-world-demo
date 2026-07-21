package com.zibrinet.split.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zibrinet.split.data.SplitRepository
import com.zibrinet.split.data.model.Expense
import com.zibrinet.split.data.model.Settlement
import com.zibrinet.split.domain.computeBalances
import com.zibrinet.split.domain.outstandingBalances
import com.zibrinet.split.ui.common.appContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface LedgerItem {
    val key: String
    val date: Long
    val createdAt: Long

    data class ExpenseItem(val expense: Expense) : LedgerItem {
        override val key get() = "expense:${expense.id}"
        override val date get() = expense.date
        override val createdAt get() = expense.createdAt
    }

    data class SettlementItem(val settlement: Settlement) : LedgerItem {
        override val key get() = "settlement:${settlement.id}"
        override val date get() = settlement.date
        override val createdAt get() = settlement.createdAt
    }
}

data class HomeUiState(
    val loading: Boolean = true,
    val selfId: String = "",
    val selfName: String = "",
    val otherName: String = "",
    /** currency -> net minor units; positive = the other person owes you. */
    val balances: Map<String, Long> = emptyMap(),
    val items: List<LedgerItem> = emptyList(),
)

class HomeViewModel(private val repository: SplitRepository) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        repository.participants,
        repository.expenses,
        repository.settlements,
    ) { participants, expenses, settlements ->
        val self = participants.firstOrNull { it.isSelf }
        val other = participants.firstOrNull { !it.isSelf }
        val items = buildList {
            expenses.forEach { add(LedgerItem.ExpenseItem(it)) }
            settlements.forEach { add(LedgerItem.SettlementItem(it)) }
        }.sortedWith(compareByDescending<LedgerItem> { it.date }.thenByDescending { it.createdAt })
        HomeUiState(
            loading = false,
            selfId = self?.id.orEmpty(),
            selfName = self?.name.orEmpty(),
            otherName = other?.name.orEmpty(),
            balances = outstandingBalances(
                computeBalances(expenses, settlements, self?.id.orEmpty())
            ),
            items = items,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun deleteExpense(id: String) = viewModelScope.launch { repository.deleteExpense(id) }

    fun restoreExpense(id: String) = viewModelScope.launch { repository.restoreExpense(id) }

    fun deleteSettlement(id: String) = viewModelScope.launch { repository.deleteSettlement(id) }

    fun restoreSettlement(id: String) = viewModelScope.launch { repository.restoreSettlement(id) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(appContainer().repository) }
        }
    }
}
