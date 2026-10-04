package com.salestracker.app.data

import java.time.format.FormatStyle
import android.content.res.Resources
import android.text.format.DateFormat
import androidx.appcompat.app.AppCompatDelegate
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The language chosen in the app (or the phone's language if none was chosen). Used for words and dates. */
fun appLocale(): Locale = AppCompatDelegate.getApplicationLocales()[0] ?: Locale.getDefault()

/**
 * The phone's own region setting. Money always uses this, so switching the app to Spanish
 * doesn't turn your dollars into euros.
 */
fun regionLocale(): Locale = Resources.getSystem().configuration.locales[0] ?: Locale.getDefault()

/** A date/time format laid out the way the chosen language expects (e.g. "October 2026" vs "2026年10月"). */
fun localizedFormatter(skeleton: String, locale: Locale = appLocale()): DateTimeFormatter = try {
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
} catch (e: IllegalArgumentException) {
    // A few languages use pattern letters older Android versions can't read; fall back to a standard format.
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
}

fun formatDuration(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(Locale.ROOT, h, m, s) else "%d:%02d".format(Locale.ROOT, m, s)
}

fun formatMoney(value: Double): String =
    if (PrivacyMode.hideAmounts) PrivacyMode.MASK else NumberFormat.getCurrencyInstance(regionLocale()).format(value)

fun formatPercent(ratio: Double): String = "%.1f%%".format(regionLocale(), ratio * 100)

fun formatDateTime(millis: Long): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(appLocale())
        .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

/** Uses the 12- or 24-hour clock that's normal for the chosen language. */
fun formatMinuteOfDay(minuteOfDay: Int): String =
    LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(appLocale()))

/** Accepts "1,250.50", "$99", " 12 " etc. */
fun parseMoney(text: String): Double? =
    text.replace(Regex("[^0-9.\\-]"), "").takeIf { it.isNotEmpty() }?.toDoubleOrNull()
