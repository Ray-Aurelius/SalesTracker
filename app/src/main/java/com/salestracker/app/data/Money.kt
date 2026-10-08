package com.salestracker.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Currency
import java.util.Locale

/**
 * The currency amounts are shown in. Null = automatic: the currency of the phone's region
 * (dollars in the US, euros in Germany, pesos in Mexico…). Only the symbol and number format change;
 * amounts already entered are never converted.
 */
object MoneySettings {
    var currencyCode by mutableStateOf<String?>(null)
}

/** The currency in use: the one chosen in Settings, else the phone region's, else US dollars. */
fun moneyCurrency(): Currency {
    MoneySettings.currencyCode?.let { code -> runCatching { return Currency.getInstance(code) } }
    return regionCurrency()
}

/** The phone region's own currency. Falls back to US dollars when the phone doesn't say which country it's in. */
fun regionCurrency(): Currency {
    for (l in listOf(regionLocale(), Locale.getDefault())) {
        if (l.country.length != 2) continue
        val c = runCatching { Currency.getInstance(l) }.getOrNull()
        if (c != null && c.currencyCode != "XXX") return c
    }
    return Currency.getInstance("USD")
}

/** The symbol to show beside amount boxes, e.g. "$", "€", "£", "₹", "R$". */
fun currencySymbol(): String = moneyCurrency().getSymbol(regionLocale())

/** Whether this region writes the symbol after the number ("12,50 €") rather than before ("$12.50"). */
fun currencySymbolAfter(): Boolean {
    val sample = java.text.NumberFormat.getCurrencyInstance(regionLocale()).apply { currency = moneyCurrency() }.format(1.0)
    val symbol = currencySymbol()
    return !sample.trimStart().startsWith(symbol) && sample.trimEnd().endsWith(symbol)
}

/** The currencies offered in Settings: the ones most used around the world, by people in the app's languages. */
val COMMON_CURRENCIES = listOf(
    "USD", "EUR", "GBP", "CAD", "AUD", "NZD", "CHF", "SEK", "NOK", "DKK", "PLN", "CZK", "HUF", "RON",
    "MXN", "BRL", "ARS", "COP", "CLP", "PEN",
    "INR", "BDT", "PKR", "LKR", "NPR",
    "CNY", "HKD", "TWD", "JPY", "KRW", "SGD", "MYR", "IDR", "PHP", "THB", "VND",
    "RUB", "UAH", "KZT", "TRY",
    "AED", "SAR", "QAR", "KWD", "EGP", "MAD", "ILS",
    "ZAR", "NGN", "KES", "GHS",
)
