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
    val skill: String? = null,
    val durationMinutes: Int = 60
)

data class Venue(
    val name: String,
    val distanceKm: Double
)

/**
 * A facility's real open-hours window for one activity on one date — e.g. a badminton court
 * open 7:00–10:00 PM. Unlike [Session] (a fixed single class/occurrence with its own start time
 * and headcount), this is a RANGE: what counts as a bookable slot inside it depends on how long
 * the booking needs to be, computed by [computeAvailableSlots] rather than stored directly.
 */
data class VenueAvailability(
    val id: String,
    val activity: String,
    val venueName: String,
    val distanceKm: Double,
    val date: String,
    val opensAt: String,
    val closesAt: String,
    val price: Int,
    val capacity: Int
)

/** One concrete, real (start, end) pair that actually fits inside a venue's open hours for a
 *  requested duration — the only kind of slot a picker should ever offer as selectable. */
data class TimeSlotOption(
    val venueName: String,
    val distanceKm: Double,
    val date: String,
    val startTime: String,
    val endTime: String,
    val price: Int,
    val capacity: Int
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

enum class GroupMemberRole { CREATOR, MEMBER }

/** A real membership relation — who is actually in a group, not just a headcount. */
data class GroupMember(
    val personName: String,
    val role: GroupMemberRole,
    val joinedAt: String
)

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
    val needed: Int,
    val capacity: Int,
    val creator: String,
    priceNormal: Int,
    priceIfDeadlineMissed: Int,
    val icon: String
) {
    var whenLabel by mutableStateOf(whenLabel)
    var venue by mutableStateOf(venue)
    var sessionId by mutableStateOf(sessionId)
    /** Set once a real venue+time is confirmed (see AppState.resolveSessionForGroup/confirmVenueSlot)
     *  — the only source of truth [deadline]/[isPastFillDeadline] compute from, never a stored
     *  string that could drift out of sync with the actual confirmed time. */
    var eventWeekdayIndex by mutableStateOf<Int?>(null)
    var eventStartMinutes by mutableStateOf<Int?>(null)
    val memberList = mutableStateListOf<GroupMember>()
    val members: Int get() = memberList.size
    var status by mutableStateOf(GroupStatus.FILLING)
    var paymentStatus by mutableStateOf(PaymentStatus.NOT_REQUESTED)
    var bookingStatus by mutableStateOf(BookingStatus.UNBOOKED)
    var priceNormal by mutableStateOf(priceNormal)
    var priceIfDeadlineMissed by mutableStateOf(priceIfDeadlineMissed)
    val connections = mutableStateListOf<String>()

    /** New members can join right up until exactly 2 hours before the confirmed start time —
     *  "—" until a venue/time is actually confirmed, since there's nothing real to count down to. */
    val deadline: String
        get() {
            val weekday = eventWeekdayIndex ?: return "—"
            val startMinutes = eventStartMinutes ?: return "—"
            return fillDeadlineLabel(weekday, startMinutes)
        }

    /** Computed fresh against the real clock every read — never a manually-flipped flag alone. */
    val isPastFillDeadline: Boolean
        get() {
            val weekday = eventWeekdayIndex ?: return false
            val startMinutes = eventStartMinutes ?: return false
            return fillDeadlinePassed(weekday, startMinutes)
        }

    /** [status] as manually set, upgraded to DEADLINE_MISSED the moment the real deadline has
     *  actually passed — so expiry doesn't only happen via the debug "Simulate" affordance. */
    val effectiveStatus: GroupStatus
        get() = if (status == GroupStatus.FILLING && isPastFillDeadline) GroupStatus.DEADLINE_MISSED else status

    val statusLabel: String
        get() = if (bookingStatus == BookingStatus.CONFIRMED) "Booked" else when (effectiveStatus) {
            GroupStatus.FILLING -> "Filling"
            GroupStatus.FULL -> "Full"
            GroupStatus.DEADLINE_MISSED -> "Deadline passed"
            GroupStatus.CANCELLED -> "Cancelled"
        }

    val statusVariant: StatusChipVariant
        get() = when {
            bookingStatus == BookingStatus.CONFIRMED -> StatusChipVariant.BOOKED
            effectiveStatus == GroupStatus.CANCELLED -> StatusChipVariant.CANCELLED
            effectiveStatus == GroupStatus.FULL || effectiveStatus == GroupStatus.DEADLINE_MISSED -> StatusChipVariant.FULL
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

/** Booking lifecycle — mirrors the PRD's server-authoritative state machine (never trust a
 *  client to jump straight to CONFIRMED; that only happens once a Payment reports SUCCEEDED). */
enum class BookingState { INITIATED, PAYMENT_PENDING, CONFIRMED, COMPLETED, CANCELLED, REFUNDED, FAILED }

data class Booking(
    val id: String,
    val groupId: String,
    val sessionId: String?,
    val userId: String,
    val amount: Int,
    val state: BookingState
)

enum class PaymentState { INITIATED, PENDING, SUCCEEDED, FAILED, REFUNDED }

/** [idempotencyKey] lets a repeated confirm-payment call (retry, double-tap) resolve to the
 *  same Payment instead of charging twice — required even against a mock gateway so the real
 *  one (Cloud Functions + webhook) is a drop-in swap, not a behavior change. */
data class Payment(
    val id: String,
    val bookingId: String,
    val amount: Int,
    val idempotencyKey: String,
    val state: PaymentState,
    val provider: String = "mock"
)

data class ActivityInfo(
    val icon: String,
    val category: String,
    val description: String,
    val venues: List<Venue>,
    val skillLabel: String,
    val price: Int
)

/** [sentAtMillis] is a plain epoch-millis Double, not a Firestore Timestamp — the domain model
 *  stays backend-agnostic; ChatRepository converts at the boundary. */
data class ChatMessage(
    val id: String,
    val groupId: String,
    val senderId: String,
    val senderName: String,
    val text: String,
    val sentAtMillis: Double
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
