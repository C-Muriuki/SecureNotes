package com.example.securenotes.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class EncryptionManager {
    data class EncryptedValue(val ciphertext: String, val iv: String)

    fun encrypt(userId: Long, plaintext: String): EncryptedValue =
        encryptWithKey(getOrCreateKey(userId), plaintext)

    /** Encrypts an update without replacing a missing account key. */
    fun encryptWithExistingKey(userId: Long, plaintext: String): EncryptedValue =
        encryptWithKey(getExistingKey(userId), plaintext)

    private fun encryptWithKey(key: SecretKey, plaintext: String): EncryptedValue {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        try {
            cipher.init(Cipher.ENCRYPT_MODE, key)
        } catch (error: Exception) {
            throw NoteKeyUnavailableException("The encryption key for this account is unavailable.", error)
        }
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return EncryptedValue(encoder.encodeToString(ciphertext), encoder.encodeToString(cipher.iv))
    }

    fun decrypt(userId: Long, ciphertextBase64: String, ivBase64: String): String {
        // A read must never create a replacement key: that key cannot decrypt existing rows.
        val key = getExistingKey(userId)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        try {
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(TAG_BITS, decoder.decode(ivBase64))
            )
        } catch (error: Exception) {
            throw NoteKeyUnavailableException("The encryption key for this account is unavailable.", error)
        }
        return try {
            cipher.doFinal(decoder.decode(ciphertextBase64)).toString(Charsets.UTF_8)
        } catch (error: Exception) {
            throw NoteDecryptionException("This note could not be authenticated or decrypted.", error)
        }
    }

    private fun getOrCreateKey(userId: Long): SecretKey {
        require(userId > 0) { "A valid account is required to access notes." }
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val alias = KEY_PREFIX + userId
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun getExistingKey(userId: Long): SecretKey {
        require(userId > 0) { "A valid account is required to access notes." }
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
            keyStore.getKey(KEY_PREFIX + userId, null) as? SecretKey
                ?: throw NoteKeyUnavailableException("The encryption key for this account is unavailable.")
        } catch (error: NoteKeyUnavailableException) {
            throw error
        } catch (error: Exception) {
            throw NoteKeyUnavailableException(
                "The encryption key for this account is unavailable.",
                error
            )
        }
    }

    class NoteKeyUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)

    class NoteDecryptionException(message: String, cause: Throwable) : Exception(message, cause)

    companion object {
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_PREFIX = "securenotes_user_"
        private const val KEY_SIZE_BITS = 256
        private const val TAG_BITS = 128
        private val encoder = Base64.getEncoder()
        private val decoder = Base64.getDecoder()
    }
}
