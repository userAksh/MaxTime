package com.maxtime.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.maxtime.app.data.firebase.SessionRepository
import com.maxtime.app.data.firebase.VenueAvailabilityRepository
import com.maxtime.app.data.repository.BookingRepository
import com.maxtime.app.data.repository.FirestoreGroupRepository
import com.maxtime.app.data.repository.GroupRepository
import com.maxtime.app.data.repository.InviteRepository
import com.maxtime.app.data.repository.MockBookingRepository
import com.maxtime.app.data.repository.MockInviteRepository
import com.maxtime.app.data.repository.MockPaymentRepository
import com.maxtime.app.data.repository.PaymentRepository
import kotlin.math.roundToInt

/**
 * Single shared source of truth for the whole app — every screen reads from this and writes
 * back to it on user action, mirroring the prototype's one `state` object. Uses a unidirectional,
 * reducer-style set of mutator functions rather than scattering mutation logic across screens.
 *
 * Storage and per-aggregate invariants live behind repository interfaces (mock-backed today,
 * swappable for Firestore/Cloud Functions-backed ones later without touching this class's public
 * surface); AppState itself only orchestrates the cross-aggregate flows (e.g. an accepted invite
 * touching both Invite and Group, a confirmed payment touching Group, Booking, and Payment).
 */
