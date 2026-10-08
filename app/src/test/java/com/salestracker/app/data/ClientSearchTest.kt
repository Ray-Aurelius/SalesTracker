package com.salestracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ClientSearchTest {
    private val ann = Client(1, "Ann", "Lee", "555-0110", "ann@shop.com", occupation = "Dentist")
    private val bob = Client(2, "Bob", "Annan", "(555) 777-0199", "bob@mail.net", reference = "WO-1042")
    private val cy = Client(3, "Cy", "Stone", "", "cy@stone.io")
    private val all = listOf(cy, bob, ann)
    private fun ids(q: String) = ClientSearch.filter(all, q).map { it.id }

    @Test fun byName() {
        assertEquals(listOf(1L, 2L), ids("ann"))        // first name Ann first, then last name Annan
        assertEquals(listOf(1L), ids("ann lee"))
        assertEquals(listOf(3L), ids("STONE"))
    }
    @Test fun byPhoneIgnoringPunctuation() {
        assertEquals(listOf(1L), ids("5550110"))
        assertEquals(listOf(2L), ids("777 0199"))
        assertEquals(listOf(2L), ids("(555) 777"))
    }
    @Test fun byEmailJobAndOccupation() {
        assertEquals(listOf(3L), ids("stone.io"))
        assertEquals(listOf(2L), ids("wo-1042"))
        assertEquals(listOf(1L), ids("dentist"))
    }
    @Test fun emptyShowsEveryoneAlphabetically() = assertEquals(listOf(1L, 2L, 3L), ids(""))
}
