package com.example.securenotes.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.example.securenotes.R
import com.example.securenotes.databinding.ActivitySettingsBinding
import com.example.securenotes.services.NoteBackupService
import com.example.securenotes.utils.Constants
import com.example.securenotes.utils.SessionManager
import com.example.securenotes.utils.ThemeUtils

class SettingsActivity : BaseActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var sessionManager: SessionManager

    private val notificationPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            startBackup()
            if (!granted) {
                Toast.makeText(this, R.string.backup_permission_denied, Toast.LENGTH_LONG).show()
                // If permission is permanently denied, Settings guidance appears on the next backup request.
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupEdgeToEdgeInsets(binding.mainSettings)
        sessionManager = SessionManager(applicationContext)
        setupThemeSelector()
        setupPrivacyLink()
        setupBackupAction()
        setupNavigation()
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) showUnseenBackupResult()
    }

    private fun setupThemeSelector() {
        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        when (prefs.getInt(Constants.KEY_THEME_MODE, Constants.THEME_MODE_SYSTEM)) {
            Constants.THEME_MODE_LIGHT -> binding.rbThemeLight.isChecked = true
            Constants.THEME_MODE_DARK -> binding.rbThemeDark.isChecked = true
            else -> binding.rbThemeSystem.isChecked = true
        }
        binding.rgThemeMode.setOnCheckedChangeListener { _, checkedId ->
            val selectedMode = when (checkedId) {
                R.id.rbThemeLight -> Constants.THEME_MODE_LIGHT
                R.id.rbThemeDark -> Constants.THEME_MODE_DARK
                else -> Constants.THEME_MODE_SYSTEM
            }
            prefs.edit().putInt(Constants.KEY_THEME_MODE, selectedMode).apply()
            ThemeUtils.setThemeMode(selectedMode)
        }
    }

    private fun setupPrivacyLink() {
        binding.cardPrivacyInfo.setOnClickListener {
            startActivity(Intent(this, PrivacySecurityActivity::class.java))
        }
    }

    private fun setupBackupAction() {
        binding.cardCreateBackup.setOnClickListener { requestBackupPermissionIfNeeded() }
    }

    private fun requestBackupPermissionIfNeeded() {
        if (!sessionManager.isLoggedIn() || sessionManager.getCurrentUserId() <= 0) {
            Toast.makeText(this, R.string.backup_sign_in_required, Toast.LENGTH_SHORT).show()
            return
        }
        if (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            startBackup()
            return
        }

        val permissionPrefs = getSharedPreferences(PREFS_PERMISSION, MODE_PRIVATE)
        val requestCount = permissionPrefs.getInt(KEY_NOTIFICATION_REQUEST_COUNT, 0)
        if (requestCount >= MAX_NOTIFICATION_PERMISSION_REQUESTS &&
            !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            showNotificationSettingsDialog()
            return
        }

        // Explain why completion notifications are useful before showing the Android prompt.
        AlertDialog.Builder(this)
            .setTitle(R.string.backup_permission_title)
            .setMessage(R.string.backup_permission_rationale)
            .setPositiveButton(R.string.backup_action_continue) { _, _ ->
                permissionPrefs.edit().putInt(KEY_NOTIFICATION_REQUEST_COUNT, requestCount + 1).apply()
                notificationPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            .setNegativeButton(R.string.backup_action_without_notifications) { _, _ ->
                startBackup()
                Toast.makeText(this, R.string.backup_permission_denied, Toast.LENGTH_LONG).show()
            }
            .show()
    }

    private fun showNotificationSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.backup_permission_title)
            .setMessage(R.string.backup_permission_settings_message)
            .setPositiveButton(R.string.backup_action_open_settings) { _, _ ->
                val appInfo = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(android.net.Uri.fromParts("package", packageName, null))
                startActivity(appInfo)
            }
            .setNegativeButton(R.string.backup_action_without_notifications) { _, _ ->
                startBackup()
                Toast.makeText(this, R.string.backup_permission_denied, Toast.LENGTH_LONG).show()
            }
            .show()
    }

    private fun startBackup() {
        val userId = sessionManager.getCurrentUserId()
        val intent = Intent(this, NoteBackupService::class.java).apply {
            action = NoteBackupService.ACTION_CREATE_BACKUP
            putExtra(NoteBackupService.EXTRA_USER_ID, userId)
        }
        getSharedPreferences(NoteBackupService.PREFS_BACKUP, MODE_PRIVATE).edit()
            .putLong(
                NoteBackupService.KEY_LAST_SEEN_EVENT,
                getSharedPreferences(NoteBackupService.PREFS_BACKUP, MODE_PRIVATE)
                    .getLong(NoteBackupService.KEY_EVENT_ID, 0L)
            ).apply()
        try {
            ContextCompat.startForegroundService(this, intent)
            Toast.makeText(this, R.string.backup_starting, Toast.LENGTH_SHORT).show()
        } catch (_: RuntimeException) {
            Toast.makeText(this, R.string.backup_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun showUnseenBackupResult() {
        val prefs = getSharedPreferences(NoteBackupService.PREFS_BACKUP, MODE_PRIVATE)
        val eventId = prefs.getLong(NoteBackupService.KEY_EVENT_ID, 0L)
        val lastSeen = prefs.getLong(NoteBackupService.KEY_LAST_SEEN_EVENT, 0L)
        if (eventId == 0L || eventId <= lastSeen) return
        prefs.edit().putLong(NoteBackupService.KEY_LAST_SEEN_EVENT, eventId).apply()
        val message = if (prefs.getBoolean(NoteBackupService.KEY_SUCCESS, false)) R.string.backup_completed else R.string.backup_failed
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun setupNavigation() {
        binding.bottomNavSettings.selectedItemId = R.id.nav_settings
        binding.bottomNavSettings.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { navigateToTab(DashboardActivity::class.java); true }
                R.id.nav_notes -> { navigateToTab(NotesListActivity::class.java); true }
                R.id.nav_search -> { navigateToTab(SearchActivity::class.java); true }
                else -> true
            }
        }
    }

    companion object {
        private const val PREFS_PERMISSION = "backup_permission_preferences"
        private const val KEY_NOTIFICATION_REQUEST_COUNT = "notification_request_count"
        private const val MAX_NOTIFICATION_PERMISSION_REQUESTS = 2
    }
}
