package com.maxtime.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.model.CameraPosition
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import com.maxtime.app.ui.theme.MaxTimeColors
import com.maxtime.app.ui.theme.MaxTimeSpacing

@Composable
actual fun MapPicker(initial: LatLng?, onConfirm: (LatLng) -> Unit, onCancel: () -> Unit) {
    val start = initial ?: DEFAULT_MAP_CENTER
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            com.google.android.gms.maps.model.LatLng(start.latitude, start.longitude),
            15f
        )
    }

    Box(Modifier.fillMaxSize().background(MaxTimeColors.bg1)) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false,
                mapToolbarEnabled = false
            )
        )

        // Fixed center pin — offset up by roughly half its own height so the pin's TIP (not its
        // visual center) marks the coordinate that gets confirmed, matching how a real map pin
        // points at a spot rather than floating over it.
        Text(
            "📍",
            fontSize = 34.sp,
            modifier = Modifier.align(Alignment.Center).offset(y = (-17).dp)
        )

        Box(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp)) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaxTimeColors.bg1.copy(alpha = 0.9f))
                    .border(1.dp, MaxTimeColors.cardBorder, CircleShape)
                    .clickable { onCancel() },
                contentAlignment = Alignment.Center
            ) { Text("✕", color = MaxTimeColors.textStrong, fontSize = 16.sp) }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MaxTimeColors.bg1)
                .navigationBarsPadding()
                .padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 16.dp)
        ) {
            PrimaryButton("Confirm this location") {
                val target = cameraPositionState.position.target
                onConfirm(LatLng(target.latitude, target.longitude))
            }
        }
    }
}
