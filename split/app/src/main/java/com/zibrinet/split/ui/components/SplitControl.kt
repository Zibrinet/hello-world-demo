package com.zibrinet.split.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zibrinet.split.data.model.SplitType
import com.zibrinet.split.domain.Money
import com.zibrinet.split.domain.percentShares

/**
 * Split editor, slider-first: 50/50 by default, both sides pinned to 100,
 * live amount for each person so the split is never abstract. Exact-amount
 * entry is a secondary mode behind a small text link.
 */
@Composable
fun SplitControl(
    amountMinor: Long,
    currency: String,
    selfName: String,
    otherName: String,
    splitType: SplitType,
    selfPercent: Int,
    exactSelfText: String,
    exactOtherText: String,
    onSplitTypeChange: (SplitType) -> Unit,
    onSelfPercentChange: (Int) -> Unit,
    onExactSelfChange: (String) -> Unit,
    onExactOtherChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (splitType) {
            SplitType.PERCENT -> {
                PercentEditor(
                    amountMinor = amountMinor,
                    currency = currency,
                    selfName = selfName,
                    otherName = otherName,
                    selfPercent = selfPercent,
                    onSelfPercentChange = onSelfPercentChange,
                )
                TextButton(onClick = { onSplitTypeChange(SplitType.EXACT) }) {
                    Text("Enter exact amounts instead")
                }
            }

            SplitType.EXACT -> {
                ExactEditor(
                    selfName = selfName,
                    otherName = otherName,
                    exactSelfText = exactSelfText,
                    exactOtherText = exactOtherText,
                    onExactSelfChange = onExactSelfChange,
                    onExactOtherChange = onExactOtherChange,
                )
                TextButton(onClick = { onSplitTypeChange(SplitType.PERCENT) }) {
                    Text("Back to percent split")
                }
            }
        }
    }
}

@Composable
private fun PercentEditor(
    amountMinor: Long,
    currency: String,
    selfName: String,
    otherName: String,
    selfPercent: Int,
    onSelfPercentChange: (Int) -> Unit,
) {
    val shares = percentShares(amountMinor, selfPercent)
    Column {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SideLabel(
                name = selfName,
                percent = selfPercent,
                amount = Money.format(shares.selfMinor, currency),
                alignEnd = false,
            )
            SideLabel(
                name = otherName,
                percent = 100 - selfPercent,
                amount = Money.format(shares.otherMinor, currency),
                alignEnd = true,
            )
        }
        Slider(
            value = selfPercent.toFloat(),
            onValueChange = { onSelfPercentChange(it.toInt().coerceIn(0, 100)) },
            valueRange = 0f..100f,
        )
    }
}

@Composable
private fun SideLabel(name: String, percent: Int, amount: String, alignEnd: Boolean) {
    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(
            "$name · $percent%",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(amount, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ExactEditor(
    selfName: String,
    otherName: String,
    exactSelfText: String,
    exactOtherText: String,
    onExactSelfChange: (String) -> Unit,
    onExactOtherChange: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = exactSelfText,
            onValueChange = onExactSelfChange,
            label = { Text(selfName) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = exactOtherText,
            onValueChange = onExactOtherChange,
            label = { Text(otherName) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
    Text(
        "Shares always add up to the total — edit one side and the other adjusts.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}
