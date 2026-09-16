package com.maxtime.app.data.firebase

import com.maxtime.app.data.StatusChipVariant
import com.maxtime.app.data.formatSessionDate
import com.maxtime.app.data.formatSessionTime
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.firestore.toMilliseconds
import dev.gitlive.firebase.firestore.where
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

@Serializable
private data class BookingDoc(
    val slotId: String = "",
    val serviceId: String = "",
    val venueId: String = "",
    val customerUserId: String = "",
    val participantCount: Int = 1,
    val status: String = "",
    val amount: Int = 0,
    val currency: String = "INR",
    val createdAt: Timestamp? = null,
)

@Serializable
private data class BookingSlotDoc(val date: String = "", val startTime: String = "", val activityCategory: String = "")

@Serializable
private data class BookingVenueDoc(val name: String = "")

@Serializable
private data class BookingServiceDoc(val name: String = "")

data class MyBooking(
    val id: String,
    val activity: String,
    val venueName: String,
    val serviceName: String,
    val whenLabel: String,
    val amount: Int,
    val currency: String,
    val statusLabel: String,
    val statusVariant: StatusChipVariant,
)

private fun bookingStatusDisplay(status: String): Pair<String, StatusChipVariant> = when (status) {
    "CONFIRMED", "CHECKED_IN", "COMPLETED", "RESOLVED" -> "Confirmed" to StatusChipVariant.BOOKED
    "PENDING_PAYMENT" -> "Pending payment" to StatusChipVariant.FILLING
    else -> status.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() } to StatusChipVariant.CANCELLED
}

/**
 * Real, Firestore-backed — unlike Groups (mock/local, built around the "start an activity/join a
 * group" flow), a Booking is created server-side by createBookingHold the moment a real slot is
 * reserved, so this is the only source of truth for "what did I actually pay for," including
 * solo bookings that never touch the Group system at all.
 */
class BookingsRepository {
    private val venueNameCache = mutableMapOf<String, String>()
    private val serviceNameCache = mutableMapOf<String, String>()
    // Guards the two caches above — toMyBooking now runs concurrently across bookings (see
    // observeMyBookings), so plain HashMaps would otherwise be mutated from multiple coroutines
    // at once whenever two bookings share the same venue/service.
    private val cacheMutex = Mutex()

    fun observeMyBookings(customerUserId: String): Flow<List<MyBooking>> =
        Firebase.firestore.collection("bookings")
            .where { "customerUserId" equalTo customerUserId }
            .snapshots
            .map { snapshot ->
                coroutineScope {
                    snapshot.documents
                        .map { doc -> doc.id to doc.data<BookingDoc>() }
                        .sortedByDescending { (_, b) -> b.createdAt?.toMilliseconds() ?: 0.0 }
                        .map { (id, b) -> async { toMyBooking(id, b) } }
                        .awaitAll()
                }
            }
            // A stale/unpublished venue or a permission hiccup on one join must not take down the
            // whole list — same defensive posture as SessionRepository's own venue lookups.
            .catch { emit(emptyList()) }

    private suspend fun toMyBooking(id: String, b: BookingDoc): MyBooking = coroutineScope {
        val slotDeferred = async {
            runCatching { Firebase.firestore.collection("inventory_slots").document(b.slotId).get() }
                .getOrNull()?.takeIf { it.exists }?.data<BookingSlotDoc>()
        }
        val venueDeferred = async { cachedName(venueNameCache, b.venueId, "venues", "Venue") { it.data<BookingVenueDoc>().name } }
        val serviceDeferred = async { cachedName(serviceNameCache, b.serviceId, "services", "Session") { it.data<BookingServiceDoc>().name } }

        val slot = slotDeferred.await()
        val venueName = venueDeferred.await()
        val serviceName = serviceDeferred.await()

        val whenLabel = if (slot != null) {
            "${formatSessionDate(slot.date)}, ${formatSessionTime(slot.startTime)}"
        } else {
            "Date to be confirmed"
        }
        val (statusLabel, statusVariant) = bookingStatusDisplay(b.status)

        MyBooking(
            id = id,
            activity = slot?.activityCategory ?: serviceName,
            venueName = venueName,
            serviceName = serviceName,
            whenLabel = whenLabel,
            amount = b.amount,
            currency = b.currency,
            statusLabel = statusLabel,
            statusVariant = statusVariant,
        )
    }

    private suspend fun cachedName(
        cache: MutableMap<String, String>,
        docId: String,
        collection: String,
        fallback: String,
        nameOf: (dev.gitlive.firebase.firestore.DocumentSnapshot) -> String,
    ): String {
        cacheMutex.withLock { cache[docId] }?.let { return it }
        val fetched = runCatching { Firebase.firestore.collection(collection).document(docId).get() }
            .getOrNull()?.takeIf { it.exists }?.let(nameOf) ?: fallback
        cacheMutex.withLock { cache[docId] = fetched }
        return fetched
    }
}
