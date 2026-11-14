package com.example.securenotes.activities

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telephony.TelephonyManager
import com.example.securenotes.R
import com.example.securenotes.databinding.ActivityAccountBinding
import com.example.securenotes.utils.SessionManager

class AccountActivity : BaseActivity() {
    private lateinit var binding: ActivityAccountBinding
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAccountBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupEdgeToEdgeInsets(binding.mainAccount)
        sessionManager = SessionManager(applicationContext)
        binding.tvAccountUsername.text = sessionManager.getCurrentUserFullName() ?: "Security Vault User"
        binding.tvAccountUserId.text = sessionManager.getCurrentUserEmail() ?: "Account"
        binding.btnAccountBack.setOnClickListener { finish() }
        binding.btnLogout.setOnClickListener {
            sessionManager.clearSession()
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        }
        refreshOperatorName()
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) refreshOperatorName()
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
            // getNetworkOperatorName() needs radio-access hardware, but no user phone-state permission.
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
