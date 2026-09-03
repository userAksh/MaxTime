package com.maxtime.app.ui.components

import androidx.compose.runtime.Composable

/** Platform-neutral coordinate — `com.google.android.gms.maps.model.LatLng` and
 *  `CLLocationCoordinate2D` are each platform-only types, so commonMain needs its own. */
data class LatLng(val latitude: Double, val longitude: Double)

/** Full-screen "drag the map, the pin stays centered" location picker — the same interaction
 *  pattern used by Uber/Google Maps' own location pickers, chosen over a draggable marker
 *  specifically because it has no drag-gesture edge cases to get wrong (marker vs. map
 *  gesture conflicts, drag-outside-viewport handling, etc.) — the map's own pan/zoom gestures
 *  are all there is, and the center is always exactly what's under the fixed pin.
 *  [initial] centers the camera there if provided (falls back to a sane default otherwise);
 *  [onConfirm] fires once, with whatever coordinate is centered at the moment of confirming. */
@Composable
expect fun MapPicker(
    initial: LatLng?,
    onConfirm: (LatLng) -> Unit,
    onCancel: () -> Unit
)

/** New Delhi — this app's other mocked location defaults (e.g. "Saket, South Delhi") already
 *  assume Delhi, so an empty/denied-GPS map picker opens somewhere consistent with the rest of
 *  the app's data instead of the middle of the ocean (0,0). */
internal val DEFAULT_MAP_CENTER = LatLng(28.6139, 77.2090)
