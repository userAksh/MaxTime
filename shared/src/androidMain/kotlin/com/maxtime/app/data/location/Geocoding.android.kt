package com.maxtime.app.data.location

import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

actual suspend fun reverseGeocode(latitude: Double, longitude: Double): String? {
    val geocoder = Geocoder(AppContextHolder.context)
    val address = try {
        if (Build.VERSION.SDK_INT >= 33) {
            // The listener-based overload never throws for a failed lookup — it calls back with
            // an empty list instead (Geocoder.GeocodeListener's default onError does this), so
            // "no result" and "lookup failed" both just fall through to firstOrNull() == null.
            suspendCancellableCoroutine<Address?> { cont ->
                geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                    cont.resume(addresses.firstOrNull())
                }
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
            }
        }
    } catch (e: Exception) {
        // No network, no geocoding backend on this device (common on non-Google-Play builds),
        // IllegalArgumentException for out-of-range coordinates — all degrade to "no label"
        // rather than crashing; callers already treat null as an expected, handled outcome.
        null
    } ?: return null

    return formatAddress(address)
}

/** Prefers the neighbourhood-level name over the raw formatted address line, matching this
 *  app's existing "Hauz Khas, South Delhi" style (subLocality + subAdminArea, one falling back
 *  to the next coarser field if absent). */
private fun formatAddress(address: Address): String? {
    val neighborhood = address.subLocality ?: address.locality
    val region = address.subAdminArea ?: address.adminArea
    val parts = listOfNotNull(neighborhood, region).distinct()
    return if (parts.isNotEmpty()) parts.joinToString(", ") else address.getAddressLine(0)
}
