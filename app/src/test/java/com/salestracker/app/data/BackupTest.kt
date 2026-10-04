package com.salestracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {
    private val sample = AppData(
        clients = listOf(Client(1, "Ana", "García", "555-0100", "ana@example.com", "VIP — Café ☕", reference = "WO-1042")),
        sales = listOf(Sale(2, 1, 1_700_000_000_000, true, true, true, 1200.0, 300.0, 900, "notes", 10.0)),
        appointments = listOf(Appointment(3, "Follow-up", 20_000, 600, 1, "", 15)),
        goals = listOf(Goal(4, "Q4", GoalScope.COMPANY, GoalMetric.REVENUE, GoalPeriod.QUARTER, 100_000.0)),
        defaultCommissionPercent = 7.5,
        dayHighlights = mapOf(20_000L to HighlightColor.RED, 20_005L to HighlightColor.BLUE),
    )
    private val password = "correct horse".toCharArray()

    // Fewer rounds keep the test fast; the format and code path are identical.
    private fun encrypt(data: AppData = sample) = Backup.encrypt(data, password.copyOf(), rounds = 2_000, now = 42L)

    @Test fun roundTripRestoresEverything() {
        val restored = Backup.decrypt(encrypt(), password.copyOf())
        assertEquals(sample, restored.data)
        assertEquals(42L, restored.createdAt)
    }

    @Test fun fileDoesNotContainPlainText() {
        val text = String(encrypt(), Charsets.ISO_8859_1)
        assertFalse(text.contains("García") || text.contains("ana@example.com") || text.contains("Follow-up"))
    }

    @Test fun sameDataEncryptsDifferentlyEachTime() {
        assertFalse(encrypt().contentEquals(encrypt()))
    }

    @Test(expected = Backup.WrongPasswordException::class)
    fun wrongPasswordIsRejected() {
        Backup.decrypt(encrypt(), "wrong password".toCharArray())
    }

    @Test(expected = Backup.WrongPasswordException::class)
    fun tamperedFileIsRejected() {
        val bytes = encrypt()
        bytes[bytes.size - 5] = (bytes[bytes.size - 5].toInt() xor 1).toByte()
        Backup.decrypt(bytes, password.copyOf())
    }

    @Test(expected = Backup.WrongPasswordException::class)
    fun tamperedHeaderIsRejected() {
        val bytes = encrypt()
        bytes[20] = (bytes[20].toInt() xor 1).toByte() // inside the salt
        Backup.decrypt(bytes, password.copyOf())
    }

    @Test(expected = Backup.NotABackupException::class)
    fun randomFileIsNotABackup() {
        Backup.decrypt("hello, this is not a backup file at all".toByteArray(), password.copyOf())
    }

    @Test fun emptyDataRoundTrips() {
        val restored = Backup.decrypt(encrypt(AppData()), password.copyOf())
        assertTrue(restored.data.clients.isEmpty() && restored.data.sales.isEmpty())
    }
}
