package com.zibrinet.split.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.zibrinet.split.data.model.Expense
import com.zibrinet.split.data.model.Settlement
import com.zibrinet.split.data.model.SplitType
import com.zibrinet.split.domain.Money
import com.zibrinet.split.domain.categoryById
import com.zibrinet.split.domain.shares
import com.zibrinet.split.ui.common.formatDate
import com.zibrinet.split.ui.components.EmptyState
import com.zibrinet.split.ui.navigation.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavHostController,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Deletion undo: detail screens leave the deleted id behind before popping.
    val backStackEntry = navController.currentBackStackEntry
    LaunchedEffect(backStackEntry) {
        val handle = backStackEntry?.savedStateHandle ?: return@LaunchedEffect
        handle.getStateFlow("deleted_expense", "").collect { id ->
            if (id.isNotEmpty()) {
                handle["deleted_expense"] = ""
                val result = snackbarHostState.showSnackbar(
                    message = "Expense deleted",
                    actionLabel = "Undo",
                    withDismissAction = true,
                )
                if (result == SnackbarResult.ActionPerformed) viewModel.restoreExpense(id)
            }
        }
    }
    LaunchedEffect(backStackEntry) {
        val handle = backStackEntry?.savedStateHandle ?: return@LaunchedEffect
        handle.getStateFlow("deleted_settlement", "").collect { id ->
            if (id.isNotEmpty()) {
                handle["deleted_settlement"] = ""
                val result = snackbarHostState.showSnackbar(
                    message = "Settlement deleted",
                    actionLabel = "Undo",
                    withDismissAction = true,
                )
                if (result == SnackbarResult.ActionPerformed) viewModel.restoreSettlement(id)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { navController.navigate(Routes.ADD_EXPENSE) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add expense") },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Split",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { navController.navigate(Routes.SETTINGS) }) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                }
            }

            BalanceHeader(
                state = state,
                onSettleUp = { navController.navigate(Routes.SETTLE_UP) },
            )

            if (!state.loading && state.items.isEmpty()) {
                EmptyState(
                    emoji = "🌱",
                    title = "Nothing shared yet",
                    subtitle = "Add your first expense and Split keeps track of who owes whom.",
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(state.items, key = { it.key }) { item ->
                        when (item) {
                            is LedgerItem.ExpenseItem -> ExpenseRow(
                                expense = item.expense,
                                state = state,
                                onClick = {
                                    navController.navigate(Routes.expenseDetail(item.expense.id))
                                },
                                modifier = Modifier.animateItem(),
                            )

                            is LedgerItem.SettlementItem -> SettlementRow(
                                settlement = item.settlement,
                                state = state,
                                onDelete = { viewModel.deleteSettlement(item.settlement.id) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceHeader(state: HomeUiState, onSettleUp: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            if (state.balances.isEmpty()) {
                Text("All settled ✨", style = MaterialTheme.typography.headlineSmall)
                if (state.items.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Neither of you owes anything.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                state.balances.entries.sortedBy { it.key }.forEach { (currency, net) ->
                    val label = if (net > 0) {
                        "${state.otherName} owes you"
                    } else {
                        "You owe ${state.otherName}"
                    }
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                    AnimatedContent(
                        targetState = net,
                        transitionSpec = {
                            (slideInVertically(spring()) { it / 2 } + fadeIn()) togetherWith
                                (slideOutVertically(spring()) { -it / 2 } + fadeOut())
                        },
                        label = "balance",
                    ) { value ->
                        Text(
                            Money.formatAbs(value, currency),
                            style = MaterialTheme.typography.displaySmall,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                FilledTonalButton(onClick = onSettleUp) { Text("Settle up") }
            }
        }
    }
}

@Composable
private fun ExpenseRow(
    expense: Expense,
    state: HomeUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val paidBySelf = expense.paidById == state.selfId
    val shares = expense.shares()
    val subtitle = buildString {
        append(if (paidBySelf) state.selfName else state.otherName)
        append(" paid · ")
        if (expense.splitType == SplitType.PERCENT) {
            append("${expense.selfPercent}/${expense.otherPercent}")
        } else {
            append("exact split")
        }
        append(" · ")
        append(
            if (paidBySelf) {
                "${state.otherName} owes ${Money.format(shares.otherMinor, expense.currency)}"
            } else {
                "you owe ${Money.format(shares.selfMinor, expense.currency)}"
            }
        )
    }

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(categoryById(expense.category)?.emoji ?: "🧾")
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    expense.title.ifBlank {
                        categoryById(expense.category)?.label ?: "Expense"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    Money.format(expense.amountMinor, expense.currency),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    formatDate(expense.date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SettlementRow(
    settlement: Settlement,
    state: HomeUiState,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fromSelf = settlement.fromId == state.selfId
    val text = if (fromSelf) {
        "You paid ${state.otherName} back"
    } else {
        "${state.otherName} paid you back"
    }
    var showActions by remember { mutableStateOf(false) }

    Surface(
        onClick = { showActions = true },
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("🤝")
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(text, style = MaterialTheme.typography.titleMedium)
                settlement.notes?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    Money.format(settlement.amountMinor, settlement.currency),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    formatDate(settlement.date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showActions) {
        AlertDialog(
            onDismissRequest = { showActions = false },
            title = { Text(text) },
            text = {
                Text(
                    "${Money.format(settlement.amountMinor, settlement.currency)} · " +
                        formatDate(settlement.date)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showActions = false
                    onDelete()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showActions = false }) { Text("Close") }
            },
        )
    }
}
