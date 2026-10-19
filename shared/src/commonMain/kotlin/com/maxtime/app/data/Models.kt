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
 *  (see `LocationPopup`) — manual text entry (including the default "Saket, South Delhi" area
 *  the app starts with) leaves them null. [SessionRepository.realSessionsFor] can only compute a
 *  real per-venue distance when these are set — without them every session's `distanceKm` is a
 *  placeholder 0.0, which [kmAwayLabel]/[kmLabel] below know to render honestly rather than as a
 *  literal (and misleading) "0.0 km away". */
data class UserLocation(
    val area: String,
    val radiusKm: Int,
    val latitude: Double? = null,
    val longitude: Double? = null
) {
    val radiusLabel: String get() = "$radiusKm km"
    val hasCoordinates: Boolean get() = latitude != null && longitude != null

    // Rounds to 1 decimal place before formatting — a raw Double this small (e.g. a venue a few
    // meters away) renders via Kotlin's default toString() as scientific notation ("1.48E-5"),
    // which is what rounding here avoids, not just cosmetic decimal-place trimming.
    fun kmAwayLabel(distanceKm: Double): String {
        if (!hasCoordinates) return "distance unknown"
        val rounded = kotlin.math.round(distanceKm * 10) / 10.0
        return "$rounded km away"
    }
    fun kmLabel(distanceKm: Double): String {
        if (!hasCoordinates) return "distance unknown"
        val rounded = kotlin.math.round(distanceKm * 10) / 10.0
        return "$rounded km"
    }
}

/** What the user is currently searching for. Ephemeral, not persisted long-term. */
data class ActivityIntent(
    val activity: String,
    val whenLabel: String,
    val peopleNeeded: Int = 2,
    val skill: String? = null,
    val durationMinutes: Int = 60
)

/** One concrete, real (start, end) pair for a specific real Session — matched against what the
 *  user asked for (day/duration) in [AppState.availableSlotsFor], never synthesized. */
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
    // formatSessionDate/formatSessionTime handle both the mock vocabulary's bare weekday
    // abbreviation + 12h time and real InventorySlot-derived ISO date + 24h time — without this,
    // every screen that displays whenLabel (SessionDetail, BookingReview, GroupChat/Detail, Home,
    // Plans) would show a real session's raw "2026-09-12, 07:00" instead of "Today, 7:00 AM".
    val whenLabel: String get() = "${formatSessionDate(date)}, ${formatSessionTime(startTime)}"
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
    val joinedAt: String,
    // Only ever non-null for the real signed-in user (the creator) — invited/simulated
    // co-members ("Ayesha", "Guest", invite-by-name) aren't real accounts with a real photo.
    val photoUrl: String? = null
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
    sessionDate: String? = null,
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
    /** The resolved session's raw `date` (kept format-agnostic — bare weekday abbreviation for
     *  mock Sessions, ISO yyyy-MM-dd for real ones) — lets "another group the same day, different
     *  time" be found by exact string match without needing to reverse [eventWeekdayIndex] back
     *  into a specific calendar date. */
    var sessionDate by mutableStateOf(sessionDate)
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
    /** Real Firebase Auth UIDs of members we actually know the identity of — the creator (at
     *  creation) and anyone who joined via the real "Join Group" flow (AppState.joinGroup).
     *  Deliberately NOT populated for invite-by-name/simulated members (no real UID exists for
     *  them) — this list exists so Firestore security rules can verify membership by UID instead
     *  of by [GroupMember.personName], which isn't a reliable identity check (names can collide
     *  or change). See firestore.rules' groups/{groupId} rule. */
    val memberUids = mutableStateListOf<String>()

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
    val sentAtMillis: Double,
    // Only ever non-null when the real signed-in user sent this message — see GroupMember.photoUrl.
    val photoUrl: String? = null
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
