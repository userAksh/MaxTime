package com.maxtime.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxtime.app.data.AppState
import com.maxtime.app.data.UserLocation
import com.maxtime.app.data.persistConfirmedLocation
import com.maxtime.app.data.location.LocationResult
import com.maxtime.app.data.location.rememberLocationContext
import com.maxtime.app.data.location.requestCurrentLocation
import com.maxtime.app.data.location.reverseGeocode
import com.maxtime.app.ui.components.LatLng
import com.maxtime.app.ui.components.MapPicker
import com.maxtime.app.ui.components.PrimaryButton
import com.maxtime.app.ui.theme.MaxTimeColors
import com.maxtime.app.ui.theme.MaxTimeShapes
import kotlin.math.round
import kotlinx.coroutines.launch

/** No `String.format`/`"%.4f"` in commonMain — that's JVM-only, not part of kotlin-stdlib-common. */
private fun Double.roundTo4(): String {
    val rounded = round(this * 10000.0) / 10000.0
    return rounded.toString()
}

/**
 * Bottom sheet shown once per session the first time Home renders — governed by
 * `state.locationConfirmed`, not a first-launch-ever flag (Section 5).
 */
@Composable
fun LocationPopup(state: AppState) {
    var manualArea by remember { mutableStateOf(state.location.area) }
    var isLocating by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf<String?>(null) }
    var showMapPicker by remember { mutableStateOf(false) }
    var isOpeningMap by remember { mutableStateOf(false) }
    var mapPickerInitial by remember { mutableStateOf<LatLng?>(null) }
    val locationContext = rememberLocationContext()
    val scope = rememberCoroutineScope()

    fun openMapPicker() {
        val known = state.location.latitude?.let { lat -> state.location.longitude?.let { lng -> LatLng(lat, lng) } }
        if (known != null) {
            mapPickerInitial = known
            showMapPicker = true
            return
        }
        // No coordinates on hand yet (e.g. GPS was never used this session) — try a real fix so
        // the map opens centered on where the device actually is instead of always falling back
        // to MapPicker's hardcoded Delhi default regardless of the user's real location.
        isOpeningMap = true
        scope.launch {
            val result = requestCurrentLocation(locationContext)
            mapPickerInitial = (result as? LocationResult.Success)?.let { LatLng(it.latitude, it.longitude) }
            isOpeningMap = false
            showMapPicker = true
        }
    }

    fun useCoordinates(latitude: Double, longitude: Double) {
        isLocating = true
        scope.launch {
            val label = reverseGeocode(latitude, longitude) ?: "${latitude.roundTo4()}, ${longitude.roundTo4()}"
            val resolved = UserLocation(
                area = label,
                radiusKm = state.location.radiusKm,
                latitude = latitude,
                longitude = longitude
            )
            state.location = resolved
            persistConfirmedLocation(resolved)
            manualArea = label
            state.locationConfirmed = true
            isLocating = false
        }
    }

    if (showMapPicker) {
        MapPicker(
            initial = mapPickerInitial,
            onConfirm = { picked ->
                showMapPicker = false
                useCoordinates(picked.latitude, picked.longitude)
            },
            onCancel = { showMapPicker = false }
        )
        return
    }

    fun useCurrentLocation() {
        locationError = null
        isLocating = true
        scope.launch {
            when (val result = requestCurrentLocation(locationContext)) {
                is LocationResult.Success -> useCoordinates(result.latitude, result.longitude)
                LocationResult.PermissionDenied -> {
                    locationError = "Location permission denied — enter your area manually below."
                    isLocating = false
                }
                LocationResult.LocationDisabled -> {
                    locationError = "Turn on location services to use this, or enter your area manually below."
                    isLocating = false
                }
                is LocationResult.Error -> {
                    locationError = result.message
                    isLocating = false
                }
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xB2030402))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(MaxTimeColors.bg1)
                .border(1.dp, MaxTimeColors.cardBorder, RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .padding(horizontal = 22.dp, vertical = 26.dp)
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(MaxTimeColors.accentWash)
                    .border(2.dp, MaxTimeColors.accent, CircleShape),
                contentAlignment = Alignment.Center
            ) { Text("◆", color = MaxTimeColors.accent, fontSize = 22.sp) }

            Text(
                "Let's help you find people and slots",
                color = MaxTimeColors.textStrong,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)
            )
            Text(
                "Tell us where you are so we can show nearby activities, open slots, and people up for the same thing.",
                color = MaxTimeColors.muted2,
                fontSize = 12.5.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
            )

            PrimaryButton(
                if (isLocating) "Locating…" else "◆ Use current location",
                enabled = !isLocating,
                onClick = { useCurrentLocation() }
            )
            locationError?.let {
                Text(
                    it,
                    color = MaxTimeColors.red,
                    fontSize = 11.5.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }

            Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                Text("or enter manually", color = MaxTimeColors.muted, fontSize = 11.sp)
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(MaxTimeShapes.medium)
                    .background(MaxTimeColors.cardBg)
                    .border(1.dp, MaxTimeColors.cardBorder, MaxTimeShapes.medium)
                    .padding(horizontal = 14.dp, vertical = 13.dp)
            ) {
                BasicTextField(
                    value = manualArea,
                    onValueChange = { manualArea = it },
                    textStyle = TextStyle(color = MaxTimeColors.textStrong, fontSize = 13.5.sp),
                    cursorBrush = SolidColor(MaxTimeColors.accent),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Box(
                Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .clip(MaxTimeShapes.medium)
                    .background(MaxTimeColors.cardBg)
                    .border(1.dp, MaxTimeColors.cardBorder, MaxTimeShapes.medium)
                    .clickable(enabled = !isOpeningMap) { openMapPicker() }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (isOpeningMap) "Finding your location…" else "🗺 Select on map instead",
                    color = MaxTimeColors.muted2,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp
                )
            }

            Box(Modifier.padding(top = 14.dp)) {
                PrimaryButton(
                    "Confirm location",
                    onClick = {
                        if (manualArea.isNotBlank()) {
                            state.location = UserLocation(area = manualArea.trim(), radiusKm = state.location.radiusKm)
                        }
                        persistConfirmedLocation(state.location)
                        state.locationConfirmed = true
                    }
                )
            }

            Text(
                "Skip for now",
                color = MaxTimeColors.muted,
                fontSize = 11.5.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .clickable { state.locationConfirmed = true }
            )
        }
    }
}
