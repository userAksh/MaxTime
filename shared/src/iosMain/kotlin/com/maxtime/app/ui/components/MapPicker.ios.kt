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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.UIKitView
import com.maxtime.app.ui.theme.MaxTimeColors
import com.maxtime.app.ui.theme.MaxTimeSpacing
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.MapKit.MKCoordinateRegionMakeWithDistance
import platform.MapKit.MKMapView
import platform.MapKit.MKMapViewDelegateProtocol
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun MapPicker(initial: LatLng?, onConfirm: (LatLng) -> Unit, onCancel: () -> Unit) {
    val start = initial ?: DEFAULT_MAP_CENTER
    var currentCenter by remember { mutableStateOf(start) }

    // Held at the composable's scope (not inside the UIKitView factory) so it outlives a single
    // factory call — MKMapView.delegate is a weak reference on iOS, so without a strong Kotlin
    // reference somewhere ARC is free to deallocate this the moment the factory lambda returns.
    val delegate = remember {
        object : NSObject(), MKMapViewDelegateProtocol {
            override fun mapView(mapView: MKMapView, regionDidChangeAnimated: Boolean) {
                currentCenter = mapView.centerCoordinate.useContents { LatLng(latitude, longitude) }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(MaxTimeColors.bg1)) {
        UIKitView(
            factory = {
                val mapView = MKMapView()
                val region = MKCoordinateRegionMakeWithDistance(
                    CLLocationCoordinate2DMake(start.latitude, start.longitude),
                    3000.0,
                    3000.0
                )
                mapView.setRegion(region, animated = false)
                mapView.delegate = delegate
                mapView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Fixed center pin — offset up by roughly half its own height so the pin's TIP (not its
        // visual center) marks the coordinate that gets confirmed, same as the Android side.
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
            PrimaryButton("Confirm this location") { onConfirm(currentCenter) }
        }
    }
}
