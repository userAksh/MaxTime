package com.maxtime.app.data

import android.content.Context
import com.maxtime.app.data.location.AppContextHolder

actual object LocalSettings {
    private val prefs by lazy {
        AppContextHolder.context.getSharedPreferences("maxtime_prefs", Context.MODE_PRIVATE)
    }

    actual fun getString(key: String): String? = prefs.getString(key, null)
    actual fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    // SharedPreferences has no putDouble/getDouble — stored as the raw bit pattern in a Long.
    actual fun getDouble(key: String): Double? =
        if (prefs.contains(key)) Double.fromBits(prefs.getLong(key, 0L)) else null

    actual fun putDouble(key: String, value: Double) {
        prefs.edit().putLong(key, value.toRawBits()).apply()
    }

    actual fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    actual fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }
}