class AppState(
    private val sessionRepository: SessionRepository = SessionRepository(),
    private val venueAvailabilityRepository: VenueAvailabilityRepository = VenueAvailabilityRepository(),
    private val groupRepository: GroupRepository = FirestoreGroupRepository(),
    private val inviteRepository: InviteRepository = MockInviteRepository(),
    private val bookingRepository: BookingRepository = MockBookingRepository(),
    private val paymentRepository: PaymentRepository = MockPaymentRepository()
) {
    var currentUser by mutableStateOf(User(name = "Priya Sharma"))
    // A previously-confirmed location (real GPS fix, map pick, or submitted manual entry) is
    // restored immediately so a returning user never sees the location sheet again — a "Skip for
    // now" deliberately never persists, so skipping doesn't get silently treated the same as
    // actually setting one (see persistConfirmedLocation).
    private val persistedLocation = loadPersistedLocation()
    var location by mutableStateOf(persistedLocation ?: UserLocation(area = "Saket, South Delhi", radiusKm = 5))
    var intent by mutableStateOf(ActivityIntent(activity = "Badminton", whenLabel = "Sat, 9 AM", peopleNeeded = 2))
    var selectedSession by mutableStateOf<Session?>(null)
    var selectedGroup by mutableStateOf<Group?>(null)
    var selectedPersonName by mutableStateOf<String?>(null)
    var freeInvitesLeft by mutableStateOf(3)
    var locationConfirmed by mutableStateOf(persistedLocation != null)
    var paymentFailed by mutableStateOf(false)
    /** Chosen on the onboarding Interests step (or restored from a saved profile). Not yet
     *  wired into discovery ranking — that's a later build-order step. */
    var interests by mutableStateOf<Map<String, Set<String>>>(DEFAULT_SELECTED_INTERESTS)
    /** One-shot signal: set right before navigating to GroupDetail after creating an activity,
     *  consumed (and reset) there to show a confirmation the user actually asked for. */
    var justCreatedActivity by mutableStateOf(false)

    /** key: person name -> Invite. */
    val invites: MutableMap<String, Invite> get() = inviteRepository.invites

    /** key: group id -> Group. */
    val groups: MutableMap<String, Group> get() = groupRepository.groups

    /** Restores whatever groups this user created or joined before the app was last closed —
     *  call once at launch (after the real profile name, if any, has been restored). */
    suspend fun loadPersistedGroups() = groupRepository.loadMyGroups(currentUser.name)

    fun activityInfo(activity: String = intent.activity): ActivityInfo = getActivityInfo(activity, location.area)

    suspend fun sessionsFor(activity: String = intent.activity): List<Session> =
        sessionRepository.sessionsFor(activity, location.radiusKm).filterNotPassed()

    /**
     * Real (start, end) options for a brand-new activity — scoped to the day actually requested
     * (never "any day this venue happens to be open") and computed purely from real venue open
     * hours for the requested duration, so nothing offered here can ever be a slot that doesn't
     * really fit.
     */
    suspend fun availableSlotsFor(durationMinutes: Int, activity: String = intent.activity): List<TimeSlotOption> {
        val windows = venueAvailabilityRepository.availabilityFor(activity, location.radiusKm)
        val allowedWeekdays = matchingWeekdayIndices(intent.whenLabel)
        val scoped = allowedWeekdays?.let { allowed ->
            windows.filter { w -> weekdayIndexOf(w.date)?.let { it in allowed } == true }
        } ?: windows
        return computeAvailableSlots(scoped, durationMinutes).filterNot { hasSessionPassed(it.date, it.startTime) }
    }

    /** Assigns a concretely-confirmed venue/time directly — used when the user picked an exact
     *  real slot up front, so there's no deferred "searching" state to wait out (contrast with
     *  [resolveSessionForGroup], which matches a class-style Session later, if/when one appears). */
    fun confirmVenueSlot(g: Group, slot: TimeSlotOption) {
        val dayLabel = relativeDayLabel(slot.date) ?: slot.date
        g.venue = slot.venueName
        g.whenLabel = "$dayLabel, ${slot.startTime} – ${slot.endTime}"
        g.eventWeekdayIndex = weekdayIndexOf(slot.date)
        g.eventStartMinutes = parseClockTimeToMinutes(slot.startTime)
        g.priceNormal = slot.price
        g.priceIfDeadlineMissed = increasedPrice(slot.price)
        groupRepository.save(g)
    }

    fun eligiblePeopleForIntent(): List<Person> = eligiblePeople(intent.activity)

    suspend fun recommendedGroup(): Pair<String, Session>? {
        val sessions = sessionRepository.sessionsForActivities(HOME_CANDIDATE_ACTIVITIES, location.radiusKm).filterNotPassed()
        for (activity in HOME_CANDIDATE_ACTIVITIES) {
            val session = sessions.firstOrNull { it.activity == activity }
            if (session != null) return activity to session
        }
        return null
    }

    suspend fun nearbySession(excludeActivity: String?): Pair<String, Session>? {
        val sessions = sessionRepository.sessionsForActivities(HOME_CANDIDATE_ACTIVITIES, location.radiusKm).filterNotPassed()
        for (activity in HOME_CANDIDATE_ACTIVITIES) {
            if (activity == excludeActivity) continue
            val session = sessions.firstOrNull { it.activity == activity }
            if (session != null) return activity to session
        }
        return null
    }

    /** A session that already started is never offered — solo booking, group creation, and
     *  "join a group" all filter through here (via [sessionsFor]/[recommendedGroup]/[nearbySession]). */
    private fun List<Session>.filterNotPassed(): List<Session> =
        filterNot { hasSessionPassed(it.date, it.startTime) }

    /**
     * Invariant #1: a Session is never synthesized by stamping the user's requested "when"
     * onto a venue — it always carries its own independently-generated date/time.
     */
    suspend fun selectSession(venueName: String? = null) {
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
        val info = activityInfo(intent.activity)
        return groupRepository.getOrCreateMyGroup(
            intent = intent,
            location = location,
            creatorName = currentUser.name,
            info = info,
            priceIfDeadlineMissed = increasedPrice(info.price)
        )
    }

    /**
     * Prefers a session matching the specific day AND time slot the group actually asked for
     * (set on CreateGroupScreen) — falling back to the first available only when nothing lines
     * up, so a group never silently gets matched to a time its members never agreed to. Matching
     * on time alone isn't enough: the same clock time can be real on more than one day, so a
     * "Tomorrow, 6:00 PM" group must never resolve to a session that's actually today at 6:00 PM.
     */
    suspend fun resolveSessionForGroup(g: Group) {
        val sessions = sessionsFor(g.activity)
        val preferredTime = g.whenLabel.substringAfter(",", "").trim()
        val allowedWeekdays = matchingWeekdayIndices(g.whenLabel)
        val s = sessions.firstOrNull { session ->
            preferredTime.isNotEmpty() && session.startTime == preferredTime &&
                (allowedWeekdays == null || weekdayIndexOf(session.date)?.let { it in allowedWeekdays } == true)
        } ?: sessions.firstOrNull() ?: return // stays unassigned — nothing in radius
        g.venue = s.venueName
        g.sessionId = s.id
        g.whenLabel = s.whenLabel
        g.eventWeekdayIndex = weekdayIndexOf(s.date)
        g.eventStartMinutes = parseClockTimeToMinutes(s.startTime)
        g.priceNormal = s.price
        g.priceIfDeadlineMissed = increasedPrice(s.price)
        groupRepository.save(g)
    }

    /**
     * Invariant #3: adding a member only ever changes membership/status — never paymentStatus
     * or bookingStatus. Only a successful payment does that (see confirmPayment()).
     */
    fun addPersonToMyGroup(personName: String): Group =
        groupRepository.addMember(getOrCreateMyGroup(), personName)

    /** Adds the current user to a group they found and are not yet part of (e.g. one surfaced
     *  by "Join a group" in Find Results) — distinct from [addPersonToMyGroup], which only ever
     *  operates on the caller's own group. */
    fun joinGroup(g: Group): Group = groupRepository.addMember(g, currentUser.name)

    suspend fun joinExistingGroup(): Group? {
        val sessions = sessionsFor(intent.activity)
        val sess = sessions.firstOrNull() ?: return null
        val info = activityInfo(intent.activity)
        val g = groupRepository.joinExistingGroup(
            intent = intent,
            location = location,
            session = sess,
            info = info,
            priceIfDeadlineMissed = increasedPrice(sess.price)
        )
        selectedGroup = g
        return g
    }

    /**
     * Invariant #5: sending an invite never consumes a credit. Only status -> ACCEPTED does,
     * exactly once, guarded by `consumed` so reopening an already-accepted invite never
     * double-charges. Declined/expired invites consume nothing.
     */
    fun sendInvite(personName: String) {
        inviteRepository.sendInvite(personName, intent.activity, intent.whenLabel)
    }

    fun simulateAccept(personName: String) {
        val invite = invites[personName]
        val wasAlreadyAccepted = invite?.status == InviteStatus.ACCEPTED
        inviteRepository.accept(personName)
        if (invite != null && !wasAlreadyAccepted && !invite.consumed) {
            invite.consumed = true
            if (freeInvitesLeft > 0) freeInvitesLeft -= 1
        }
        selectedGroup = addPersonToMyGroup(personName)
    }

    fun simulateDecline(personName: String) {
        inviteRepository.decline(personName)
    }

    /**
     * Server-authoritative in shape even against a mock gateway: a Booking is created (or
     * fetched idempotently) before a Payment is charged against it, and only a SUCCEEDED
     * Payment flips the Group to PAID/CONFIRMED — never the client jumping straight there.
     */
    fun confirmPayment() {
        val g = selectedGroup ?: return
        paymentFailed = false
        val booking = bookingRepository.createOrGet(
            groupId = g.id, sessionId = g.sessionId, userId = currentUser.id, amount = g.priceNormal
        )
        val payment = paymentRepository.charge(bookingId = booking.id, amount = g.priceNormal, idempotencyKey = g.id)
        if (payment.state == PaymentState.SUCCEEDED) {
            bookingRepository.updateState(g.id, BookingState.CONFIRMED)
            g.paymentStatus = PaymentStatus.PAID
            g.bookingStatus = BookingStatus.CONFIRMED
            groupRepository.save(g)
        }
    }

    fun simulatePaymentFailure() {
        val g = selectedGroup ?: return
        paymentFailed = true
        val booking = bookingRepository.createOrGet(
            groupId = g.id, sessionId = g.sessionId, userId = currentUser.id, amount = g.priceNormal
        )
        paymentRepository.recordFailure(bookingId = booking.id, amount = g.priceNormal, idempotencyKey = g.id)
        bookingRepository.updateState(g.id, BookingState.FAILED)
        g.paymentStatus = PaymentStatus.FAILED
        groupRepository.save(g)
    }

    fun confirmHigherPrice() {
        selectedGroup?.let {
            it.priceNormal = it.priceIfDeadlineMissed
            it.paymentStatus = PaymentStatus.PENDING
            groupRepository.save(it)
        }
    }

    private fun increasedPrice(price: Int): Int {
        val bumped = (price * 1.35).roundToInt()
        return if (bumped == 0) 150 else bumped
    }
}
