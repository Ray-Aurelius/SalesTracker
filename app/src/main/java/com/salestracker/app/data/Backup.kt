package com.salestracker.app.data

import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-protected backup files.
 *
 * The password is stretched into a 256-bit key with PBKDF2-HMAC-SHA256 (a random salt and many rounds,
 * so guessing passwords is slow), then the data is encrypted with AES-256-GCM, which also detects any
 * tampering or a wrong password. Nothing about the password is stored in the file.
 *
 * File layout: "STBK" | version (1 byte) | rounds (4 bytes) | salt (16) | iv (12) | encrypted gzip'd JSON.
 * The header is authenticated too, so it can't be altered without the file failing to open.
 */
object Backup {
    private val MAGIC = byteArrayOf('S'.code.toByte(), 'T'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte())
    private const val VERSION: Byte = 1
    const val DEFAULT_ROUNDS = 210_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val HEADER_BYTES = 4 + 1 + 4 + SALT_BYTES + IV_BYTES
    const val MIN_PASSWORD_LENGTH = 8

    class NotABackupException : Exception("Not an Ultimate Sales Productivity backup")
    class WrongPasswordException : Exception("Wrong password or damaged file")

    /** What a backup holds, plus when it was made. */
    data class Contents(val data: AppData, val createdAt: Long)

    fun encrypt(data: AppData, password: CharArray, rounds: Int = DEFAULT_ROUNDS, now: Long = System.currentTimeMillis()): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER_BYTES).put(MAGIC).put(VERSION).putInt(rounds).put(salt).put(iv).array()

        val json = JSONObject().put("createdAt", now).put("data", data.toJson()).toString()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt, rounds), GCMParameterSpec(128, iv))
        cipher.updateAAD(header)
        return header + cipher.doFinal(gzip(json.toByteArray(Charsets.UTF_8)))
    }

    fun decrypt(file: ByteArray, password: CharArray): Contents {
        if (file.size <= HEADER_BYTES || !file.copyOfRange(0, 4).contentEquals(MAGIC) || file[4] != VERSION) {
            throw NotABackupException()
        }
        val buf = ByteBuffer.wrap(file, 5, HEADER_BYTES - 5)
        val rounds = buf.int
        if (rounds !in 1_000..10_000_000) throw NotABackupException()
        val salt = ByteArray(SALT_BYTES).also { buf.get(it) }
        val iv = ByteArray(IV_BYTES).also { buf.get(it) }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt, rounds), GCMParameterSpec(128, iv))
        cipher.updateAAD(file, 0, HEADER_BYTES)
        val plain = try {
            cipher.doFinal(file, HEADER_BYTES, file.size - HEADER_BYTES)
        } catch (e: AEADBadTagException) {
            throw WrongPasswordException()
        }
        val o = JSONObject(String(gunzip(plain), Charsets.UTF_8))
        return Contents(AppData.fromJson(o.getJSONObject("data")), o.optLong("createdAt"))
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, rounds: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, rounds, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun gzip(b: ByteArray): ByteArray =
        ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(b) } }.toByteArray()

    private fun gunzip(b: ByteArray): ByteArray = GZIPInputStream(ByteArrayInputStream(b)).use { it.readBytes() }
}
