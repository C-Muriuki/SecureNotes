package com.example.securenotes.activities

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.example.securenotes.database.DatabaseContract
import com.example.securenotes.databinding.ActivitySplashBinding
import com.example.securenotes.utils.Constants
import com.example.securenotes.utils.SessionManager

class SplashActivity : BaseActivity() {
    private lateinit var binding: ActivitySplashBinding
    private val splashHandler = Handler(Looper.getMainLooper())
    private val routeAfterSplash = Runnable { routeToFirstScreen() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupEdgeToEdgeInsets(binding.mainSplash)
        splashHandler.postDelayed(routeAfterSplash, SPLASH_DELAY_MS)
    }

    override fun onDestroy() {
        splashHandler.removeCallbacks(routeAfterSplash)
        super.onDestroy()
    }

    private fun routeToFirstScreen() {
        val session = SessionManager(applicationContext)
        val preferences = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        if (!preferences.contains(Constants.KEY_ONBOARDING_COMPLETED)) {
            // Existing installations already have a local database and should go to login;
            // a clean install sees onboarding once.
            val existingInstallation = getDatabasePath(DatabaseContract.DATABASE_NAME).exists() || session.isLoggedIn()
            preferences.edit().putBoolean(Constants.KEY_ONBOARDING_COMPLETED, existingInstallation).apply()
        }

        val destination = when {
            session.isLoggedIn() -> DashboardActivity::class.java
            preferences.getBoolean(Constants.KEY_ONBOARDING_COMPLETED, false) -> LoginActivity::class.java
            else -> OnboardingActivity::class.java
        }
        startActivity(Intent(this, destination))
        finish()
    }

    companion object {
        private const val SPLASH_DELAY_MS = 1_500L
    }
}
