package com.example.securenotes.activities

import android.content.Intent
import android.os.Bundle
import android.text.TextWatcher
import android.widget.Toast
import com.example.securenotes.R
import com.example.securenotes.database.DatabaseHelper
import com.example.securenotes.databinding.ActivityRegisterBinding
import com.example.securenotes.models.User
import com.example.securenotes.repositories.UserRepository
import com.example.securenotes.security.PasswordHasher
import com.example.securenotes.utils.SessionManager
import com.example.securenotes.utils.ValidationUtils

class RegisterActivity : BaseActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var userRepository: UserRepository
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupEdgeToEdgeInsets(binding.mainRegister)

        userRepository = UserRepository(DatabaseHelper(applicationContext))
        sessionManager = SessionManager(applicationContext)

        binding.etRegPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.passwordStrengthMeter.setPassword(s)
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })

        binding.btnRegisterSubmit.setOnClickListener {
            handleRegistration()
        }

        binding.btnLoginLink.setOnClickListener {
            finish()
        }
    }

    private sealed interface RegistrationResult {
        data class Created(val userId: Long) : RegistrationResult
        data object Duplicate : RegistrationResult
        data object Failed : RegistrationResult
    }

    private fun handleRegistration() {
        val fullName = binding.etRegFullName.text?.toString()?.trim()
        val email = binding.etRegEmail.text?.toString()?.trim()
        val password = binding.etRegPassword.text?.toString()
        val confirmPassword = binding.etConfirmPassword.text?.toString()

        // Clear error states
        binding.tilRegFullName.error = null
        binding.tilRegEmail.error = null
        binding.tilRegPassword.error = null
        binding.tilConfirmPassword.error = null

        val validation = ValidationUtils.validateRegistration(fullName, email, password, confirmPassword)
        if (!validation.isValid) {
            val errorMsg = validation.errorMessage ?: getString(R.string.error_generic)
            Toast.makeText(this@RegisterActivity, errorMsg, Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnRegisterSubmit.isEnabled = false
        binding.btnRegisterSubmit.setText(R.string.state_creating_account)
        runIo({
            if (userRepository.isEmailRegistered(email!!)) {
                RegistrationResult.Duplicate
            } else {
                val passwordChars = password!!.toCharArray()
                try {
                    val saltHex = PasswordHasher.generateSalt()
                    val hashHex = PasswordHasher.hashPassword(passwordChars, saltHex)
                    val user = User(
                        fullName = fullName!!,
                        email = email,
                        passwordHash = hashHex,
                        passwordSalt = saltHex
                    )
                    val insertedId = userRepository.registerUser(user)
                    if (insertedId > 0) RegistrationResult.Created(insertedId) else RegistrationResult.Failed
                } finally {
                    passwordChars.fill(Char.MIN_VALUE)
                }
            }
        }) { outcome ->
            binding.btnRegisterSubmit.isEnabled = true
            binding.btnRegisterSubmit.setText(R.string.action_register)
            outcome.onSuccess { result ->
                when (result) {
                    RegistrationResult.Duplicate -> binding.tilRegEmail.error = getString(R.string.error_email_exists)
                    is RegistrationResult.Created -> {
                        Toast.makeText(this@RegisterActivity, getString(R.string.success_registration), Toast.LENGTH_LONG).show()
                        sessionManager.saveUserSession(result.userId, email!!, fullName!!)
                        val intent = Intent(this@RegisterActivity, DashboardActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        startActivity(intent)
                        finish()
                    }
                    RegistrationResult.Failed ->
                        Toast.makeText(this@RegisterActivity, getString(R.string.error_generic), Toast.LENGTH_SHORT).show()
                }
            }.onFailure {
                Toast.makeText(this@RegisterActivity, getString(R.string.error_generic), Toast.LENGTH_SHORT).show()
            }
        }
    }
}
