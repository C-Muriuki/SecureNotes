package com.example.securenotes

import com.example.securenotes.customviews.PasswordStrength
import com.example.securenotes.customviews.PasswordStrengthCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

class PasswordStrengthCalculatorTest {
    @Test fun emptyPasswordHasNoStrengthSegments() {
        assertEquals(PasswordStrength.EMPTY, PasswordStrengthCalculator.evaluate(""))
        assertEquals(PasswordStrength.EMPTY, PasswordStrengthCalculator.evaluate(null))
    }

    @Test fun limitedPasswordMixIsWeakOrFair() {
        assertEquals(PasswordStrength.WEAK, PasswordStrengthCalculator.evaluate("abc"))
        assertEquals(PasswordStrength.FAIR, PasswordStrengthCalculator.evaluate("Secret123"))
    }

    @Test fun longerPasswordWithMultipleCharacterTypesIsStrong() {
        assertEquals(PasswordStrength.STRONG, PasswordStrengthCalculator.evaluate("Secure@123456"))
    }
}
