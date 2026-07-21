package com.zibrinet.split.ui.expense

import android.net.Uri
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
import com.zibrinet.split.data.model.joinReceiptPaths
import com.zibrinet.split.data.model.receiptPathList
import com.zibrinet.split.data.settings.SettingsRepository
import com.zibrinet.split.domain.Money
import com.zibrinet.split.ocr.CaptureMode
import com.zibrinet.split.ocr.ReceiptAnalyzer
import com.zibrinet.split.ui.common.appContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** An attached receipt image plus what OCR read off it (editor-session only). */
data class ReceiptAttachment(
    val path: String,
    val amountMinor: Long? = null,
    val currency: String? = null,
)

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
    val attachments: List<ReceiptAttachment> = emptyList(),
    val rawOcrText: String? = null,
    /** True when fields were pre-filled by OCR and need human review. */
    val reviewMode: Boolean = false,
    val scanning: Boolean = false,
    val scanMessage: String? = null,
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
    private val receiptAnalyzer: ReceiptAnalyzer,
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
                        attachments = loaded.receiptPathList().map { ReceiptAttachment(it) },
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

    fun clearAmount() {
        _state.update { s -> s.copy(amountText = "").syncExactWithAmount() }
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

    /**
     * The last amount text this class wrote into the form from OCR. While the
     * visible amount still equals it, the amount is "automatic": new photo
     * batches add on top and removing a photo subtracts its share. The moment
     * the user edits the amount by hand, automation backs off.
     */
    private var lastAutoAmountText: String? = null

    /**
     * Receipt/invoice/email-screenshot capture; accepts several images at
     * once. Gallery picks are treated as separate receipts whose totals sum;
     * scanner pages are parsed as one document. Extracted values only
     * pre-fill the form ([EditorState.reviewMode]) — the human always
     * confirms before anything is saved, and a manual amount is never
     * overwritten. If nothing useful was parsed, the photos still attach:
     * never a hard failure.
     */
    fun attachImages(uris: List<Uri>, fromScanner: Boolean) {
        if (uris.isEmpty()) return
        _state.update { it.copy(scanning = true, scanMessage = null) }
        viewModelScope.launch {
            val mode = if (fromScanner) CaptureMode.SCAN_PAGES else CaptureMode.SEPARATE_PHOTOS
            val result = try {
                receiptAnalyzer.analyzeAll(uris, _state.value.currency, mode)
            } catch (_: Exception) {
                _state.update {
                    it.copy(scanning = false, scanMessage = "Couldn't read those images — try again.")
                }
                return@launch
            }
            _state.update { s ->
                val parsed = result.parsed
                val newAttachments = result.imagePaths.mapIndexed { i, path ->
                    val p = result.perImage.getOrNull(i)
                    ReceiptAttachment(path, p?.amountMinor, p?.currency)
                }
                val amountIsAuto = s.amountText.isBlank() || s.amountText == lastAutoAmountText

                var currency = s.currency
                var amountText = s.amountText
                var message: String? = null
                if (parsed.amountMinor != null && amountIsAuto) {
                    if (s.amountText.isBlank()) {
                        currency = parsed.currency ?: s.currency
                        amountText = Money.toPlainString(parsed.amountMinor, currency)
                        lastAutoAmountText = amountText
                    } else if (parsed.currency == null || parsed.currency == s.currency) {
                        val current = Money.parseToMinor(s.amountText, s.currency) ?: 0L
                        amountText = Money.toPlainString(current + parsed.amountMinor, s.currency)
                        lastAutoAmountText = amountText
                    } else {
                        message = "New photos use a different currency — amounts were not combined."
                    }
                }
                if (result.mixedCurrencies) {
                    message = "Photos show different currencies — only the first total was used."
                } else if (!fromScanner && result.amountsFound in 1 until uris.size) {
                    message =
                        "No total found on ${uris.size - result.amountsFound} of ${uris.size} photos — check the amount."
                } else if (!parsed.foundAnything) {
                    message = "No details recognized — photos attached, fill in the rest manually."
                }

                s.copy(
                    scanning = false,
                    attachments = s.attachments + newAttachments,
                    rawOcrText = listOfNotNull(s.rawOcrText, result.rawText)
                        .joinToString("\n----\n").ifBlank { null },
                    reviewMode = s.reviewMode || parsed.foundAnything,
                    scanMessage = message,
                    amountText = amountText,
                    currency = currency,
                    title = s.title.ifBlank { parsed.merchant.orEmpty() },
                    date = if (s.attachments.isEmpty()) parsed.dateMillis ?: s.date else s.date,
                ).syncExactWithAmount()
            }
        }
    }

    fun removeReceiptImage(path: String) = _state.update { s ->
        val removed = s.attachments.firstOrNull { it.path == path }
        val remaining = s.attachments.filterNot { it.path == path }

        var amountText = s.amountText
        val removable = removed?.amountMinor != null &&
            s.amountText == lastAutoAmountText &&
            (removed.currency == null || removed.currency == s.currency)
        if (removable) {
            val current = Money.parseToMinor(s.amountText, s.currency) ?: 0L
            val newMinor = (current - removed!!.amountMinor!!).coerceAtLeast(0L)
            amountText = if (newMinor == 0L) "" else Money.toPlainString(newMinor, s.currency)
            lastAutoAmountText = amountText.ifBlank { null }
        }

        s.copy(
            attachments = remaining,
            amountText = amountText,
            rawOcrText = if (remaining.isEmpty()) null else s.rawOcrText,
            reviewMode = if (remaining.isEmpty()) false else s.reviewMode,
        ).syncExactWithAmount()
    }

    fun clearScanMessage() = _state.update { it.copy(scanMessage = null) }

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
            receiptImagePaths = joinReceiptPaths(s.attachments.map { it.path }),
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
                    receiptAnalyzer = container.receiptAnalyzer,
                    savedStateHandle = createSavedStateHandle(),
                )
            }
        }
    }
}
