package com.example.securenotes.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PasswordHasher {
    var iterations: Int = 310_000
        set(value) { require(value >= 100_000); field = value }
    var keyLengthBits: Int = 256
        set(value) { require(value >= 128); field = value }

    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val SALT_SIZE_BYTES = 16
    private val secureRandom = SecureRandom()

    @Synchronized
    fun generateSalt(): String = ByteArray(SALT_SIZE_BYTES).also(secureRandom::nextBytes).toHex()

    fun hashPassword(password: CharArray, saltHex: String): String {
        val saltBytes = saltHex.hexToBytes()
        require(saltBytes.size >= 16) { "Salt must contain at least 16 bytes." }
        val spec = PBEKeySpec(password, saltBytes, iterations, keyLengthBits)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded.toHex()
        } finally {
            spec.clearPassword()
        }
    }

    fun verifyPassword(password: CharArray, storedHashHex: String, storedSaltHex: String): Boolean {
        return try {
            val expected = storedHashHex.hexToBytes()
            val actual = hashPassword(password, storedSaltHex).hexToBytes()
            expected.isNotEmpty() && MessageDigest.isEqual(actual, expected)
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0 && all { it.digitToIntOrNull(16) != null }) { "Invalid hexadecimal value." }
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
