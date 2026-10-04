package com.salestracker.app.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM with a key that lives inside the Android Keystore (the phone's secure hardware on most devices).
 * The key can be used by this app on this phone only. It can't be read out, copied or backed up,
 * so a copy of the data file taken off the phone is useless.
 */
class KeystoreCipher private constructor(private val key: SecretKey) : DataCipher {

    override fun encrypt(plain: ByteArray): Pair<ByteArray, ByteArray> {
        val c = Cipher.getInstance(TRANSFORMATION)
        c.init(Cipher.ENCRYPT_MODE, key) // the Keystore picks a fresh random IV every time
        val encrypted = c.doFinal(plain)
        return c.iv to encrypted
    }

    override fun decrypt(iv: ByteArray, encrypted: ByteArray): ByteArray {
        val c = Cipher.getInstance(TRANSFORMATION)
        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return c.doFinal(encrypted)
    }

    companion object {
        private const val ALIAS = "usp_data_key_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"

        /** The app's key, created on first use. Null only if this phone's Keystore is broken. */
        fun getOrNull(): KeystoreCipher? = try {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val existing = ks.getKey(ALIAS, null) as? SecretKey
            KeystoreCipher(existing ?: create())
        } catch (e: Exception) {
            null
        }

        private fun create(): SecretKey {
            val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            gen.init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            return gen.generateKey()
        }

        /** Destroys the key. Anything encrypted with it becomes permanently unreadable. Used by "Erase all data". */
        fun destroy() {
            try {
                KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(ALIAS)
            } catch (e: Exception) {
                // Nothing to delete.
            }
        }
    }
}
