package com.example.securenotes.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.example.securenotes.R
import com.example.securenotes.database.DatabaseHelper
import com.example.securenotes.databinding.ActivityLoginBinding
import com.example.securenotes.repositories.UserRepository
import com.example.securenotes.security.PasswordHasher
import com.example.securenotes.utils.SessionManager
import com.example.securenotes.utils.ValidationUtils

class LoginActivity : BaseActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var userRepository: UserRepository
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sessionManager = SessionManager(applicationContext)

        // Session check: if active session exists, navigate directly to main dashboard
        if (sessionManager.isLoggedIn()) {
            startActivity(Intent(this@LoginActivity, DashboardActivity::class.java))
            finish()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupEdgeToEdgeInsets(binding.mainLogin)

        userRepository = UserRepository(DatabaseHelper(applicationContext))

        binding.btnLogin.setOnClickListener {
            handleLogin()
        }

        binding.btnRegisterLink.setOnClickListener {
            startActivity(Intent(this@LoginActivity, RegisterActivity::class.java))
        }
    }

    private fun handleLogin() {
        val email = binding.etEmail.text?.toString()?.trim()
        val password = binding.etPassword.text?.toString()

        binding.tilEmail.error = null
        binding.tilPassword.error = null

        // Login Throttling Check
        if (sessionManager.isThrottled()) {
            val remainingSecs = sessionManager.getRemainingLockoutSeconds()
            val msg = getString(R.string.error_login_throttled, remainingSecs)
            Toast.makeText(this@LoginActivity, msg, Toast.LENGTH_LONG).show()
            return
        }

        val validation = ValidationUtils.validateLogin(email, password)
        if (!validation.isValid) {
            Toast.makeText(this@LoginActivity, validation.errorMessage ?: getString(R.string.error_generic), Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnLogin.isEnabled = false
        binding.btnLogin.setText(R.string.state_signing_in)
        runIo({
            val user = userRepository.getUserByEmail(email!!)
            if (user == null) {
                // Preserve the generic failure timing without revealing whether the email exists.
                val dummyPassword = password!!.toCharArray()
                try {
                    PasswordHasher.hashPassword(dummyPassword, "0".repeat(32))
                } finally {
                    dummyPassword.fill(Char.MIN_VALUE)
                }
                null
            } else {
                val passwordChars = password!!.toCharArray()
                try {
                    user.takeIf {
                        PasswordHasher.verifyPassword(passwordChars, user.passwordHash, user.passwordSalt)
                    }
                } finally {
                    passwordChars.fill(Char.MIN_VALUE)
                }
            }
        }) { outcome ->
            binding.btnLogin.isEnabled = true
            binding.btnLogin.setText(R.string.action_login)
            outcome.onSuccess { user ->
                if (user == null) {
                    showGenericLoginError(sessionManager.recordFailedAttempt())
                } else {
                    sessionManager.saveUserSession(user.userId, user.email, user.fullName)
                    Toast.makeText(this@LoginActivity, "Welcome back, ${user.fullName}!", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this@LoginActivity, DashboardActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                    finish()
                }
            }.onFailure {
                Toast.makeText(this@LoginActivity, getString(R.string.error_generic), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showGenericLoginError(failedAttempts: Int) {
        if (failedAttempts >= 5) {
            val remainingSecs = sessionManager.getRemainingLockoutSeconds()
            Toast.makeText(this@LoginActivity, getString(R.string.error_login_throttled, remainingSecs), Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this@LoginActivity, getString(R.string.error_invalid_credentials), Toast.LENGTH_SHORT).show()
        }
    }
}
