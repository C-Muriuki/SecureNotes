package com.example.securenotes.activities

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telephony.TelephonyManager
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.securenotes.R
import com.example.securenotes.database.DatabaseHelper
import com.example.securenotes.databinding.ActivityAccountBinding
import com.example.securenotes.repositories.UserRepository
import com.example.securenotes.security.PasswordHasher
import com.example.securenotes.utils.SessionManager
import com.example.securenotes.utils.ValidationUtils

class AccountActivity : BaseActivity() {
    private lateinit var binding: ActivityAccountBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var userRepository: UserRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAccountBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupEdgeToEdgeInsets(binding.mainAccount)
        sessionManager = SessionManager(applicationContext)
        userRepository = UserRepository(DatabaseHelper(applicationContext))

        refreshUserInfo()

        binding.btnAccountBack.setOnClickListener { finish() }
        binding.btnEditProfile.setOnClickListener { showEditProfileDialog() }
        binding.cardSettingsShortcut.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            if (ValueAnimator.areAnimatorsEnabled()) {
                overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
            }
        }
        binding.btnLogout.setOnClickListener {
            sessionManager.clearSession()
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        }

        binding.bottomNavAccount.selectedItemId = R.id.nav_account
        binding.bottomNavAccount.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_notes -> { navigateToTab(DashboardActivity::class.java); true }
                R.id.nav_account -> true
                else -> true
            }
        }

        refreshOperatorName()
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) {
            refreshUserInfo()
            refreshOperatorName()
        }
    }

    private fun refreshUserInfo() {
        val name = sessionManager.getCurrentUserFullName() ?: "Security Vault User"
        val username = sessionManager.getCurrentUsername()
        binding.tvAccountUsername.text = if (!username.isNullOrBlank()) "@$username ($name)" else name
        binding.tvAccountUserId.text = sessionManager.getCurrentUserEmail() ?: "Account"
    }

    private fun showEditProfileDialog() {
        val context = this
        val currentUserId = sessionManager.getCurrentUserId()
        if (currentUserId <= 0) return

        val dialogBinding = com.example.securenotes.databinding.DialogEditProfileBinding.inflate(layoutInflater)
        dialogBinding.etEditFullName.setText(sessionManager.getCurrentUserFullName().orEmpty())
        dialogBinding.etEditUsername.setText(sessionManager.getCurrentUsername().orEmpty())
        dialogBinding.etEditEmail.setText(sessionManager.getCurrentUserEmail().orEmpty())

        dialogBinding.cbChangePassword.setOnCheckedChangeListener { _, isChecked ->
            dialogBinding.layoutPasswordFields.visibility = if (isChecked) android.view.View.VISIBLE else android.view.View.GONE
        }

        dialogBinding.etNewPassword.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                dialogBinding.passwordStrengthMeter.setPassword(s)
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })

        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.edit_profile_dialog_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.action_save, null)
            .setNegativeButton(R.string.action_cancel, null)
            .create()

        dialog.setOnDismissListener {
            dialogBinding.etCurrentPassword.text?.clear()
            dialogBinding.etNewPassword.text?.clear()
            dialogBinding.etConfirmPassword.text?.clear()
        }

        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val newName = dialogBinding.etEditFullName.text.toString().trim()
            val newUsername = dialogBinding.etEditUsername.text.toString().trim()
            val newEmail = dialogBinding.etEditEmail.text.toString().trim()
            val changePassword = dialogBinding.cbChangePassword.isChecked

            if (newName.isBlank()) {
                dialogBinding.tilEditFullName.error = getString(R.string.error_field_required)
                return@setOnClickListener
            } else {
                dialogBinding.tilEditFullName.error = null
            }

            if (newUsername.isBlank()) {
                dialogBinding.tilEditUsername.error = getString(R.string.error_field_required)
                return@setOnClickListener
            } else {
                dialogBinding.tilEditUsername.error = null
            }

            if (!ValidationUtils.isValidEmail(newEmail)) {
                dialogBinding.tilEditEmail.error = getString(R.string.error_invalid_email)
                return@setOnClickListener
            } else {
                dialogBinding.tilEditEmail.error = null
            }

            if (changePassword) {
                val currentPassword = dialogBinding.etCurrentPassword.text.toString().trim()
                val newPassword = dialogBinding.etNewPassword.text.toString().trim()
                val confirmPassword = dialogBinding.etConfirmPassword.text.toString().trim()

                if (currentPassword.isBlank()) {
                    dialogBinding.tilCurrentPassword.error = getString(R.string.error_field_required)
                    return@setOnClickListener
                }

                runIo({
                    val user = userRepository.getUserById(currentUserId)
                    if (user == null || !PasswordHasher.verifyPassword(currentPassword.toCharArray(), user.passwordHash, user.passwordSalt)) {
                        null
                    } else {
                        user
                    }
                }) { userResult ->
                    userResult.onSuccess { user ->
                        if (user == null) {
                            dialogBinding.tilCurrentPassword.error = getString(R.string.error_incorrect_password)
                            return@onSuccess
                        } else {
                            dialogBinding.tilCurrentPassword.error = null
                        }

                        if (!ValidationUtils.isValidPassword(newPassword)) {
                            dialogBinding.tilNewPassword.error = getString(R.string.error_password_weak)
                            return@onSuccess
                        } else {
                            dialogBinding.tilNewPassword.error = null
                        }

                        if (newPassword != confirmPassword) {
                            dialogBinding.tilConfirmPassword.error = getString(R.string.error_passwords_do_not_match)
                            return@onSuccess
                        } else {
                            dialogBinding.tilConfirmPassword.error = null
                        }

                        val generatedSalt = PasswordHasher.generateSalt()
                        val generatedHash = PasswordHasher.hashPassword(newPassword.toCharArray(), generatedSalt)

                        executeSaveCredentials(currentUserId, newName, newUsername, newEmail, generatedHash, generatedSalt, dialog)
                    }
                }
            } else {
                executeSaveCredentials(currentUserId, newName, newUsername, newEmail, null, null, dialog)
            }
        }
    }

    private fun executeSaveCredentials(
        userId: Long,
        name: String,
        username: String,
        email: String,
        newHash: String?,
        newSalt: String?,
        dialog: AlertDialog
    ) {
        runIo({
            userRepository.updateUserCredentials(userId, name, username, email, newHash, newSalt)
        }) { result ->
            result.onSuccess { updated ->
                if (updated) {
                    sessionManager.saveUserSession(userId, email, name, username)
                    refreshUserInfo()
                    Toast.makeText(this, R.string.profile_updated_success, Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } else {
                    Toast.makeText(this, R.string.profile_update_failed, Toast.LENGTH_LONG).show()
                }
            }.onFailure {
                Toast.makeText(this, R.string.profile_update_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun refreshOperatorName() {
        val hasRadioFeature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS)
        } else {
            packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
        }
        if (!hasRadioFeature) {
            binding.tvMobileOperator.setText(R.string.account_operator_unavailable)
            return
        }
        val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        if (telephonyManager == null) {
            binding.tvMobileOperator.setText(R.string.account_operator_unavailable)
            return
        }
        val operatorName = try {
            telephonyManager.networkOperatorName?.trim().orEmpty()
        } catch (_: SecurityException) {
            ""
        } catch (_: UnsupportedOperationException) {
            ""
        }
        if (operatorName.isBlank()) binding.tvMobileOperator.setText(R.string.account_operator_not_registered)
        else binding.tvMobileOperator.text = operatorName
    }
}
