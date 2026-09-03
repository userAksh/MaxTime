package com.maxtime.app.data.location

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.coroutines.resume
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.Foundation.NSError
import platform.darwin.NSObject

actual class LocationContext

@Composable
actual fun rememberLocationContext(): LocationContext = remember { LocationContext() }

/** No separate permission step like Android's: requesting authorization and requesting a fix
 *  are both driven through the same `CLLocationManager` delegate callbacks here, so the whole
 *  round trip (prompt if needed, then the fix) is one suspend call, same shape as the Android
 *  side even though the underlying platform flow is different. */
@OptIn(ExperimentalForeignApi::class)
actual suspend fun requestCurrentLocation(context: LocationContext): LocationResult =
    suspendCancellableCoroutine { cont ->
        val manager = CLLocationManager()
        manager.desiredAccuracy = kCLLocationAccuracyBest
        val delegate = LocationDelegate(cont)
        manager.delegate = delegate
        // Keep the delegate (and the manager) alive for the continuation's lifetime — without a
        // strong reference held somewhere, ARC is free to deallocate it before a callback fires.
        cont.invokeOnCancellation {
            manager.delegate = null
            retainedDelegate = null
        }
        retainedDelegate = delegate to manager

        when (manager.authorizationStatus) {
            kCLAuthorizationStatusNotDetermined -> manager.requestWhenInUseAuthorization()
            kCLAuthorizationStatusDenied, kCLAuthorizationStatusRestricted -> cont.resume(LocationResult.PermissionDenied)
            else -> manager.requestLocation()
        }
    }

/** Holds the in-flight delegate+manager pair so ARC can't collect them mid-request; cleared
 *  the moment the continuation resolves or is cancelled. Only one location request is ever
 *  in flight from this popup at a time, so a single slot is enough. */
@OptIn(ExperimentalForeignApi::class)
private var retainedDelegate: Pair<NSObject, CLLocationManager>? = null

@OptIn(ExperimentalForeignApi::class)
private class LocationDelegate(
    private val cont: CancellableContinuation<LocationResult>
) : NSObject(), CLLocationManagerDelegateProtocol {

    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        if (!cont.isActive) return
        val location = didUpdateLocations.lastOrNull() as? CLLocation
        if (location == null) {
            cont.resume(LocationResult.Error("No location returned."))
        } else {
            val (lat, lng) = location.coordinate.useContents { latitude to longitude }
            cont.resume(LocationResult.Success(lat, lng))
        }
        retainedDelegate = null
    }

    override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
        if (!cont.isActive) return
        cont.resume(LocationResult.Error(didFailWithError.localizedDescription))
        retainedDelegate = null
    }

    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
        if (!cont.isActive) return
        when (manager.authorizationStatus) {
            kCLAuthorizationStatusAuthorizedWhenInUse, kCLAuthorizationStatusAuthorizedAlways -> manager.requestLocation()
            kCLAuthorizationStatusDenied, kCLAuthorizationStatusRestricted -> {
                cont.resume(LocationResult.PermissionDenied)
                retainedDelegate = null
            }
            else -> Unit
        }
    }
}
