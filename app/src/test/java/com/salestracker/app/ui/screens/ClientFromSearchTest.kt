package com.salestracker.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What a salesperson typed into the client search becomes the start of the new client. */
class ClientFromSearchTest {
    @Test fun nameSplitsIntoFirstAndLast() {
        val c = clientFromSearch("  Riley   Quinn Jr ")!!
        assertEquals("Riley", c.firstName)
        assertEquals("Quinn Jr", c.lastName)
    }

    @Test fun singleWordIsAFirstName() {
        val c = clientFromSearch("Riley")!!
        assertEquals("Riley", c.firstName); assertEquals("", c.lastName)
    }

    @Test fun phoneNumberGoesToPhone() {
        val c = clientFromSearch("(555) 012-3456")!!
        assertEquals("(555) 012-3456", c.phone); assertEquals("", c.firstName)
    }

    @Test fun emailGoesToEmail() {
        assertEquals("riley@example.com", clientFromSearch("riley@example.com")!!.email)
    }

    @Test fun shortNumbersAreNotPhones() {
        // A job number like "1040" is more likely a name/reference search than a phone number.
        assertEquals("1040", clientFromSearch("1040")!!.firstName)
    }

    @Test fun blankGivesNothing() = assertNull(clientFromSearch("   "))
}
