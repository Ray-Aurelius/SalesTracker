package com.salestracker.app.data

import java.nio.ByteBuffer

/** Encrypts and decrypts raw bytes. On the phone this is backed by the Android Keystore; tests use a software key. */
interface DataCipher {
    /** Returns the IV followed by the encrypted bytes (with authentication tag). */
    fun encrypt(plain: ByteArray): Pair<ByteArray, ByteArray>
    fun decrypt(iv: ByteArray, encrypted: ByteArray): ByteArray
}

/**
 * Layout of the encrypted data file on the phone: "USPD" | version | IV length | IV | AES-GCM ciphertext.
 * GCM's tag means any corruption or tampering is detected instead of silently loading bad data.
 */
object SealedData {
    private val MAGIC = "USPD".toByteArray(Charsets.US_ASCII)
    private const val VERSION: Byte = 1

    class UnreadableException(message: String) : Exception(message)

    fun seal(plain: ByteArray, cipher: DataCipher): ByteArray {
        val (iv, encrypted) = cipher.encrypt(plain)
        return ByteBuffer.allocate(MAGIC.size + 2 + iv.size + encrypted.size)
            .put(MAGIC).put(VERSION).put(iv.size.toByte()).put(iv).put(encrypted).array()
    }

    fun isSealed(bytes: ByteArray): Boolean = bytes.size > MAGIC.size + 2 && bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    fun open(bytes: ByteArray, cipher: DataCipher): ByteArray {
        if (!isSealed(bytes) || bytes[MAGIC.size] != VERSION) throw UnreadableException("not a sealed data file")
        val ivLen = bytes[MAGIC.size + 1].toInt()
        val start = MAGIC.size + 2
        if (ivLen !in 8..32 || bytes.size <= start + ivLen) throw UnreadableException("bad header")
        return try {
            cipher.decrypt(bytes.copyOfRange(start, start + ivLen), bytes.copyOfRange(start + ivLen, bytes.size))
        } catch (e: Exception) {
            throw UnreadableException("could not decrypt: ${e.javaClass.simpleName}")
        }
    }
}
