package com.maxtime.app.data.location

import kotlin.coroutines.resume
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreLocation.CLGeocoder
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLPlacemark

@OptIn(ExperimentalForeignApi::class)
actual suspend fun reverseGeocode(latitude: Double, longitude: Double): String? =
    suspendCancellableCoroutine { cont ->
        val geocoder = CLGeocoder()
        val location = CLLocation(latitude = latitude, longitude = longitude)
        cont.invokeOnCancellation { geocoder.cancelGeocode() }
        geocoder.reverseGeocodeLocation(location) { placemarks, _ ->
            // A failed lookup (no network, rate limit, invalid coordinate) comes back as a
            // non-nil error with a nil/empty placemark list — either way `firstOrNull()` is
            // null and we fall through to the same "no label" result callers already expect.
            if (!cont.isActive) return@reverseGeocodeLocation
            val placemark = placemarks?.firstOrNull() as? CLPlacemark
            cont.resume(placemark?.let(::formatPlacemark))
        }
    }

@OptIn(ExperimentalForeignApi::class)
private fun formatPlacemark(placemark: CLPlacemark): String? {
    val neighborhood = placemark.subLocality ?: placemark.locality
    val region = placemark.subAdministrativeArea ?: placemark.administrativeArea
    val parts = listOfNotNull(neighborhood, region).distinct()
    return parts.takeIf { it.isNotEmpty() }?.joinToString(", ")
}
