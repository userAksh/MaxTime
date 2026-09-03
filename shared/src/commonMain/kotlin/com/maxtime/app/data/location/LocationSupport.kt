package com.maxtime.app.data.location

import androidx.compose.runtime.Composable

/** Result of a single "give me the device's current position" attempt — every way this can
 *  legitimately fail is a distinct case, not a generic null/exception, so the UI can show the
 *  right message (and the right recovery action) for each one. */
sealed class LocationResult {
    data class Success(val latitude: Double, val longitude: Double) : LocationResult()

    /** The OS permission prompt was declined, or permission was already denied before this call. */
    object PermissionDenied : LocationResult()

    /** Permission is granted, but the device's location service itself (GPS/network) is off. */
    object LocationDisabled : LocationResult()

    data class Error(val message: String) : LocationResult()
}

/** Platform handle needed to request location — an Activity on Android (required to request
 *  runtime permissions and to scope the FusedLocationProviderClient); nothing on iOS, since
 *  CLLocationManager needs neither. Mirrors [com.maxtime.app.data.firebase.PhoneAuthContext]'s
 *  shape/spirit exactly. */
expect class LocationContext

@Composable
expect fun rememberLocationContext(): LocationContext

/** Requests permission (if not already granted) and a single fresh location fix. Suspends for
 *  the whole round trip — permission dialog, then the actual GPS/network fix — so callers just
 *  await one result instead of juggling callbacks. */
expect suspend fun requestCurrentLocation(context: LocationContext): LocationResult
