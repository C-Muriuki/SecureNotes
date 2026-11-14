package com.example.securenotes.activities

import android.content.Intent
import android.os.Bundle
import com.example.securenotes.databinding.ActivityOnboardingBinding
import com.example.securenotes.utils.Constants

class OnboardingActivity : BaseActivity() {
    private lateinit var binding: ActivityOnboardingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupEdgeToEdgeInsets(binding.mainOnboarding)

        binding.btnSkip.setOnClickListener { completeOnboarding() }
        binding.btnGetStarted.setOnClickListener { completeOnboarding() }
    }

    private fun completeOnboarding() {
        getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE).edit()
            .putBoolean(Constants.KEY_ONBOARDING_COMPLETED, true)
            .apply()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
