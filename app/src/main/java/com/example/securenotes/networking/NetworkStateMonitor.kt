package com.example.securenotes.networking

import android.content.Context
import android.net.ConnectivityManager

class NetworkStateMonitor(private val context: Context) {
    fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        return cm?.activeNetwork != null
    }
}
