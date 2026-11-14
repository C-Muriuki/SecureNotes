package com.example.securenotes.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.securenotes.utils.SessionManager

/**
 * Manifest receiver for the protected, package-scoped MY_PACKAGE_REPLACED system event.
 * It performs only a quick local preference update and requires re-authentication after updates.
 */
class AppUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        SessionManager(context.applicationContext).clearSessionImmediately()
    }
}
