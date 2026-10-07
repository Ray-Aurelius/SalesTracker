package com.salestracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ClientLabelTest {
    private fun client(first: String = "", last: String = "", phone: String = "", email: String = "", ref: String = "", notes: String = "") =
        Client(1, first, last, phone, email, notes = notes, reference = ref)

    @Test fun nameWins() = assertEquals("Ann Lee", client("Ann", "Lee", ref = "WO-1").label { "Job # $it" })
    @Test fun workOrderOnly() = assertEquals("Job # WO-1042", client(ref = "WO-1042").label { "Job # $it" })
    @Test fun phoneThenEmail() {
        assertEquals("555-0100", client(phone = "555-0100", email = "a@b.co").label())
        assertEquals("a@b.co", client(email = "a@b.co").label())
    }
    @Test fun notesFirstLine() = assertEquals("Blue house", client(notes = "Blue house\nback gate").label())
}
