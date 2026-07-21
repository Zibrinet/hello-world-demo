package com.zibrinet.split.ui.expense

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.zibrinet.split.domain.Categories
import com.zibrinet.split.domain.Money
import com.zibrinet.split.ui.common.formatDate
import com.zibrinet.split.ui.components.AmountKeypad
import com.zibrinet.split.ui.components.CurrencyChip
import com.zibrinet.split.ui.components.SplitControl

/**
 * One low-friction editor for new, edited, and OCR-prefilled expenses.
 * A 50/50 expense paid by self is: open -> type amount -> Save (3 taps + digits).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseEditorScreen(
    navController: NavHostController,
    viewModel: ExpenseEditorViewModel = viewModel(factory = ExpenseEditorViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved) {
        if (state.saved) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            navController.popBackStack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.editingId == null) "New expense" else "Edit expense") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.Close, contentDescription = "Discard")
                    }
                },
            )
        },
        bottomBar = {
            Button(
                onClick = viewModel::save,
                enabled = state.canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .height(56.dp),
            ) {
                Text("Save", style = MaterialTheme.typography.titleMedium)
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Amount, front and center.
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    state.amountText.ifEmpty { "0" },
                    style = MaterialTheme.typography.displayMedium,
                    color = if (state.amountText.isEmpty()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(1f),
                )
                CurrencyChip(
                    currency = state.currency,
                    onCurrencySelected = viewModel::setCurrency,
                )
            }
            AmountKeypad(
                onDigit = viewModel::appendDigit,
                onBackspace = viewModel::backspace,
                decimalEnabled = Money.fractionDigits(state.currency) > 0,
            )

            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::setTitle,
                label = { Text("Title (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )

            Column {
                Text(
                    "Paid by",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = state.paidBySelf,
                        onClick = { viewModel.setPaidBySelf(true) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) { Text(state.selfName.ifBlank { "You" }) }
                    SegmentedButton(
                        selected = !state.paidBySelf,
                        onClick = { viewModel.setPaidBySelf(false) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) { Text(state.otherName.ifBlank { "Them" }) }
                }
            }

            Column {
                Text(
                    "Split",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                SplitControl(
                    amountMinor = state.amountMinor,
                    currency = state.currency,
                    selfName = state.selfName.ifBlank { "You" },
                    otherName = state.otherName.ifBlank { "Them" },
                    splitType = state.splitType,
                    selfPercent = state.selfPercent,
                    exactSelfText = state.exactSelfText,
                    exactOtherText = state.exactOtherText,
                    onSplitTypeChange = viewModel::setSplitType,
                    onSelfPercentChange = viewModel::setSelfPercent,
                    onExactSelfChange = viewModel::setExactSelf,
                    onExactOtherChange = viewModel::setExactOther,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(
                    onClick = { showDatePicker = true },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.CalendarToday,
                            contentDescription = null,
                        )
                    },
                    label = { Text(formatDate(state.date)) },
                )
                Spacer(Modifier.width(8.dp))
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Categories, key = { it.id }) { category ->
                    FilterChip(
                        selected = state.categoryId == category.id,
                        onClick = {
                            viewModel.setCategory(
                                if (state.categoryId == category.id) null else category.id
                            )
                        },
                        label = { Text("${category.emoji} ${category.label}") },
                    )
                }
            }

            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::setNotes,
                label = { Text("Notes (optional)") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = state.date)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let(viewModel::setDate)
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
