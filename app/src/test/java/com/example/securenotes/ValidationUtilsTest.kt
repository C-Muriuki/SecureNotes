package com.example.securenotes

import com.example.securenotes.utils.ValidationUtils
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationUtilsTest {
    @Test fun isNotEmpty_rejectsBlankAndNull() {
        assertTrue(ValidationUtils.isNotEmpty("SecureNotes"))
        assertFalse(ValidationUtils.isNotEmpty(""))
        assertFalse(ValidationUtils.isNotEmpty("   "))
        assertFalse(ValidationUtils.isNotEmpty(null))
    }

    @Test fun emailValidation_acceptsValidAndRejectsMalformed() {
        assertTrue(ValidationUtils.isValidEmail("user@example.com"))
        assertTrue(ValidationUtils.isValidEmail("User.Name+tag@sub.example.org"))
        assertFalse(ValidationUtils.isValidEmail("not-an-email"))
        assertFalse(ValidationUtils.isValidEmail("user@"))
        assertFalse(ValidationUtils.isValidEmail(null))
    }

    @Test fun passwordValidation_requiresLengthAndNumberOrSymbol() {
        assertTrue(ValidationUtils.isValidPassword("Secret123"))
        assertTrue(ValidationUtils.isValidPassword("P@ssword"))
        assertFalse(ValidationUtils.isValidPassword("short"))
        assertFalse(ValidationUtils.isValidPassword("lettersOnly"))
        assertFalse(ValidationUtils.isValidPassword(null))
    }

    @Test fun registrationValidation_checksAllFields() {
        assertTrue(ValidationUtils.validateRegistration("Jane Doe", "jane@example.com", "Secure123", "Secure123").isValid)
        assertFalse(ValidationUtils.validateRegistration("", "jane@example.com", "Secure123", "Secure123").isValid)
        assertFalse(ValidationUtils.validateRegistration("Jane", "bad", "Secure123", "Secure123").isValid)
        assertFalse(ValidationUtils.validateRegistration("Jane", "jane@example.com", "Secure123", "Different123").isValid)
    }

    @Test fun loginValidation_rejectsEmptyInputs() {
        assertFalse(ValidationUtils.validateLogin("", "").isValid)
        assertFalse(ValidationUtils.validateLogin("jane@example.com", "").isValid)
    }
}
