package com.salestracker.app

import com.salestracker.app.data.Feedback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackTest {
    @Test fun bodyHasMessageTagAndDetails() {
        val b = Feedback.body("  Love the widgets  ", Feedback.Kind.LOVE, "Quota Vault 1.0.1 (1) · Android 15 · Pixel 8")
        assertTrue(b.startsWith("Love the widgets\n"))
        assertTrue(b.contains("[love]"))
        assertTrue(b.endsWith("Pixel 8"))
    }

    @Test fun detailsLeftOutWhenUnticked() {
        val b = Feedback.body("Hi", Feedback.Kind.PROBLEM, null)
        assertTrue(b.endsWith("[problem]"))
        assertFalse(b.contains("Android"))
    }

    @Test fun tagsAreStableEnglish() =
        assertEquals(listOf("idea", "problem", "question", "love"), Feedback.Kind.entries.map { it.tag })
}
