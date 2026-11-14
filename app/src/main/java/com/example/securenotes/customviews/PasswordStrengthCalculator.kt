package com.example.securenotes.customviews

/**
 * Small, presentation-independent strength estimate for the registration hint.
 * Account acceptance remains controlled by ValidationUtils.
 */
enum class PasswordStrength(val filledSegments: Int) {
    EMPTY(0),
    WEAK(1),
    FAIR(2),
    STRONG(3)
}

object PasswordStrengthCalculator {
    fun evaluate(password: CharSequence?): PasswordStrength {
        if (password.isNullOrEmpty()) return PasswordStrength.EMPTY

        var hasLower = false
        var hasUpper = false
        var hasDigit = false
        var hasSymbol = false
        password.forEach { character ->
            when {
                character.isLowerCase() -> hasLower = true
                character.isUpperCase() -> hasUpper = true
                character.isDigit() -> hasDigit = true
                !character.isLetterOrDigit() -> hasSymbol = true
            }
        }

        val score = (if (password.length >= 8) 1 else 0) +
            (if (password.length >= 12) 1 else 0) +
            (if (hasLower) 1 else 0) +
            (if (hasUpper) 1 else 0) +
            (if (hasDigit) 1 else 0) +
            (if (hasSymbol) 1 else 0)

        return when {
            score >= 5 -> PasswordStrength.STRONG
            score >= 3 -> PasswordStrength.FAIR
            else -> PasswordStrength.WEAK
        }
    }
}
