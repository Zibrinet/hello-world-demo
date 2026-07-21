package com.zibrinet.split.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

/**
 * Big tactile numeric keypad: the fast path for amount entry. The decimal key
 * disappears for zero-decimal currencies (JPY etc.).
 */
@Composable
fun AmountKeypad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    decimalEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val rows = listOf("123", "456", "789")
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { digit ->
                    KeypadKey(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                            onDigit(digit)
                        },
                    ) {
                        Text(digit.toString(), style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (decimalEnabled) {
                KeypadKey(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                        onDigit('.')
                    },
                ) {
                    Text(".", style = MaterialTheme.typography.headlineSmall)
                }
            } else {
                Box(Modifier.weight(1f))
            }
            KeypadKey(
                modifier = Modifier.weight(1f),
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                    onDigit('0')
                },
            ) {
                Text("0", style = MaterialTheme.typography.headlineSmall)
            }
            KeypadKey(
                modifier = Modifier.weight(1f),
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                    onBackspace()
                },
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.Backspace,
                    contentDescription = "Delete last digit",
                )
            }
        }
    }
}

@Composable
private fun KeypadKey(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}
