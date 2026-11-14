package com.example.securenotes.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object ThemeUtils {

    fun applySavedTheme(context: Context) {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val themeMode = prefs.getInt(Constants.KEY_THEME_MODE, Constants.THEME_MODE_SYSTEM)
        setThemeMode(themeMode)
    }

    fun setThemeMode(mode: Int) {
        val nightMode = when (mode) {
            Constants.THEME_MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            Constants.THEME_MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }
}
