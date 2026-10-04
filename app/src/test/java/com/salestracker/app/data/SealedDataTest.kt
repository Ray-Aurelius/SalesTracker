package com.salestracker.app.data

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Same algorithm as the phone's Keystore cipher, with a key held in memory for testing. */
private class SoftwareCipher(private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()) : DataCipher {
    override fun encrypt(plain: ByteArray): Pair<ByteArray, ByteArray> {
        val iv = ByteArray(12).also(SecureRandom()::nextBytes)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv)) }
        return iv to c.doFinal(plain)
    }
    override fun decrypt(iv: ByteArray, encrypted: ByteArray): ByteArray =
        Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv)) }.doFinal(encrypted)
}

class SealedDataTest {
    private val data = AppData(
        clients = listOf(Client(1, "Private", "Person", "555-0199", "secret@example.com")),
        defaultCommissionPercent = 12.0,
    )
    private val json = data.toJson().toString().toByteArray()

    @Test fun roundTrip() {
        val cipher = SoftwareCipher()
        val sealed = SealedData.seal(json, cipher)
        assertTrue(SealedData.isSealed(sealed))
        assertArrayEquals(json, SealedData.open(sealed, cipher))
        assertEquals(data, AppData.fromJson(JSONObject(String(SealedData.open(sealed, cipher)))))
    }

    @Test fun noPlainTextOnDisk() {
        val text = String(SealedData.seal(json, SoftwareCipher()), Charsets.ISO_8859_1)
        assertFalse(text.contains("secret@example.com") || text.contains("Private"))
    }

    @Test fun plainJsonIsNotMistakenForSealed() {
        assertFalse(SealedData.isSealed(json))
    }

    @Test(expected = SealedData.UnreadableException::class)
    fun differentKeyCannotOpen() {
        SealedData.open(SealedData.seal(json, SoftwareCipher()), SoftwareCipher())
    }

    @Test(expected = SealedData.UnreadableException::class)
    fun tamperingIsDetected() {
        val cipher = SoftwareCipher()
        val sealed = SealedData.seal(json, cipher)
        sealed[sealed.size - 1] = (sealed[sealed.size - 1].toInt() xor 0x40).toByte()
        SealedData.open(sealed, cipher)
    }
}
