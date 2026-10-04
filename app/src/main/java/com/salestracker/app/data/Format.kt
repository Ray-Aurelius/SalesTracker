package com.salestracker.app.data

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateTimeFormat = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.getDefault())
private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

fun formatDuration(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatMoney(value: Double): String = NumberFormat.getCurrencyInstance().format(value)

fun formatPercent(ratio: Double): String = "%.1f%%".format(ratio * 100)

fun formatDateTime(millis: Long): String =
    dateTimeFormat.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

fun formatMinuteOfDay(minuteOfDay: Int): String =
    LocalTime.of(minuteOfDay / 60, minuteOfDay % 60).format(timeFormat)

/** Accepts "1,250.50", "$99", " 12 " etc. */
fun parseMoney(text: String): Double? =
    text.replace(Regex("[^0-9.\\-]"), "").takeIf { it.isNotEmpty() }?.toDoubleOrNull()
