package com.salestracker.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MoneyTest {
    @After fun reset() { MoneySettings.currencyCode = null; PrivacyMode.hideAmounts = false }

    @Test @Config(sdk = [34], qualifiers = "en")
    fun languageWithoutCountryShowsDollarsNotPlaceholder() {
        val s = formatMoney(1250.5)
        assertFalse("got $s", s.contains('¤'))
        assertTrue("got $s", s.contains('$'))
    }

    @Test @Config(sdk = [34], qualifiers = "de-rDE")
    fun germanyShowsEuros() {
        val s = formatMoney(1250.5)
        assertTrue("got $s", s.contains('€'))
        assertEquals(1250.5, parseMoney("1.250,50")!!, 0.001)
    }

    @Test @Config(sdk = [34], qualifiers = "en-rUS")
    fun chosenCurrencyWins() {
        MoneySettings.currencyCode = "GBP"
        assertTrue(formatMoney(10.0).contains('£'))
        MoneySettings.currencyCode = "JPY"
        assertFalse("yen has no cents: ${formatMoney(1500.0)}", formatMoney(1500.0).contains('.'))
    }

    @Test fun parsesBothStyles() {
        assertEquals(1250.5, parseMoney("$1,250.50", '.')!!, 0.001)
        assertEquals(1250.5, parseMoney("1.250,50 €", ',')!!, 0.001)
        assertEquals(1250.0, parseMoney("1.250", ',')!!, 0.001)
        assertEquals(1250.0, parseMoney("1,250", '.')!!, 0.001)
        assertEquals(12.5, parseMoney("12,5", '.')!!, 0.001)
        assertEquals(12.5, parseMoney("12.5", ',')!!, 0.001)
        assertEquals(1250000.0, parseMoney("1 250 000", ',')!!, 0.001)
        assertEquals(99.0, parseMoney("99", '.')!!, 0.001)
        assertEquals(null, parseMoney("abc", '.'))
    }
}
