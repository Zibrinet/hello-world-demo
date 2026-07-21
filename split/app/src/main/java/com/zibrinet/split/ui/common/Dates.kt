package com.zibrinet.split.ui.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dayMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
private val dayMonthYear: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

fun formatDate(epochMillis: Long): String {
    val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when {
        date == today -> "Today"
        date == today.minusDays(1) -> "Yesterday"
        date.year == today.year -> date.format(dayMonth)
        else -> date.format(dayMonthYear)
    }
}
