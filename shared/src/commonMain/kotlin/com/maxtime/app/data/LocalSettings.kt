package com.maxtime.app.data

/** Minimal on-device key-value store — SharedPreferences on Android, NSUserDefaults on iOS.
 *  Not a Composable (unlike [com.maxtime.app.data.location.rememberLocationContext]): this needs
 *  to be readable from plain code like [AppState]'s constructor, before any composition exists,
 *  so a returning user's already-confirmed location is available on the very first frame instead
 *  of flashing the default then jumping. */
expect object LocalSettings {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun getDouble(key: String): Double?
    fun putDouble(key: String, value: Double)
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
}

internal object LocationPrefsKeys {
    const val CONFIRMED = "location_confirmed"
    const val AREA = "location_area"
    const val RADIUS_KM = "location_radius_km"
    const val LATITUDE = "location_latitude"
    const val LONGITUDE = "location_longitude"
}

/** Reads back a location only if it was previously persisted via [persistConfirmedLocation] —
 *  never returns a "skipped" state, since skipping intentionally leaves nothing real set. */
fun loadPersistedLocation(): UserLocation? {
    if (!LocalSettings.getBoolean(LocationPrefsKeys.CONFIRMED, false)) return null
    val area = LocalSettings.getString(LocationPrefsKeys.AREA) ?: return null
    val radiusKm = LocalSettings.getDouble(LocationPrefsKeys.RADIUS_KM)?.toInt() ?: 5
    return UserLocation(
        area = area,
        radiusKm = radiusKm,
        latitude = LocalSettings.getDouble(LocationPrefsKeys.LATITUDE),
        longitude = LocalSettings.getDouble(LocationPrefsKeys.LONGITUDE)
    )
}

/** Called only when a location was genuinely set (GPS fix, map pick, or manual entry submitted
 *  via "Confirm location") — deliberately NOT called for "Skip for now", so a skip doesn't
 *  silently turn into "never ask again" with nothing real behind it. */
fun persistConfirmedLocation(location: UserLocation) {
    LocalSettings.putBoolean(LocationPrefsKeys.CONFIRMED, true)
    LocalSettings.putString(LocationPrefsKeys.AREA, location.area)
    LocalSettings.putDouble(LocationPrefsKeys.RADIUS_KM, location.radiusKm.toDouble())
    location.latitude?.let { LocalSettings.putDouble(LocationPrefsKeys.LATITUDE, it) }
    location.longitude?.let { LocalSettings.putDouble(LocationPrefsKeys.LONGITUDE, it) }
}
