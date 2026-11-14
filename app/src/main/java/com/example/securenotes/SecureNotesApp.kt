package com.example.securenotes

import android.app.Application
import com.example.securenotes.utils.ThemeUtils

class SecureNotesApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        ThemeUtils.applySavedTheme(this)
    }

    companion object {
        lateinit var instance: SecureNotesApp
            private set
    }
}
