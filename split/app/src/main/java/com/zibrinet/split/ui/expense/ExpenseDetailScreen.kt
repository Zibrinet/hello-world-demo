package com.zibrinet.split.ui.expense

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.zibrinet.split.data.model.SplitType
import com.zibrinet.split.data.model.receiptPathList
import com.zibrinet.split.domain.Money
import com.zibrinet.split.domain.categoryById
import com.zibrinet.split.domain.shares
import com.zibrinet.split.ui.common.formatDate
import com.zibrinet.split.ui.navigation.Routes
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseDetailScreen(
    navController: NavHostController,
    viewModel: ExpenseDetailViewModel = viewModel(factory = ExpenseDetailViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Expense") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        navController.navigate(Routes.editExpense(viewModel.expenseId))
                    }) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete")
                    }
                },
            )
        },
    ) { padding ->
        val expense = state.expense
        if (state.loading || expense == null) {
            Spacer(Modifier.padding(padding))
            return@Scaffold
        }
        val paidBySelf = expense.paidById == state.selfId
        val shares = expense.shares()

        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Text(
                    "${categoryById(expense.category)?.emoji ?: "🧾"} " +
                        expense.title.ifBlank {
                            categoryById(expense.category)?.label ?: "Expense"
                        },
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    Money.format(expense.amountMinor, expense.currency),
                    style = MaterialTheme.typography.displaySmall,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text(formatDate(expense.date)) })
                AssistChip(onClick = {}, label = { Text(expense.currency) })
                categoryById(expense.category)?.let {
                    AssistChip(onClick = {}, label = { Text("${it.emoji} ${it.label}") })
                }
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailLine(
                        "Paid by",
                        if (paidBySelf) state.selfName else state.otherName,
                    )
                    HorizontalDivider()
                    DetailLine(
                        "Split",
                        if (expense.splitType == SplitType.PERCENT) {
                            "${expense.selfPercent}% / ${expense.otherPercent}%"
                        } else {
                            "Exact amounts"
                        },
                    )
                    HorizontalDivider()
                    DetailLine(
                        "${state.selfName} owes",
                        Money.format(shares.selfMinor, expense.currency),
                    )
                    DetailLine(
                        "${state.otherName} owes",
                        Money.format(shares.otherMinor, expense.currency),
                    )
                }
            }

            expense.notes?.let {
                Column {
                    Text(
                        "Notes",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.bodyLarge)
                }
            }

            val receiptPaths = expense.receiptPathList()
            if (receiptPaths.isNotEmpty()) {
                Column {
                    Text(
                        if (receiptPaths.size == 1) "Receipt" else "Receipts",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    receiptPaths.forEach { path ->
                        AsyncImage(
                            model = File(path),
                            contentDescription = "Receipt image",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this expense?") },
            text = { Text("You can undo right after deleting.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.delete {
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set("deleted_expense", viewModel.expenseId)
                        navController.popBackStack()
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
