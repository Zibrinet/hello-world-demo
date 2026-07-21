package com.zibrinet.split.ui.settle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zibrinet.split.data.SplitRepository
import com.zibrinet.split.data.model.Settlement
import com.zibrinet.split.domain.Money
import com.zibrinet.split.domain.computeBalances
import com.zibrinet.split.domain.outstandingBalances
import com.zibrinet.split.ui.common.appContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettleUpState(
    val loading: Boolean = true,
    /** currency -> net minor units (positive = other owes self). */
    val balances: Map<String, Long> = emptyMap(),
    val selectedCurrency: String? = null,
    val amountText: String = "",
    val notes: String = "",
    val selfName: String = "",
    val otherName: String = "",
    val saved: Boolean = false,
) {
    val selectedNet: Long get() = selectedCurrency?.let { balances[it] } ?: 0L

    val canSave: Boolean
        get() {
            val currency = selectedCurrency ?: return false
            val amount = Money.parseToMinor(amountText, currency) ?: return false
            return amount > 0L && selectedNet != 0L
        }
}

class SettleUpViewModel(private val repository: SplitRepository) : ViewModel() {

    private val _state = MutableStateFlow(SettleUpState())
    val state: StateFlow<SettleUpState> = _state.asStateFlow()

    private var selfId: String = ""
    private var otherId: String = ""

    init {
        viewModelScope.launch {
            val participants = repository.participants.first()
            selfId = participants.firstOrNull { it.isSelf }?.id.orEmpty()
            otherId = participants.firstOrNull { !it.isSelf }?.id.orEmpty()
            val balances = outstandingBalances(
                computeBalances(
                    repository.expenses.first(),
                    repository.settlements.first(),
                    selfId,
                )
            )
            val firstCurrency = balances.keys.firstOrNull()
            _state.update {
                it.copy(
                    loading = false,
                    balances = balances,
                    selectedCurrency = firstCurrency,
                    amountText = firstCurrency
                        ?.let { c -> Money.toPlainString(kotlin.math.abs(balances.getValue(c)), c) }
                        .orEmpty(),
                    selfName = participants.firstOrNull { p -> p.isSelf }?.name.orEmpty(),
                    otherName = participants.firstOrNull { p -> !p.isSelf }?.name.orEmpty(),
                )
            }
        }
    }

    fun selectCurrency(currency: String) {
        _state.update { s ->
            val net = s.balances[currency] ?: 0L
            s.copy(
                selectedCurrency = currency,
                amountText = Money.toPlainString(kotlin.math.abs(net), currency),
            )
        }
    }

    fun setAmountText(text: String) = _state.update { it.copy(amountText = text) }

    fun setNotes(value: String) = _state.update { it.copy(notes = value) }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        val currency = s.selectedCurrency ?: return
        val amount = Money.parseToMinor(s.amountText, currency) ?: return
        val net = s.selectedNet
        val now = System.currentTimeMillis()
        // Positive net: they owe you, so the settlement flows from them to you.
        val settlement = Settlement(
            fromId = if (net > 0) otherId else selfId,
            toId = if (net > 0) selfId else otherId,
            amountMinor = amount,
            currency = currency,
            date = now,
            notes = s.notes.trim().ifBlank { null },
            createdAt = now,
            updatedAt = now,
        )
        viewModelScope.launch {
            repository.saveSettlement(settlement)
            _state.update { it.copy(saved = true) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { SettleUpViewModel(appContainer().repository) }
        }
    }
}
