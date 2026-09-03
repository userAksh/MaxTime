package com.maxtime.app.data

import platform.Foundation.NSUserDefaults

actual object LocalSettings {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun getString(key: String): String? = defaults.stringForKey(key)
    actual fun putString(key: String, value: String) {
        defaults.setObject(value, key)
    }

    actual fun getDouble(key: String): Double? =
        if (defaults.objectForKey(key) == null) null else defaults.doubleForKey(key)

    actual fun putDouble(key: String, value: Double) {
        defaults.setDouble(value, key)
    }

    actual fun getBoolean(key: String, default: Boolean): Boolean =
        if (defaults.objectForKey(key) == null) default else defaults.boolForKey(key)

    actual fun putBoolean(key: String, value: Boolean) {
        defaults.setBool(value, key)
    }
}
