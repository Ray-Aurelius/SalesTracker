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

fun formatMoney(value: Double): String {
    if (PrivacyMode.hideAmounts) return PrivacyMode.MASK
    return formatMoneyForFile(value)
}

/** The real amount even in privacy mode: for reports the user chose to create (privacy mode only hides the screen). */
fun formatMoneyForFile(value: Double): String {
    val currency = moneyCurrency()
    return NumberFormat.getCurrencyInstance(regionLocale()).apply {
        // Set the currency explicitly: a phone whose language has no country (e.g. plain "English")
        // would otherwise get Android's placeholder sign "¤" instead of $, € or £.
        this.currency = currency
        val digits = currency.defaultFractionDigits.coerceAtLeast(0)
        minimumFractionDigits = digits
        maximumFractionDigits = digits
    }.format(value)
}

fun formatPercent(ratio: Double): String = "%.1f%%".format(regionLocale(), ratio * 100)

fun formatDateTime(millis: Long): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(appLocale())
        .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

/** Uses the 12- or 24-hour clock that's normal for the chosen language. */
fun formatMinuteOfDay(minuteOfDay: Int): String =
    LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(appLocale()))

/**
 * Reads an amount the way it's written where the user lives: "1,250.50", "1.250,50", "1 250,50", "$99", "12".
 * With only one kind of separator, the region's decimal mark counts as decimal; the other one counts as
 * thousands only when exactly three digits follow it ("1.250" = 1250 in Germany, "12.5" = 12.5 anywhere).
 */
fun parseMoney(text: String, decimalMark: Char = regionDecimalMark()): Double? {
    val negative = text.trim().startsWith("-") || text.trim().startsWith("\u2212")
    val t = text.filter { it.isDigit() || it == '.' || it == ',' }
    if (t.none { it.isDigit() }) return null
    val lastDot = t.lastIndexOf('.'); val lastComma = t.lastIndexOf(',')
    val decimalAt = when {
        lastDot >= 0 && lastComma >= 0 -> maxOf(lastDot, lastComma)           // both: the later one is the decimal
        lastDot < 0 && lastComma < 0 -> -1
        else -> {
            val at = maxOf(lastDot, lastComma); val mark = t[at]
            val after = t.length - at - 1
            val single = t.count { it == mark } == 1
            if (mark == decimalMark && single) at
            else if (single && after != 3) at                                    // "12.5", "99,99"
            else -1                                                               // "1.250", "1,250,000"
        }
    }
    val whole = (if (decimalAt >= 0) t.substring(0, decimalAt) else t).filter { it.isDigit() }
    val frac = if (decimalAt >= 0) t.substring(decimalAt + 1).filter { it.isDigit() } else ""
    val v = "${whole.ifEmpty { "0" }}.${frac.ifEmpty { "0" }}".toDoubleOrNull() ?: return null
    return if (negative) -v else v
}

/** The decimal mark used in the phone's region ("." in the US, "," in most of Europe). */
fun regionDecimalMark(): Char = java.text.DecimalFormatSymbols.getInstance(regionLocale()).decimalSeparator

/** An amount pre-filled into a text box: the region's decimal mark, no thousands separators, no symbol. */
fun formatAmountInput(value: Double): String {
    val digits = moneyCurrency().defaultFractionDigits.coerceAtLeast(0)
    val f = java.text.DecimalFormat("0" + if (digits > 0) "." + "#".repeat(digits) else "", java.text.DecimalFormatSymbols.getInstance(regionLocale()))
    return f.format(value)
}
