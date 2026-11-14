package com.example.securenotes

import com.example.securenotes.security.PasswordHasher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherTest {

    @Test
    fun generateSalt_returnsNonEmptyUniqueHex() {
        val salt1 = PasswordHasher.generateSalt()
        val salt2 = PasswordHasher.generateSalt()

        assertNotNull(salt1)
        assertNotNull(salt2)
        assertEquals(32, salt1.length) // 16 bytes = 32 hex chars
        assertNotEquals(salt1, salt2) // Unique salt per call
    }

    @Test
    fun hashPassword_deterministicWithSameSalt() {
        val salt = PasswordHasher.generateSalt()
        val password = "MyMasterPassword123!".toCharArray()

        val hash1 = PasswordHasher.hashPassword(password, salt)
        val hash2 = PasswordHasher.hashPassword(password, salt)

        assertEquals(hash1, hash2)
        assertEquals(64, hash1.length) // 256 bits = 64 hex chars
    }

    @Test
    fun verifyPassword_correctPassword_returnsTrue() {
        val salt = PasswordHasher.generateSalt()
        val password = "VaultMasterKey2025#".toCharArray()
        val hash = PasswordHasher.hashPassword(password, salt)

        val isVerified = PasswordHasher.verifyPassword("VaultMasterKey2025#".toCharArray(), hash, salt)
        assertTrue(isVerified)
    }

    @Test
    fun verifyPassword_incorrectPassword_returnsFalse() {
        val salt = PasswordHasher.generateSalt()
        val password = "CorrectPassword123".toCharArray()
        val hash = PasswordHasher.hashPassword(password, salt)

        val isVerified = PasswordHasher.verifyPassword("WrongPassword123".toCharArray(), hash, salt)
        assertFalse(isVerified)
    }
}
