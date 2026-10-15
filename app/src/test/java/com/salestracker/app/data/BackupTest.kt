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

    @Test fun receiptPhotosRoundTrip() {
        val photos = mapOf(2L to ByteArray(50_000) { (it * 7).toByte() }, 9L to byteArrayOf(-1, -40, -1, -39))
        val file = Backup.encrypt(sample, password.copyOf(), photos, rounds = 2_000, now = 42L)
        val restored = Backup.decrypt(file, password.copyOf())
        assertEquals(sample, restored.data)
        assertEquals(photos.keys, restored.receipts.keys)
        photos.forEach { (id, bytes) -> assertTrue(bytes.contentEquals(restored.receipts[id])) }
    }

    @Test fun receiptPhotosAreEncrypted() {
        val marker = "JPEG-MARKER-PLAINTEXT".toByteArray()
        val file = Backup.encrypt(sample, password.copyOf(), mapOf(2L to marker), rounds = 2_000, now = 42L)
        assertFalse(String(file, Charsets.ISO_8859_1).contains("JPEG-MARKER-PLAINTEXT"))
    }

    @Test fun backupWithoutPhotosRestoresNone() {
        assertTrue(Backup.decrypt(encrypt(), password.copyOf()).receipts.isEmpty())
    }

    /** Files made by earlier versions of the app (format 1: gzip JSON only) must still restore. */
    @Test fun versionOneBackupStillRestores() {
        val salt = ByteArray(16) { it.toByte() }
        val iv = ByteArray(12) { (it + 3).toByte() }
        val rounds = 2_000
        val header = java.nio.ByteBuffer.allocate(37).put("STBK".toByteArray()).put(1.toByte()).putInt(rounds).put(salt).put(iv).array()
        val json = org.json.JSONObject().put("createdAt", 7L).put("data", sample.toJson()).toString().toByteArray()
        val gz = java.io.ByteArrayOutputStream().also { o -> java.util.zip.GZIPOutputStream(o).use { it.write(json) } }.toByteArray()
        val keyBytes = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(javax.crypto.spec.PBEKeySpec(password.copyOf(), salt, rounds, 256)).encoded
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, javax.crypto.spec.SecretKeySpec(keyBytes, "AES"), javax.crypto.spec.GCMParameterSpec(128, iv))
        cipher.updateAAD(header)
        val restored = Backup.decrypt(header + cipher.doFinal(gz), password.copyOf())
        assertEquals(sample, restored.data)
        assertEquals(7L, restored.createdAt)
        assertTrue(restored.receipts.isEmpty())
    }
}
