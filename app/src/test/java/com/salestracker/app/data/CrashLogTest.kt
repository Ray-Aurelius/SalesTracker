package com.salestracker.app.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
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
class CrashLogTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    @After fun clean() = CrashLog.deleteAll(app)

    /** Everything a user might have typed, planted in error messages, the way real crashes can quote input. */
    private val secrets = listOf("Jordan", "Lee", "555-0111", "jordan@example.com", "1,440.00", "WO-1040", "Dentist", "follow up re: quote")

    private fun crash(): Throwable {
        val inner = NumberFormatException("For input string: \"1,440.00\" (Jordan Lee, WO-1040, Dentist)")
        val middle = IllegalArgumentException("client jordan@example.com 555-0111", inner)
        return IllegalStateException("Could not save note: follow up re: quote", middle).apply {
            addSuppressed(RuntimeException("Lee"))
        }
    }

    @Test fun reportNeverContainsErrorMessagesOrUserText() {
        val text = CrashLog.format(crash(), "1.0.90 (90)", at = 0L)
        secrets.forEach { assertFalse("report leaked \"$it\":\n$text", text.contains(it)) }
        assertTrue(text.contains("java.lang.IllegalStateException"))
        assertTrue(text.contains("Caused by: java.lang.IllegalArgumentException"))
        assertTrue(text.contains("Caused by: java.lang.NumberFormatException"))
        assertTrue(text.contains("CrashLogTest.crash(CrashLogTest.kt:"))
    }

    /** Every line is one of the allowed kinds: a header field, an error type, a code location, or the closing note. */
    @Test fun reportHoldsOnlyAllowedLines() {
        val text = CrashLog.format(crash(), "1.0.90 (90)", at = 0L)
        val allowed = listOf(
            Regex("Quota Vault crash report"),
            Regex("App: 1\\.0\\.90 \\(90\\)"),
            Regex("Android: [^,]* \\(API \\d+\\)"),
            Regex("Phone: .*"),
            Regex("When: \\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2} UTC"),
            Regex("Thread: (main|background)"),
            Regex(""),
            Regex("(Caused by: )?[A-Za-z0-9_.$]+"),
            Regex("    at [A-Za-z0-9_.$<>-]+\\((native|unknown|[A-Za-z0-9_.$-]+(:\\d+)?)\\)"),
            Regex("    … \\d+ more"),
            Regex("Error messages are left out because they can contain text you typed\\."),
        )
        text.lines().forEach { line -> assertTrue("unexpected line: \"$line\"", allowed.any { it.matches(line) }) }
    }

    @Test fun causeLoopsDoNotHang() {
        val a = RuntimeException("a"); val b = RuntimeException("b", a); a.initCause(b)
        val text = CrashLog.format(a, "1", at = 0L)
        assertTrue(text.lines().count { it.contains("RuntimeException") } <= 2)
    }

    @Test fun keepsFiveNewestAndPromptsOncePerNewCrash() {
        val t = Thread.currentThread()
        assertFalse(CrashLog.hasUnseen(app))
        repeat(7) { CrashLog.save(app, t, crash(), now = 1_000_000L + it) }
        val now = 1_000_010L
        val reports = CrashLog.reports(app, now)
        assertEquals(5, reports.size)
        assertEquals(1_000_006L, reports.first().at)
        assertTrue(CrashLog.hasUnseen(app))
        CrashLog.markSeen(app)
        assertFalse(CrashLog.hasUnseen(app))
        CrashLog.save(app, t, crash(), now = 1_000_020L)
        assertTrue(CrashLog.hasUnseen(app))
        secrets.forEach { s -> CrashLog.reports(app, now).forEach { assertFalse(it.text.contains(s)) } }
    }

    @Test fun oldReportsExpireAndDeleteAllClearsEverything() {
        val t = Thread.currentThread()
        CrashLog.save(app, t, crash(), now = 0L)
        CrashLog.save(app, t, crash(), now = 40L * 24 * 60 * 60 * 1000)
        assertEquals(1, CrashLog.reports(app, now = 40L * 24 * 60 * 60 * 1000).size)
        CrashLog.deleteAll(app)
        assertEquals(0, CrashLog.reports(app).size)
        assertFalse(CrashLog.hasUnseen(app))
    }
}
