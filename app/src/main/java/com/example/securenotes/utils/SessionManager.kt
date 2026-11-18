package com.example.securenotes.utils

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_SESSION, Context.MODE_PRIVATE)

    fun saveUserSession(userId: Long, email: String, fullName: String, username: String = "") {
        val cleanUsername = if (username.isBlank()) email.substringBefore('@') else username
        prefs.edit().apply {
            putLong(KEY_USER_ID, userId)
            putString(KEY_USER_EMAIL, email)
            putString(KEY_USER_FULL_NAME, fullName)
            putString(KEY_USER_USERNAME, cleanUsername)
            putBoolean(KEY_IS_LOGGED_IN, true)
            putLong(KEY_LOGIN_TIME, System.currentTimeMillis())
            apply()
        }
        resetFailedAttempts()
    }

    fun isLoggedIn(): Boolean {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    fun getCurrentUserId(): Long {
        return prefs.getLong(KEY_USER_ID, -1L)
    }

    fun getCurrentUserEmail(): String? {
        return prefs.getString(KEY_USER_EMAIL, null)
    }

    fun getCurrentUserFullName(): String? {
        return prefs.getString(KEY_USER_FULL_NAME, "Security Vault User")
    }

    fun getCurrentUsername(): String? {
        return prefs.getString(KEY_USER_USERNAME, null) ?: getCurrentUserEmail()?.substringBefore('@')
    }

    fun clearSession() {
        prefs.edit().apply {
            remove(KEY_USER_ID)
            remove(KEY_USER_EMAIL)
            remove(KEY_USER_FULL_NAME)
            remove(KEY_USER_USERNAME)
            putBoolean(KEY_IS_LOGGED_IN, false)
            remove(KEY_LOGIN_TIME)
            apply()
        }
    }

    fun clearSessionImmediately() {
        prefs.edit().apply {
            remove(KEY_USER_ID)
            remove(KEY_USER_EMAIL)
            remove(KEY_USER_FULL_NAME)
            remove(KEY_USER_USERNAME)
            putBoolean(KEY_IS_LOGGED_IN, false)
            remove(KEY_LOGIN_TIME)
            commit()
        }
    }

    // Login Attempt Throttling
    fun recordFailedAttempt(): Int {
        val attempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        prefs.edit().apply {
            putInt(KEY_FAILED_ATTEMPTS, attempts)
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                putLong(KEY_LOCKOUT_TIMESTAMP, System.currentTimeMillis())
            }
            apply()
        }
        return attempts
    }

    fun isThrottled(): Boolean {
        val lockoutTime = prefs.getLong(KEY_LOCKOUT_TIMESTAMP, 0L)
        if (lockoutTime == 0L) return false

        val elapsed = System.currentTimeMillis() - lockoutTime
        if (elapsed < LOCKOUT_DURATION_MS) {
            return true
        } else {
            resetFailedAttempts()
            return false
        }
    }

    fun getRemainingLockoutSeconds(): Int {
        val lockoutTime = prefs.getLong(KEY_LOCKOUT_TIMESTAMP, 0L)
        if (lockoutTime == 0L) return 0
        val elapsed = System.currentTimeMillis() - lockoutTime
        val remaining = (LOCKOUT_DURATION_MS - elapsed) / 1000
        return if (remaining > 0) remaining.toInt() else 0
    }

    fun resetFailedAttempts() {
        prefs.edit().apply {
            remove(KEY_FAILED_ATTEMPTS)
            remove(KEY_LOCKOUT_TIMESTAMP)
            apply()
        }
    }

    companion object {
        private const val PREFS_SESSION = "secure_notes_session_prefs"
        private const val KEY_USER_ID = "key_user_id"
        private const val KEY_USER_EMAIL = "key_user_email"
        private const val KEY_USER_FULL_NAME = "key_user_full_name"
        private const val KEY_USER_USERNAME = "key_user_username"
        private const val KEY_IS_LOGGED_IN = "key_is_logged_in"
        private const val KEY_LOGIN_TIME = "key_login_time"

        private const val KEY_FAILED_ATTEMPTS = "key_failed_attempts"
        private const val KEY_LOCKOUT_TIMESTAMP = "key_lockout_timestamp"

        private const val MAX_FAILED_ATTEMPTS = 5
        private const val LOCKOUT_DURATION_MS = 30_000L // 30 seconds
    }
}
