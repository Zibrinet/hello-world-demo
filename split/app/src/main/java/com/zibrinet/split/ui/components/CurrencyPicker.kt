package com.zibrinet.split.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Currency
import java.util.Locale

private val CommonCurrencies = listOf("THB", "EUR", "USD", "GBP", "JPY", "SGD", "VND", "MYR", "IDR", "AUD", "CHF")

fun currencySymbol(code: String): String = try {
    Currency.getInstance(code).getSymbol(Locale.getDefault())
} catch (_: IllegalArgumentException) {
    code
}

/** Compact chip showing the active currency; tap to choose another. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyChip(
    currency: String,
    onCurrencySelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }

    AssistChip(
        onClick = { showPicker = true },
        label = { Text(currency) },
        modifier = modifier,
    )

    if (showPicker) {
        val all = remember {
            val commons = CommonCurrencies
            val rest = Currency.getAvailableCurrencies()
                .map { it.currencyCode }
                .filter { it !in commons && it.length == 3 }
                .sorted()
            commons + rest
        }
        ModalBottomSheet(onDismissRequest = { showPicker = false }) {
            Text(
                "Currency",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            LazyColumn {
                items(all, key = { it }) { code ->
                    Surface(
                        onClick = {
                            onCurrencySelected(code)
                            showPicker = false
                        },
                    ) {
                        ListItem(
                            headlineContent = { Text(code) },
                            supportingContent = {
                                val name = try {
                                    Currency.getInstance(code).displayName
                                } catch (_: IllegalArgumentException) {
                                    ""
                                }
                                Text(name)
                            },
                            trailingContent = { Text(currencySymbol(code)) },
                        )
                    }
                }
            }
        }
    }
}
