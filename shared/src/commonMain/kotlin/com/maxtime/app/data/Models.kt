package com.maxtime.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class User(
    val id: String = "u-1",
    val name: String,
    val photoUrl: String? = null,
    val dateOfBirth: String? = null,
    val phoneOrEmail: String? = null
)

/** Canonical location — the ONE source of truth. Nothing else duplicates "current location".
 *  [latitude]/[longitude] are only populated when [area] came from a real GPS fix or map pick
 *  (see `LocationPopup`) — manual text entry leaves them null. Nothing in this app's mocked
 *  session/distance logic reads them yet, but real device coordinates are worth keeping once
 *  we have them rather than throwing them away down to just a label. */
data class UserLocation(
    val area: String,
    val radiusKm: Int,
    val latitude: Double? = null,
    val longitude: Double? = null
) {
    val radiusLabel: String get() = "$radiusKm km"
}

/** What the user is currently searching for. Ephemeral, not persisted long-term. */
data class ActivityIntent(
    val activity: String,
    val whenLabel: String,
    val peopleNeeded: Int = 2,
    val skill: String? = null
)

data class Venue(
    val name: String,
    val distanceKm: Double
)

/**
 * A REAL bookable occurrence with its own date/time/capacity — independent of what a user
 * typed as "when". Never synthesized by stamping the user's requested time onto a venue.
 */
data class Session(
    val id: String,
    val venueName: String,
    val distanceKm: Double,
    val activity: String,
    val date: String,
    val startTime: String,
    val durationMinutes: Int = 60,
    val price: Int,
    val capacity: Int = 8,
    val spotsLeft: Int,
    val cancellationPolicy: String = "Free cancellation up to 2 hours before start."
) {
    val whenLabel: String get() = "$date, $startTime"
}

enum class GroupStatus { FILLING, FULL, DEADLINE_MISSED, CANCELLED }
enum class PaymentStatus { NOT_REQUESTED, PENDING, PAID, FAILED }
enum class BookingStatus { UNBOOKED, CONFIRMED }
enum class StatusChipVariant { FILLING, FULL, BOOKED, CANCELLED }

/**
 * Mutable fields are Compose-state-backed so any screen reading a Group recomposes the moment
 * it's mutated in place — mirrors the prototype's "mutate the object, then render()" model but
 * without needing an explicit render() call anywhere.
 */
class Group(
    val id: String,
    val activity: String,
    whenLabel: String,
    val where: String,
    venue: String? = null,
    sessionId: String? = null,
    members: Int,
    val needed: Int,
    val capacity: Int,
    val creator: String,
    val deadline: String,
    priceNormal: Int,
    priceIfDeadlineMissed: Int,
    val icon: String
) {
    var whenLabel by mutableStateOf(whenLabel)
    var venue by mutableStateOf(venue)
    var sessionId by mutableStateOf(sessionId)
    var members by mutableStateOf(members)
    var status by mutableStateOf(GroupStatus.FILLING)
    var paymentStatus by mutableStateOf(PaymentStatus.NOT_REQUESTED)
    var bookingStatus by mutableStateOf(BookingStatus.UNBOOKED)
    var priceNormal by mutableStateOf(priceNormal)
    var priceIfDeadlineMissed by mutableStateOf(priceIfDeadlineMissed)
    val connections = mutableStateListOf<String>()

    val statusLabel: String
        get() = if (bookingStatus == BookingStatus.CONFIRMED) "Booked" else when (status) {
            GroupStatus.FILLING -> "Filling"
            GroupStatus.FULL -> "Full"
            GroupStatus.DEADLINE_MISSED -> "Deadline passed"
            GroupStatus.CANCELLED -> "Cancelled"
        }

    val statusVariant: StatusChipVariant
        get() = when {
            bookingStatus == BookingStatus.CONFIRMED -> StatusChipVariant.BOOKED
            status == GroupStatus.CANCELLED -> StatusChipVariant.CANCELLED
            status == GroupStatus.FULL || status == GroupStatus.DEADLINE_MISSED -> StatusChipVariant.FULL
            else -> StatusChipVariant.FILLING
        }

    val venueLine: String get() = venue?.let { "$it, $where" } ?: "Finding a suitable session near $where…"
    val openSlots: Int get() = (capacity - members).coerceAtLeast(0)
}

enum class InviteStatus { PENDING, ACCEPTED, DECLINED, EXPIRED }

class Invite(val personName: String, val activity: String, val whenLabel: String) {
    var status by mutableStateOf(InviteStatus.PENDING)
    var consumed by mutableStateOf(false)
}

enum class BookingRecordStatus { REVIEWING, PAID, FAILED }

data class ActivityInfo(
    val icon: String,
    val category: String,
    val description: String,
    val venues: List<Venue>,
    val skillLabel: String,
    val price: Int
)

data class Person(
    val name: String,
    val distanceLabel: String,
    val activitiesCompleted: Int,
    val attendance: String,
    val rating: Double,
    val connected: Boolean,
    val skills: Map<String, String>
) {
    fun skillFor(activity: String): String = skills[activity] ?: "New to this activity"
}
