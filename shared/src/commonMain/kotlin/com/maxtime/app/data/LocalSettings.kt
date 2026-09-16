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

internal object AppPrefsKeys {
    /** Lives in the same on-device storage a full uninstall actually clears (NSUserDefaults /
     *  SharedPreferences) — unlike iOS Keychain, which Firebase Auth's session survives in even
     *  after the app is deleted. Its absence is a reliable "this install has never launched
     *  before" signal, used to catch a stale Keychain session left over from before the delete. */
    const val HAS_LAUNCHED_BEFORE = "has_launched_before"

    /** Set only once InterestsScreen's final "Finish — Go to Home" actually runs — signing in
     *  (currentUser != null) is NOT the same as having finished onboarding, since Auth is well
     *  before Profile/Interests in the flow. Without this, closing the app between Auth and
     *  Interests and reopening it sent people straight to Home on the strength of currentUser
     *  alone, past Profile/Interests they'd never actually filled in — landing on Home with
     *  whatever placeholder default AppState.currentUser started with, not a real name.
     *
     *  Keyed by uid, not a single flat flag: now that sign-out exists, a second, genuinely
     *  different account can sign in on the same device afterward, and a flat flag would
     *  incorrectly carry the FIRST account's completed status over to them. */
    fun onboardingCompleteKey(uid: String) = "onboarding_complete_$uid"
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
