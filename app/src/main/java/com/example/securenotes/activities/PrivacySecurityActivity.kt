package com.example.securenotes.activities

import android.os.Bundle
import com.example.securenotes.databinding.ActivityPrivacySecurityBinding

class PrivacySecurityActivity : BaseActivity() {

    private lateinit var binding: ActivityPrivacySecurityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPrivacySecurityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupEdgeToEdgeInsets(binding.mainPrivacySecurity)

        binding.btnPrivacyBack.setOnClickListener { finish() }
    }
}
