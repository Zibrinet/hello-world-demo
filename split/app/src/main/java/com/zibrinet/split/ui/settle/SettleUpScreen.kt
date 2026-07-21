package com.zibrinet.split.ui.settle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.zibrinet.split.domain.Money
import com.zibrinet.split.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettleUpScreen(
    navController: NavHostController,
    viewModel: SettleUpViewModel = viewModel(factory = SettleUpViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(state.saved) {
        if (state.saved) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            navController.popBackStack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settle up") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            Spacer(Modifier.padding(padding))
            return@Scaffold
        }
        if (state.balances.isEmpty()) {
            Column(Modifier.padding(padding)) {
                EmptyState(
                    emoji = "✨",
                    title = "You're all settled",
                    subtitle = "There's nothing to pay back right now.",
                )
            }
            return@Scaffold
        }

        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.balances.size > 1) {
                Text(
                    "Balances are tracked per currency — settle each on its own.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.balances.keys.sorted().forEach { currency ->
                        FilterChip(
                            selected = state.selectedCurrency == currency,
                            onClick = { viewModel.selectCurrency(currency) },
                            label = { Text(currency) },
                        )
                    }
                }
            }

            val currency = state.selectedCurrency
            if (currency != null) {
                val net = state.selectedNet
                Column {
                    Text(
                        if (net > 0) {
                            "${state.otherName} owes you"
                        } else {
                            "You owe ${state.otherName}"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        Money.formatAbs(net, currency),
                        style = MaterialTheme.typography.displaySmall,
                    )
                }

                OutlinedTextField(
                    value = state.amountText,
                    onValueChange = viewModel::setAmountText,
                    label = { Text("Amount being paid back") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    supportingText = {
                        Text("Pre-filled with the full balance — adjust for a partial payment.")
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = state.notes,
                    onValueChange = viewModel::setNotes,
                    label = { Text("Notes (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Button(
                    onClick = viewModel::save,
                    enabled = state.canSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    Text(
                        if (net > 0) {
                            "Record ${state.otherName}'s payment"
                        } else {
                            "Record your payment"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}
