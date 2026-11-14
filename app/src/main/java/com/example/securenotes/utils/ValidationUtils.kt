package com.example.securenotes.utils

import java.util.regex.Pattern

data class ValidationResult(val isValid: Boolean, val errorMessage: String? = null)

object ValidationUtils {
    private val emailPattern = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE)

    fun isNotEmpty(text: String?): Boolean = !text.isNullOrBlank()
    fun isValidEmail(email: String?): Boolean = !email.isNullOrBlank() && emailPattern.matcher(email.trim()).matches()
    fun isValidPassword(password: String?): Boolean =
        !password.isNullOrBlank() && password.length >= 8 && password.any { it.isDigit() || !it.isLetterOrDigit() }

    fun validateRegistration(fullName: String?, email: String?, password: String?, confirmPassword: String?): ValidationResult {
        if (!isNotEmpty(fullName)) return ValidationResult(false, "Full name is required")
        if (!isValidEmail(email)) return ValidationResult(false, "Please enter a valid email address")
        if (!isValidPassword(password)) return ValidationResult(false, "Password must be at least 8 characters and contain at least one digit or symbol")
        if (password != confirmPassword) return ValidationResult(false, "Passwords do not match")
        return ValidationResult(true)
    }

    fun validateLogin(email: String?, password: String?): ValidationResult {
        if (!isValidEmail(email)) return ValidationResult(false, "Please enter a valid email address")
        if (!isNotEmpty(password)) return ValidationResult(false, "Password cannot be empty")
        return ValidationResult(true)
    }
}
