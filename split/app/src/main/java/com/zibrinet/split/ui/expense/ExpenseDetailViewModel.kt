package com.zibrinet.split.ui.expense

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zibrinet.split.data.SplitRepository
import com.zibrinet.split.data.model.Expense
import com.zibrinet.split.ui.common.appContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ExpenseDetailState(
    val loading: Boolean = true,
    val expense: Expense? = null,
    val selfId: String = "",
    val selfName: String = "",
    val otherName: String = "",
)

class ExpenseDetailViewModel(
    private val repository: SplitRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val expenseId: String = checkNotNull(savedStateHandle.get<String>("expenseId"))

    val state: StateFlow<ExpenseDetailState> = combine(
        repository.observeExpense(expenseId),
        repository.participants,
    ) { expense, participants ->
        ExpenseDetailState(
            loading = false,
            expense = expense,
            selfId = participants.firstOrNull { it.isSelf }?.id.orEmpty(),
            selfName = participants.firstOrNull { it.isSelf }?.name.orEmpty(),
            otherName = participants.firstOrNull { !it.isSelf }?.name.orEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpenseDetailState())

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteExpense(expenseId)
            onDeleted()
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ExpenseDetailViewModel(appContainer().repository, createSavedStateHandle())
            }
        }
    }
}
