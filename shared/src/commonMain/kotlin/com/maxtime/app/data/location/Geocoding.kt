package com.maxtime.app.data.location

/** Turns a raw GPS fix into a short, human-readable area label (e.g. "Hauz Khas, South Delhi") —
 *  the same shape [UserLocation.area][com.maxtime.app.data.UserLocation] already expects
 *  everywhere else in the app, so callers don't need to know coordinates exist at all.
 *  Returns null if geocoding fails or the device has no geocoding backend available (this is
 *  the expected, handled failure mode — never throws) — callers should fall back to a plain
 *  "lat, lng" label or prompt for manual entry rather than treating it as fatal. */
expect suspend fun reverseGeocode(latitude: Double, longitude: Double): String?
