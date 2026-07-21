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
import com.zibrinet.split.data.model.SplitType
import com.zibrinet.split.data.settings.SettingsRepository
import com.zibrinet.split.domain.Money
import com.zibrinet.split.ui.common.appContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditorState(
    val loading: Boolean = true,
    val editingId: String? = null,
    val amountText: String = "",
    val currency: String = SettingsRepository.DEFAULT_HOME_CURRENCY,
    val title: String = "",
    val paidBySelf: Boolean = true,
    val splitType: SplitType = SplitType.PERCENT,
    val selfPercent: Int = 50,
    val exactSelfText: String = "",
    val exactOtherText: String = "",
    val date: Long = System.currentTimeMillis(),
    val categoryId: String? = null,
    val notes: String = "",
    val receiptImagePath: String? = null,
    val rawOcrText: String? = null,
    /** True when fields were pre-filled by OCR and need human review. */
    val reviewMode: Boolean = false,
    val selfName: String = "",
    val otherName: String = "",
    val saved: Boolean = false,
) {
    val amountMinor: Long get() = Money.parseToMinor(amountText, currency) ?: 0L

    val canSave: Boolean
        get() {
            val amount = Money.parseToMinor(amountText, currency) ?: return false
            if (amount <= 0L) return false
            if (splitType == SplitType.EXACT) {
                val self = Money.parseToMinor(exactSelfText.ifBlank { "0" }, currency) ?: return false
                if (self > amount) return false
            }
            return true
        }
}

