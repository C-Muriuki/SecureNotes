package com.example.securenotes.services

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.example.securenotes.R
import com.example.securenotes.activities.SettingsActivity
import com.example.securenotes.database.DatabaseHelper
import com.example.securenotes.utils.SessionManager
import java.io.File
import java.util.concurrent.Executors

/**
 * User-started foreground Service. Local encrypted-file creation is bounded and runs on a worker thread.
 */
class NoteBackupService : Service() {
    private val executor = Executors.newSingleThreadExecutor()
    private val writer = NoteBackupFileWriter()
    private val sessionManager by lazy { SessionManager(applicationContext) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForegroundCompat(buildNotification("Preparing encrypted backup", ongoing = true))

        if (intent?.action != ACTION_CREATE_BACKUP) {
            recordResult(success = false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        val requestedUserId = intent.getLongExtra(EXTRA_USER_ID, -1L)
        if (!sessionManager.isLoggedIn() || requestedUserId <= 0L ||
            requestedUserId != sessionManager.getCurrentUserId()
        ) {
            recordResult(success = false)
            showCompletionNotification(success = false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        executor.execute {
            var backupFile: File? = null
            val success = try {
                val helper = DatabaseHelper(applicationContext)
                try {
                    backupFile = writer.writeBackup(applicationContext, helper, requestedUserId)
                } finally {
                    helper.close()
                }
                true
            } catch (_: Exception) {
                false
            }
            recordResult(success, backupFile?.name)
            showCompletionNotification(success)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelfResult(startId)
        }
        return START_NOT_STICKY
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(message: String, ongoing: Boolean): Notification {
        val openSettings = PendingIntent.getActivity(
            this,
            0,
            Intent(this, SettingsActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(getString(R.string.backup_notification_title))
            .setContentText(message)
            .setContentIntent(openSettings)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .build()
    }

    private fun showCompletionNotification(success: Boolean) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.areNotificationsEnabled()) {
            manager.notify(
                NOTIFICATION_ID,
                buildNotification(
                    if (success) getString(R.string.backup_notification_success) else getString(R.string.backup_notification_failure),
                    ongoing = false
                )
            )
        }
    }

    private fun recordResult(success: Boolean, filename: String? = null) {
        getSharedPreferences(PREFS_BACKUP, Context.MODE_PRIVATE).edit()
            .putLong(KEY_EVENT_ID, System.currentTimeMillis())
            .putBoolean(KEY_SUCCESS, success)
            .putString(KEY_FILENAME, filename)
            .apply()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.backup_notification_channel), NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        executor.shutdownNow()
        recordResult(success = false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        const val ACTION_CREATE_BACKUP = "com.example.securenotes.action.CREATE_NOTE_BACKUP"
        const val EXTRA_USER_ID = "extra_authenticated_user_id"
        const val PREFS_BACKUP = "secure_notes_backup_status"
        const val KEY_EVENT_ID = "last_backup_event"
        const val KEY_SUCCESS = "last_backup_success"
        const val KEY_FILENAME = "last_backup_filename"
        const val KEY_LAST_SEEN_EVENT = "last_seen_backup_event"
        private const val CHANNEL_ID = "note_backups"
        private const val NOTIFICATION_ID = 4701
    }
}
