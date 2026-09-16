package com.maxtime.app.data.firebase

import com.maxtime.app.data.Session
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.firestore.where
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Serializable
private data class VenueDoc(
    val name: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
)

@Serializable
private data class InventorySlotDoc(
    val venueId: String = "",
    val activityCategory: String = "",
    val date: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val capacity: Int = 0,
    val remaining: Int = 0,
    val price: Int = 0,
    val status: String = "",
)

private const val EARTH_RADIUS_KM = 6371.0

private fun Double.toRadians() = this * PI / 180.0

private fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val dLat = (lat2 - lat1).toRadians()
    val dLng = (lng2 - lng1).toRadians()
    val a = sin(dLat / 2).let { it * it } +
        cos(lat1.toRadians()) * cos(lat2.toRadians()) * sin(dLng / 2).let { it * it }
    return 2 * EARTH_RADIUS_KM * asin(sqrt(a))
}

private fun minutesBetween(startTime: String, endTime: String): Int {
    fun toMinutes(t: String): Int {
        val (h, m) = t.split(":").map { it.toIntOrNull() ?: 0 }
        return h * 60 + m
    }
    val diff = toMinutes(endTime) - toMinutes(startTime)
    return if (diff > 0) diff else 60
}

/**
 * Caches raw (unfiltered) query results in memory, keyed by activity/activities — session
 * inventory is fixed reference data for the lifetime of the app process (not user-generated,
 * not written from the app), so re-fetching on every screen visit only adds latency and causes
 * a visible reload/reflow with no benefit. Radius filtering is applied on each call from the
 * cached docs, so changing radius never needs a new network round trip either.
 *
 * Every Session comes from real partner-created inventory (`inventory_slots`, joined against
 * `venues` for name/location) — never mock/seed data. An activity with no real partner inventory
 * yet simply returns an empty list; callers render an honest "nothing available" state rather
 * than falling back to fabricated content.
 */
class SessionRepository {
    private val inventorySlots get() = Firebase.firestore.collection("inventory_slots")
    private val venues get() = Firebase.firestore.collection("venues")
    private val byActivityInventoryCache = mutableMapOf<String, List<Pair<String, InventorySlotDoc>>>()
    private val byActivitiesInventoryCache = mutableMapOf<List<String>, List<Pair<String, InventorySlotDoc>>>()
    private val venueCache = mutableMapOf<String, VenueDoc>()

    /** A successful real booking hold decrements `InventorySlot.remaining` in Firestore — this
     *  class's per-process cache doesn't know that happened, so without clearing it the slot
     *  just booked would keep appearing available for the rest of the app session. */
    fun invalidateCache() {
        byActivityInventoryCache.clear()
        byActivitiesInventoryCache.clear()
    }

    suspend fun sessionsFor(
        activity: String,
        radiusKm: Int,
        userLat: Double? = null,
        userLng: Double? = null,
    ): List<Session> = realSessionsFor(
        byActivityInventoryCache.getOrPut(activity) {
            inventorySlots.where { "activityCategory" equalTo activity }.get().documents
                .map { it.id to it.data<InventorySlotDoc>() }
        },
        userLat,
        userLng,
    ).filter { it.distanceKm <= radiusKm }

    suspend fun sessionsForActivities(
        activities: List<String>,
        radiusKm: Int,
        userLat: Double? = null,
        userLng: Double? = null,
    ): List<Session> {
        if (activities.isEmpty()) return emptyList()
        return realSessionsFor(
            byActivitiesInventoryCache.getOrPut(activities) {
                inventorySlots.where { "activityCategory" inArray activities }.get().documents
                    .map { it.id to it.data<InventorySlotDoc>() }
            },
            userLat,
            userLng,
        ).filter { it.distanceKm <= radiusKm }
    }

    private suspend fun realSessionsFor(
        slotDocs: List<Pair<String, InventorySlotDoc>>,
        userLat: Double?,
        userLng: Double?,
    ): List<Session> {
        val bookable = slotDocs.filter { (_, doc) -> doc.status == "AVAILABLE" && doc.remaining > 0 }
        if (bookable.isEmpty()) return emptyList()

        // A slot's venue can be unpublished after the slot was generated — Firestore then denies
        // this read (rules require published == true for a non-partner, non-admin reader), which
        // must not take down the whole session list over one stale slot; skip just that venue.
        val venueIds = bookable.map { (_, doc) -> doc.venueId }.filter { it.isNotBlank() }.distinct()
        for (venueId in venueIds) {
            if (!venueCache.containsKey(venueId)) {
                try {
                    val snap = venues.document(venueId).get()
                    if (snap.exists) venueCache[venueId] = snap.data<VenueDoc>()
                } catch (_: Exception) {
                    // Unpublished/deleted venue — its slots are filtered out below (no cache entry).
                }
            }
        }

        return bookable.filter { (_, doc) -> venueCache.containsKey(doc.venueId) }.map { (id, doc) ->
            val venue = venueCache[doc.venueId]
            val distanceKm = if (venue != null && userLat != null && userLng != null) {
                haversineKm(userLat, userLng, venue.lat, venue.lng)
            } else {
                0.0
            }
            Session(
                id = id,
                venueName = venue?.name ?: "Venue",
                distanceKm = distanceKm,
                activity = doc.activityCategory,
                date = doc.date,
                startTime = doc.startTime,
                durationMinutes = minutesBetween(doc.startTime, doc.endTime),
                price = doc.price,
                capacity = doc.capacity,
                spotsLeft = doc.remaining,
            )
        }
    }
}
