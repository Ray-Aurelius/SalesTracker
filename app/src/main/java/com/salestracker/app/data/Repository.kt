package com.salestracker.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom

/**
 * Stores everything in one file in the app's private storage, encrypted with a hardware-backed key
 * (see [KeystoreCipher]). The data set for one salesperson is small, so a single file is simple and fast.
 */
class Repository(context: Context, private val cipher: DataCipher? = KeystoreCipher.getOrNull()) {
    private val dir = context.filesDir
    private val sealedFile = File(dir, SEALED_NAME)
    private val legacyFile = File(dir, LEGACY_NAME)
    private val lock = Any()
    private var lastId = 0L

    /**
     * True if the encrypted file exists but can't be opened right now (the phone's Keystore didn't respond).
     * Saving is paused so the real data is never overwritten by an empty set.
     */
    private var readOnly = false

    /** Whether data is encrypted on this phone (false only if the phone's Keystore is unavailable). */
    val encryptedAtRest: Boolean get() = cipher != null

    private val _data = MutableStateFlow(load())
    val data: StateFlow<AppData> = _data.asStateFlow()

    private fun load(): AppData = try {
        when {
            sealedFile.exists() && cipher == null -> {
                readOnly = true
                AppData()
            }
            sealedFile.exists() -> parse(SealedData.open(sealedFile.readBytes(), cipher!!))
            legacyFile.exists() -> {
                // Data from before encryption: read it, save it encrypted, then destroy the plain copy.
                val data = parse(legacyFile.readBytes())
                if (cipher != null) {
                    write(data)
                    wipe(legacyFile)
                }
                data
            }
            else -> AppData()
        }
    } catch (e: Exception) {
        // Keep the unreadable file so nothing is silently lost, then start fresh.
        val bad = if (sealedFile.exists()) sealedFile else legacyFile
        bad.renameTo(File(dir, "sales_data.unreadable-${System.currentTimeMillis()}"))
        AppData()
    }

    fun update(transform: (AppData) -> AppData) {
        synchronized(lock) {
            val next = transform(_data.value)
            _data.value = next
            if (!readOnly) write(next)
        }
    }

    private fun write(data: AppData) {
        val json = data.toJson().toString().toByteArray(Charsets.UTF_8)
        val (target, bytes) = if (cipher != null) sealedFile to SealedData.seal(json, cipher) else legacyFile to json
        val tmp = File(dir, target.name + ".tmp")
        tmp.writeBytes(bytes)
        tmp.renameTo(target)
    }

    fun newId(): Long = synchronized(lock) {
        val now = System.currentTimeMillis()
        lastId = if (now > lastId) now else lastId + 1
        lastId
    }

    /** Erase all data: removes every data file and destroys the encryption key, so nothing can be recovered. */
    fun eraseEverything() {
        synchronized(lock) {
            _data.value = AppData()
            dir.listFiles()?.filter { it.name.startsWith("sales_data") }?.forEach(::wipe)
            KeystoreCipher.destroy()
            readOnly = false
        }
    }

    companion object {
        private const val SEALED_NAME = "sales_data.enc"
        private const val LEGACY_NAME = "sales_data.json"

        private fun parse(bytes: ByteArray) = AppData.fromJson(JSONObject(String(bytes, Charsets.UTF_8)))

        /** Overwrites a file with random bytes before deleting it, so old contents aren't left behind. */
        private fun wipe(f: File) {
            try {
                if (f.exists()) f.writeBytes(ByteArray(f.length().toInt()).also(SecureRandom()::nextBytes))
            } catch (e: Exception) {
                // Still delete below.
            }
            f.delete()
        }

        /**
         * Read-only snapshot of the saved data, for background work such as reminders
         * where the app's screens aren't running. Never modifies anything.
         */
        fun readSnapshot(context: Context): AppData = try {
            val sealed = File(context.filesDir, SEALED_NAME)
            val legacy = File(context.filesDir, LEGACY_NAME)
            when {
                sealed.exists() -> KeystoreCipher.getOrNull()?.let { parse(SealedData.open(sealed.readBytes(), it)) } ?: AppData()
                legacy.exists() -> parse(legacy.readBytes())
                else -> AppData()
            }
        } catch (e: Exception) {
            AppData()
        }
    }
}
