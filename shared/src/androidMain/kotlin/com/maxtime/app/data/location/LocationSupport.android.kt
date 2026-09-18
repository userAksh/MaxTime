package com.maxtime.app.data.location

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.maxtime.app.util.friendlyErrorMessage
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine

actual class LocationContext(
    val activity: Activity,
    val requestPermission: suspend () -> Boolean
)

@Composable
actual fun rememberLocationContext(): LocationContext {
    val activity = LocalContext.current as Activity
    var pendingRequest by remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        pendingRequest?.complete(granted)
        pendingRequest = null
    }
    return remember(activity, launcher) {
        LocationContext(
            activity = activity,
            requestPermission = {
                val deferred = CompletableDeferred<Boolean>()
                pendingRequest = deferred
                launcher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                )
                deferred.await()
            }
        )
    }
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun isLocationServiceEnabled(context: Context): Boolean {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
    return manager.isProviderEnabled(LocationManager.GPS_PROVIDER) || manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
}

actual suspend fun requestCurrentLocation(context: LocationContext): LocationResult {
    val activity = context.activity

    if (!hasLocationPermission(activity)) {
        val granted = context.requestPermission()
        if (!granted) return LocationResult.PermissionDenied
    }

    // Re-check after the prompt: a device policy or provider toggle can still block us even
    // with permission granted, and we want a distinct message for that vs. a generic failure.
    if (!isLocationServiceEnabled(activity)) return LocationResult.LocationDisabled

    return try {
        val fusedClient = LocationServices.getFusedLocationProviderClient(activity)
        val location = suspendCancellableCoroutine<android.location.Location?> { cont ->
            val cancellationSource = CancellationTokenSource()
            cont.invokeOnCancellation { cancellationSource.cancel() }
            try {
                fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellationSource.token)
                    .addOnSuccessListener { loc -> cont.resume(loc) }
                    .addOnFailureListener { e -> cont.resumeWithException(e) }
            } catch (e: SecurityException) {
                // Permission can be revoked in the gap between our check above and this call
                // (e.g. from system settings) — surface it as PermissionDenied, not a crash.
                cont.resumeWithException(e)
            }
        }
        if (location == null) {
            LocationResult.Error("Couldn't determine your location — try again in a moment.")
        } else {
            LocationResult.Success(location.latitude, location.longitude)
        }
    } catch (e: SecurityException) {
        LocationResult.PermissionDenied
    } catch (e: Exception) {
        LocationResult.Error(friendlyErrorMessage(e, "Location request failed."))
    }
}
