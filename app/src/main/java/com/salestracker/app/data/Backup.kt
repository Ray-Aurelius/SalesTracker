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
 * File layout: "STBK" | version (1 byte) | rounds (4 bytes) | salt (16) | iv (12) | encrypted payload.
 * Version 1's payload is gzip'd JSON. Version 2's is: JSON length (4) | gzip'd JSON | photo count (4) | then for each
 * receipt photo: id (8) | length (4) | JPEG. The whole payload, photos included, is encrypted with the password,
 * and the header is authenticated too, so nothing can be altered without the file failing to open.
 */
object Backup {
    private val MAGIC = byteArrayOf('S'.code.toByte(), 'T'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte())
    private const val VERSION: Byte = 2
    private const val MAX_PHOTO_BYTES = 25 * 1024 * 1024
    const val DEFAULT_ROUNDS = 210_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val HEADER_BYTES = 4 + 1 + 4 + SALT_BYTES + IV_BYTES
    const val MIN_PASSWORD_LENGTH = 8

    class NotABackupException : Exception("Not an Quota Vault backup")
    class WrongPasswordException : Exception("Wrong password or damaged file")

    /** What a backup holds, plus when it was made. [receipts] maps each receipt photo's id to its JPEG. */
    class Contents(val data: AppData, val createdAt: Long, val receipts: Map<Long, ByteArray> = emptyMap())

    fun encrypt(
        data: AppData,
        password: CharArray,
        receipts: Map<Long, ByteArray> = emptyMap(),
        rounds: Int = DEFAULT_ROUNDS,
        now: Long = System.currentTimeMillis(),
    ): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER_BYTES).put(MAGIC).put(VERSION).putInt(rounds).put(salt).put(iv).array()

        val json = gzip(JSONObject().put("createdAt", now).put("data", data.toJson()).toString().toByteArray(Charsets.UTF_8))
        val payload = ByteArrayOutputStream(json.size + receipts.values.sumOf { it.size + 12 } + 8)
        java.io.DataOutputStream(payload).use { out ->
            out.writeInt(json.size)
            out.write(json)
            out.writeInt(receipts.size)
            receipts.forEach { (id, jpeg) ->
                out.writeLong(id)
                out.writeInt(jpeg.size)
                out.write(jpeg)
            }
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt, rounds), GCMParameterSpec(128, iv))
        cipher.updateAAD(header)
        return header + cipher.doFinal(payload.toByteArray())
    }

    fun decrypt(file: ByteArray, password: CharArray): Contents {
        if (file.size <= HEADER_BYTES || !file.copyOfRange(0, 4).contentEquals(MAGIC) || file[4] !in 1..VERSION) {
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
        if (file[4] == 1.toByte()) {
            // Made before receipt photos were included.
            val o = JSONObject(String(gunzip(plain), Charsets.UTF_8))
            return Contents(AppData.fromJson(o.getJSONObject("data")), o.optLong("createdAt"))
        }
        val input = java.io.DataInputStream(ByteArrayInputStream(plain))
        val jsonBytes = ByteArray(input.readInt().also { if (it !in 1..plain.size) throw NotABackupException() })
        input.readFully(jsonBytes)
        val o = JSONObject(String(gunzip(jsonBytes), Charsets.UTF_8))
        val count = input.readInt()
        if (count !in 0..100_000) throw NotABackupException()
        val receipts = LinkedHashMap<Long, ByteArray>(count)
        repeat(count) {
            val id = input.readLong()
            val size = input.readInt()
            if (size !in 1..MAX_PHOTO_BYTES) throw NotABackupException()
            receipts[id] = ByteArray(size).also { input.readFully(it) }
        }
        return Contents(AppData.fromJson(o.getJSONObject("data")), o.optLong("createdAt"), receipts)
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
