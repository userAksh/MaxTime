package com.maxtime.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt

/**
 * Single shared source of truth for the whole app — every screen reads from this and writes
 * back to it on user action, mirroring the prototype's one `state` object. Uses a unidirectional,
 * reducer-style set of mutator functions rather than scattering mutation logic across screens.
 */
class AppState {
    var currentUser by mutableStateOf(User(name = "Priya Sharma"))
    var location by mutableStateOf(UserLocation(area = "Saket, South Delhi", radiusKm = 5))
    var intent by mutableStateOf(ActivityIntent(activity = "Badminton", whenLabel = "Sat, 9 AM", peopleNeeded = 2))
    var selectedSession by mutableStateOf<Session?>(null)
    var selectedGroup by mutableStateOf<Group?>(null)
    var selectedPersonName by mutableStateOf<String?>(null)
    var freeInvitesLeft by mutableStateOf(3)
    var locationConfirmed by mutableStateOf(false)
    var paymentFailed by mutableStateOf(false)

    /** key: person name -> Invite. */
    val invites = mutableStateMapOf<String, Invite>()

    /** key: group id -> Group. */
    val groups = mutableStateMapOf<String, Group>()

    fun activityInfo(activity: String = intent.activity): ActivityInfo = getActivityInfo(activity, location.area)

    fun sessionsFor(activity: String = intent.activity): List<Session> =
        getSessionsForActivity(activity, location.radiusKm, location.area)

    fun eligiblePeopleForIntent(): List<Person> = eligiblePeople(intent.activity)

    fun recommendedGroup(): Pair<String, Session>? {
        for (activity in HOME_CANDIDATE_ACTIVITIES) {
            val sessions = sessionsFor(activity)
            if (sessions.isNotEmpty()) return activity to sessions.first()
        }
        return null
    }

    fun nearbySession(excludeActivity: String?): Pair<String, Session>? {
        for (activity in HOME_CANDIDATE_ACTIVITIES) {
            if (activity == excludeActivity) continue
            val sessions = sessionsFor(activity)
            if (sessions.isNotEmpty()) return activity to sessions.first()
        }
        return null
    }

    /**
     * Invariant #1: a Session is never synthesized by stamping the user's requested "when"
     * onto a venue — it always carries its own independently-generated date/time.
     */
    fun selectSession(venueName: String? = null) {
        val sessions = sessionsFor(intent.activity)
        val chosen = if (venueName != null) sessions.firstOrNull { it.venueName == venueName } else sessions.firstOrNull()
        selectedSession = chosen ?: run {
            val info = activityInfo(intent.activity)
            val fallbackVenue = info.venues.first()
            Session(
                id = "sess-${intent.activity}-${fallbackVenue.name}-fallback",
                venueName = fallbackVenue.name,
                distanceKm = fallbackVenue.distanceKm,
                activity = intent.activity,
                date = "Sat",
                startTime = "9:00 AM",
                price = info.price,
                capacity = 8,
                spotsLeft = 5
            )
        }
    }

    /**
     * Invariant #4: one group per (activity, when, location) key — accepted invites and
     * creation both attach to this SAME group rather than spawning one per person.
     * Invariant #2: venue/session start null; the group stays in a searching state until
     * resolveSessionForGroup() is explicitly called.
     */
    fun getOrCreateMyGroup(): Group {
        val key = "my-${intent.activity}-${intent.whenLabel}-${location.area}"
        groups[key]?.let { return it }
        val info = activityInfo(intent.activity)
        val g = Group(
            id = key,
            activity = intent.activity,
            whenLabel = intent.whenLabel,
            where = location.area,
            venue = null,
            sessionId = null,
            members = 1,
            needed = intent.peopleNeeded,
            capacity = 1 + intent.peopleNeeded,
            creator = currentUser.name,
            deadline = "Today, 5:00 PM",
            priceNormal = info.price,
            priceIfDeadlineMissed = increasedPrice(info.price),
            icon = info.icon
        )
        groups[key] = g
        return g
    }

    fun resolveSessionForGroup(g: Group) {
        val sessions = sessionsFor(g.activity)
        val s = sessions.firstOrNull() ?: return // stays unassigned — nothing in radius
        g.venue = s.venueName
        g.sessionId = s.id
        g.whenLabel = s.whenLabel
        g.priceNormal = s.price
        g.priceIfDeadlineMissed = increasedPrice(s.price)
    }

    /**
     * Invariant #3: adding a member only ever changes membership/status — never paymentStatus
     * or bookingStatus. Only a successful payment does that (see confirmPayment()).
     */
    fun addPersonToMyGroup(personName: String): Group {
        val g = getOrCreateMyGroup()
        if (personName !in g.connections) {
            g.connections.add(personName)
            g.members = (g.members + 1).coerceAtMost(g.capacity)
        }
        if (g.members >= g.capacity) g.status = GroupStatus.FULL
        return g
    }

    fun joinExistingGroup(): Group? {
        val sessions = sessionsFor(intent.activity)
        val sess = sessions.firstOrNull() ?: return null
        val key = "open-${intent.activity}-${sess.id}"
        groups[key]?.let { selectedGroup = it; return it }
        val g = Group(
            id = key,
            activity = intent.activity,
            whenLabel = sess.whenLabel,
            where = location.area,
            venue = sess.venueName,
            sessionId = sess.id,
            members = 3,
            needed = 1,
            capacity = 4,
            creator = "Ayesha",
            deadline = "Today, 5:00 PM",
            priceNormal = sess.price,
            priceIfDeadlineMissed = increasedPrice(sess.price),
            icon = activityInfo(intent.activity).icon
        )
        g.connections.add("Ayesha")
        groups[key] = g
        selectedGroup = g
        return g
    }

    /**
     * Invariant #5: sending an invite never consumes a credit. Only status -> ACCEPTED does,
     * exactly once, guarded by `consumed` so reopening an already-accepted invite never
     * double-charges. Declined/expired invites consume nothing.
     */
    fun sendInvite(personName: String) {
        invites[personName] = Invite(personName, intent.activity, intent.whenLabel)
    }

    fun simulateAccept(personName: String) {
        val invite = invites[personName]
        if (invite != null && invite.status != InviteStatus.ACCEPTED) {
            invite.status = InviteStatus.ACCEPTED
            if (!invite.consumed) {
                invite.consumed = true
                if (freeInvitesLeft > 0) freeInvitesLeft -= 1
            }
        }
        selectedGroup = addPersonToMyGroup(personName)
    }

    fun simulateDecline(personName: String) {
        invites[personName]?.status = InviteStatus.DECLINED
    }

    fun confirmPayment() {
        paymentFailed = false
        selectedGroup?.let {
            it.paymentStatus = PaymentStatus.PAID
            it.bookingStatus = BookingStatus.CONFIRMED
        }
    }

    fun simulatePaymentFailure() {
        paymentFailed = true
        selectedGroup?.paymentStatus = PaymentStatus.FAILED
    }

    fun confirmHigherPrice() {
        selectedGroup?.let {
            it.priceNormal = it.priceIfDeadlineMissed
            it.paymentStatus = PaymentStatus.PENDING
        }
    }

    private fun increasedPrice(price: Int): Int {
        val bumped = (price * 1.35).roundToInt()
        return if (bumped == 0) 150 else bumped
    }
}