class ExpenseEditorViewModel(
    private val repository: SplitRepository,
    private val settings: SettingsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val expenseId: String? = savedStateHandle.get<String>("expenseId")

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private var selfId: String = ""
    private var otherId: String = ""
    private var existing: Expense? = null

    init {
        viewModelScope.launch {
            val participants = repository.participants.first()
            selfId = participants.firstOrNull { it.isSelf }?.id.orEmpty()
            otherId = participants.firstOrNull { !it.isSelf }?.id.orEmpty()
            val selfName = participants.firstOrNull { it.isSelf }?.name.orEmpty()
            val otherName = participants.firstOrNull { !it.isSelf }?.name.orEmpty()
            val homeCurrency = settings.homeCurrency.first()

            val loaded = expenseId?.let { repository.getExpense(it) }
            existing = loaded
            _state.update { s ->
                if (loaded == null) {
                    s.copy(
                        loading = false,
                        currency = homeCurrency,
                        selfName = selfName,
                        otherName = otherName,
                    )
                } else {
                    s.copy(
                        loading = false,
                        editingId = loaded.id,
                        amountText = Money.toPlainString(loaded.amountMinor, loaded.currency),
                        currency = loaded.currency,
                        title = loaded.title,
                        paidBySelf = loaded.paidById == selfId,
                        splitType = loaded.splitType,
                        selfPercent = loaded.selfPercent,
                        exactSelfText = loaded.selfExactMinor
                            ?.let { Money.toPlainString(it, loaded.currency) }.orEmpty(),
                        exactOtherText = loaded.otherExactMinor
                            ?.let { Money.toPlainString(it, loaded.currency) }.orEmpty(),
                        date = loaded.date,
                        categoryId = loaded.category,
                        notes = loaded.notes.orEmpty(),
                        receiptImagePath = loaded.receiptImagePath,
                        rawOcrText = loaded.rawOcrText,
                        selfName = selfName,
                        otherName = otherName,
                    )
                }
            }
        }
    }

    fun appendDigit(c: Char) {
        _state.update { s ->
            val digits = Money.fractionDigits(s.currency)
            val text = s.amountText
            val next = when {
                c == '.' -> when {
                    digits == 0 || text.contains('.') -> text
                    text.isEmpty() -> "0."
                    else -> "$text."
                }

                else -> {
                    val decimals = text.substringAfter('.', missingDelimiterValue = "")
                    when {
                        text.contains('.') && decimals.length >= digits -> text
                        text == "0" -> c.toString()
                        text.length >= 12 && !text.contains('.') -> text
                        else -> text + c
                    }
                }
            }
            s.copy(amountText = next).syncExactWithAmount()
        }
    }

    fun backspace() {
        _state.update { s -> s.copy(amountText = s.amountText.dropLast(1)).syncExactWithAmount() }
    }

    fun setCurrency(code: String) {
        _state.update { s -> s.copy(amountText = "", exactSelfText = "", exactOtherText = "", currency = code) }
    }

    fun setTitle(value: String) = _state.update { it.copy(title = value) }

    fun setPaidBySelf(value: Boolean) = _state.update { it.copy(paidBySelf = value) }

    fun setSplitType(value: SplitType) {
        _state.update { s -> s.copy(splitType = value).syncExactWithAmount() }
    }

    fun setSelfPercent(value: Int) =
        _state.update { it.copy(selfPercent = value.coerceIn(0, 100)) }

    fun setExactSelf(text: String) {
        _state.update { s ->
            val amount = s.amountMinor
            val self = Money.parseToMinor(text, s.currency)
            val other = if (self != null && amount > 0) {
                Money.toPlainString((amount - self).coerceAtLeast(0), s.currency)
            } else {
                s.exactOtherText
            }
            s.copy(exactSelfText = text, exactOtherText = other)
        }
    }

    fun setExactOther(text: String) {
        _state.update { s ->
            val amount = s.amountMinor
            val other = Money.parseToMinor(text, s.currency)
            val self = if (other != null && amount > 0) {
                Money.toPlainString((amount - other).coerceAtLeast(0), s.currency)
            } else {
                s.exactSelfText
            }
            s.copy(exactOtherText = text, exactSelfText = self)
        }
    }

    fun setDate(value: Long) = _state.update { it.copy(date = value) }

    fun setCategory(id: String?) = _state.update { it.copy(categoryId = id) }

    fun setNotes(value: String) = _state.update { it.copy(notes = value) }

    /** Keeps exact shares consistent when the total changes under them. */
    private fun EditorState.syncExactWithAmount(): EditorState {
        if (splitType != SplitType.EXACT) return this
        val amount = Money.parseToMinor(amountText, currency) ?: return this
        val self = Money.parseToMinor(exactSelfText.ifBlank { "0" }, currency) ?: 0L
        val clampedSelf = self.coerceIn(0L, amount)
        return copy(
            exactSelfText = if (exactSelfText.isBlank() && clampedSelf == 0L) "" else Money.toPlainString(clampedSelf, currency),
            exactOtherText = Money.toPlainString(amount - clampedSelf, currency),
        )
    }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        val amount = Money.parseToMinor(s.amountText, s.currency) ?: return
        val (selfExact, otherExact) = if (s.splitType == SplitType.EXACT) {
            val self = (Money.parseToMinor(s.exactSelfText.ifBlank { "0" }, s.currency) ?: 0L)
                .coerceIn(0L, amount)
            self to (amount - self)
        } else {
            null to null
        }
        val now = System.currentTimeMillis()
        val expense = Expense(
            id = existing?.id ?: java.util.UUID.randomUUID().toString(),
            title = s.title.trim(),
            amountMinor = amount,
            currency = s.currency,
            date = s.date,
            paidById = if (s.paidBySelf) selfId else otherId,
            splitType = s.splitType,
            selfPercent = s.selfPercent,
            otherPercent = 100 - s.selfPercent,
            selfExactMinor = selfExact,
            otherExactMinor = otherExact,
            category = s.categoryId,
            receiptImagePath = s.receiptImagePath,
            rawOcrText = s.rawOcrText,
            notes = s.notes.trim().ifBlank { null },
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        viewModelScope.launch {
            repository.saveExpense(expense)
            _state.update { it.copy(saved = true) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                ExpenseEditorViewModel(
                    repository = container.repository,
                    settings = container.settings,
                    savedStateHandle = createSavedStateHandle(),
                )
            }
        }
    }
}
