package com.maxtime.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.maxtime.app.data.firebase.BookingFunctions
import com.maxtime.app.data.firebase.BookingHoldOutcome
import com.maxtime.app.data.firebase.BookingsRepository
import com.maxtime.app.data.firebase.CreateOrderOutcome
import com.maxtime.app.data.firebase.MyBooking
import com.maxtime.app.data.firebase.ParticipantPayload
import com.maxtime.app.data.firebase.PaymentFunctions
import com.maxtime.app.data.firebase.RazorpayOrderInfo
import com.maxtime.app.data.firebase.SessionRepository
import com.maxtime.app.data.payment.RazorpayCheckoutResult
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
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
    private val groupRepository: GroupRepository = FirestoreGroupRepository(),
    private val inviteRepository: InviteRepository = MockInviteRepository(),
    private val bookingRepository: BookingRepository = MockBookingRepository(),
    private val paymentRepository: PaymentRepository = MockPaymentRepository(),
    private val bookingFunctions: BookingFunctions = BookingFunctions(),
    private val paymentFunctions: PaymentFunctions = PaymentFunctions(),
    private val bookingsRepository: BookingsRepository = BookingsRepository()
) {
    // Empty, not a placeholder name — App.kt's own profile-load effect fills in the real saved
    // name for a returning user; for a brand-new user this stays blank until ProfileScreen sets
    // it, same as photoUrl/interests below. A hardcoded display name here would leak into
    // ProfileScreen's editable field as if the user had already typed it (see MaxTimeTextField's
    // placeholder handling, which only shows a hint when the value is genuinely empty).
    var currentUser by mutableStateOf(User(name = ""))
    // A previously-confirmed location (real GPS fix, map pick, or submitted manual entry) is
    // restored immediately so a returning user never sees the location sheet again — a "Skip for
    // now" deliberately never persists, so skipping doesn't get silently treated the same as
    // actually setting one (see persistConfirmedLocation).
    private val persistedLocation = loadPersistedLocation()
    var location by mutableStateOf(persistedLocation ?: UserLocation(area = "Saket, South Delhi", radiusKm = 5))
    var intent by mutableStateOf(ActivityIntent(activity = "Badminton", whenLabel = "Sat, 9 AM", peopleNeeded = 2))
    var selectedSession by mutableStateOf<Session?>(null)
    /** Set right before navigating from ActivityDetail's venue list into VenueSessions, so that
     *  screen knows which venue's slots to show (Session itself carries no venue id, only a
     *  display name — grouping/filtering is done by that name). */
    var selectedVenueName by mutableStateOf<String?>(null)
    var selectedGroup by mutableStateOf<Group?>(null)
    var selectedPersonName by mutableStateOf<String?>(null)
    var freeInvitesLeft by mutableStateOf(3)
    var locationConfirmed by mutableStateOf(persistedLocation != null)
    var paymentFailed by mutableStateOf(false)
    /** Set when a real createBookingHold call is rejected (slot already booked, gone, etc.) —
     *  distinct from [paymentFailed], which the "Simulate: payment failure" debug affordance also
     *  sets: a rejected hold isn't fixed by retrying payment, the user needs a different slot. */
    var bookingHoldError by mutableStateOf<String?>(null)
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
     *  call once at launch (after the real profile name, if any, has been restored). Best-effort:
     *  a launch-time restore shouldn't crash the app over a transient Firestore/network failure. */
    suspend fun loadPersistedGroups() {
        try {
            groupRepository.loadMyGroups(currentUser.name)
        } catch (_: Exception) {
            // Best-effort restore — groups just stay empty until the next successful sync.
        }
    }

    fun activityInfo(activity: String = intent.activity): ActivityInfo = getActivityInfo(activity, location.area)

    suspend fun sessionsFor(activity: String = intent.activity): List<Session> =
        sessionRepository.sessionsFor(activity, location.radiusKm, location.latitude, location.longitude).filterNotPassed()

    /**
     * Real (start, end) options for a brand-new activity — scoped to the day actually requested
     * (never "any day this venue happens to be open") and to the requested duration, drawn
     * directly from real partner-configured inventory (the same Sessions every other screen
     * shows), so nothing offered here can ever be a slot that doesn't really exist.
     */
    suspend fun availableSlotsFor(durationMinutes: Int, activity: String = intent.activity): List<TimeSlotOption> {
        return sessionsFor(activity)
            .filter { it.durationMinutes == durationMinutes }
            .filter { session -> sessionDateMatch(session.date, intent.whenLabel)?.matchesRequest != false }
            .map { session ->
                TimeSlotOption(
                    venueName = session.venueName,
                    distanceKm = session.distanceKm,
                    date = session.date,
                    startTime = session.startTime,
                    endTime = minutesToClockTime((parseClockTimeToMinutes(session.startTime) ?: 0) + session.durationMinutes),
                    price = session.price,
                    capacity = session.capacity
                )
            }
    }

    /** Assigns a concretely-confirmed venue/time directly — used when the user picked an exact
     *  real slot up front, so there's no deferred "searching" state to wait out (contrast with
     *  [resolveSessionForGroup], which matches a class-style Session later, if/when one appears). */
    fun confirmVenueSlot(g: Group, slot: TimeSlotOption) {
        val dayLabel = relativeDayLabel(slot.date) ?: slot.date
        g.venue = slot.venueName
        g.whenLabel = "$dayLabel, ${slot.startTime} – ${slot.endTime}"
        g.sessionDate = slot.date
        g.eventWeekdayIndex = weekdayIndexOf(slot.date)
        g.eventStartMinutes = parseClockTimeToMinutes(slot.startTime)
        g.priceNormal = slot.price
        g.priceIfDeadlineMissed = increasedPrice(slot.price)
        groupRepository.save(g)
    }

    /** `currentUser.id` defaults to a mock placeholder ("u-1") that's never actually updated to
     *  the real signed-in identity anywhere — real server-facing writes (createBookingHold's
     *  participant, the bookings query below) need the real Firebase Auth uid instead, same
     *  fallback GroupChatScreen already uses. */
    private val realUserId: String get() = Firebase.auth.currentUser?.uid ?: currentUser.id

    /** Lives for the whole app process — backs the warm bookings flow below so switching away
     *  from Plans and back doesn't tear down and re-attach the Firestore listener from scratch. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Warm/shared: started once (lazily, on first access) and kept alive for the rest of the
     *  process via [SharingStarted.Eagerly], so every screen that (re)collects it — most notably
     *  Plans on every tab switch, since [com.maxtime.app.ui.nav.Router] fully disposes and
     *  recomposes screens rather than keeping them alive — gets the last-known list instantly
     *  instead of waiting on a brand-new query + listener attach each time. */
    private val myBookingsFlow: StateFlow<List<MyBooking>> by lazy {
        bookingsRepository.observeMyBookings(realUserId).stateIn(appScope, SharingStarted.Eagerly, emptyList())
    }

    fun eligiblePeopleForIntent(): List<Person> = eligiblePeople(intent.activity)

    /** Real, Firestore-backed bookings for the signed-in user — unlike [groups], which is the
     *  mock/local "start an activity" concept, this reflects actual paid reservations (including
     *  solo bookings that never touch the Group system at all). */
    fun observeMyBookings(): Flow<List<MyBooking>> = myBookingsFlow

    /** ALL real groups for [activity] — not scoped to the current user, unlike [loadPersistedGroups]
     *  (which only restores groups the caller is a participant of). Used to check whether a real
     *  group already exists for a specific session, or elsewhere the same day, instead of showing
     *  fabricated "X going" numbers. */
    suspend fun groupsForActivity(activity: String = intent.activity): List<Group> =
        groupRepository.groupsForActivity(activity)

    /** First real (non-cancelled) Group found across [HOME_CANDIDATE_ACTIVITIES] — real group
     *  data (members, status, venue/time) for Home's "People & groups doing this" card, instead
     *  of a Session dressed up with a fabricated "X going" count. */
    suspend fun recommendedRealGroup(): Group? {
        for (activity in HOME_CANDIDATE_ACTIVITIES) {
            val group = groupsForActivity(activity).firstOrNull { it.effectiveStatus != GroupStatus.CANCELLED }
            if (group != null) return group
        }
        return null
    }

    suspend fun nearbySession(excludeActivity: String?): Pair<String, Session>? {
        val sessions = sessionRepository.sessionsForActivities(HOME_CANDIDATE_ACTIVITIES, location.radiusKm, location.latitude, location.longitude).filterNotPassed()
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
     * onto a venue — it always carries its own independently-generated date/time. When nothing
     * real matches, [selectedSession] is left null rather than fabricating a placeholder booking
     * — callers (SessionDetailScreen) render an honest "nothing available" state instead.
     */
    suspend fun selectSession(venueName: String? = null) {
        val sessions = sessionsFor(intent.activity)
        selectedSession = if (venueName != null) sessions.firstOrNull { it.venueName == venueName } else sessions.firstOrNull()
    }

    /** Assigns the exact Session the user picked in VenueSessions — unlike [selectSession], which
     *  resolves a venue name to its FIRST matching session, this is used once the specific
     *  date/time slot (not just the venue) has already been chosen. */
    fun selectExactSession(session: Session) {
        selectedSession = session
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
            priceIfDeadlineMissed = increasedPrice(info.price),
            creatorPhotoUrl = currentUser.photoUrl,
            creatorUid = Firebase.auth.currentUser?.uid
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
        g.sessionDate = s.date
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
    fun joinGroup(g: Group): Group = groupRepository.addMember(g, currentUser.name, Firebase.auth.currentUser?.uid)

    suspend fun joinExistingGroup(): Group? {
        val sessions = sessionsFor(intent.activity)
        val sess = sessions.firstOrNull() ?: return null
        val info = activityInfo(intent.activity)
        val g = groupRepository.joinExistingGroup(
            intent = intent,
            location = location,
            session = sess,
            info = info,
            priceIfDeadlineMissed = increasedPrice(sess.price),
            joinerUid = Firebase.auth.currentUser?.uid
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
    /** What `confirmPayment()` leaves the caller to do next — a real checkout needs to actually
     *  launch the native Razorpay SDK, which only a @Composable screen can do (see
     *  [com.maxtime.app.data.payment.rememberRazorpayCheckoutLauncher]), so AppState can't just
     *  run the whole flow itself the way the old mock-only version did. */
    sealed interface ConfirmPaymentStep {
        data class ReadyForCheckout(val bookingId: String, val order: RazorpayOrderInfo) : ConfirmPaymentStep
        data class Rejected(val message: String) : ConfirmPaymentStep
        /** No real slot to reserve (e.g. a group still "searching") — the existing local-only
         *  mock confirmation already ran; nothing further for the caller to do. */
        object Confirmed : ConfirmPaymentStep
    }

    /**
     * Reserves the real slot via createBookingHold (the only thing allowed to decrement
     * InventorySlot.remaining — firestore.rules denies direct client writes), then creates a real
     * Razorpay order for it. Returns [ConfirmPaymentStep.Rejected] (with [bookingHoldError] set)
     * if either step is rejected — e.g. someone else took the last court first — so the caller
     * knows to stay on the review screen rather than navigating to a false success.
     */
    suspend fun confirmPayment(): ConfirmPaymentStep {
        bookingHoldError = null
        paymentFailed = false
        val g = selectedGroup
        val slotId = g?.sessionId ?: selectedSession?.id
        if (slotId == null) {
            // No real slot resolved yet (e.g. a group still "searching") — nothing to hold
            // against; fall back to the local-only confirmation so unresolved-venue groups
            // aren't blocked on a slot that doesn't exist yet.
            confirmPaymentLocalOnly(g)
            return ConfirmPaymentStep.Confirmed
        }

        val participants = g?.memberList?.map { ParticipantPayload(displayName = it.personName) }?.ifEmpty { null }
            ?: listOf(ParticipantPayload(userId = realUserId, displayName = currentUser.name))
        val idempotencyKey = g?.id ?: "solo-$slotId-$realUserId"

        val holdOutcome = bookingFunctions.createBookingHold(slotId, participants, idempotencyKey)
        val held = when (holdOutcome) {
            is BookingHoldOutcome.Held -> holdOutcome.result
            is BookingHoldOutcome.Rejected -> {
                bookingHoldError = holdOutcome.message
                paymentFailed = true
                return ConfirmPaymentStep.Rejected(holdOutcome.message)
            }
        }
        sessionRepository.invalidateCache()

        return when (val orderOutcome = paymentFunctions.createRazorpayOrder(held.bookingId)) {
            is CreateOrderOutcome.Ready -> ConfirmPaymentStep.ReadyForCheckout(held.bookingId, orderOutcome.order)
            is CreateOrderOutcome.Rejected -> {
                bookingHoldError = orderOutcome.message
                paymentFailed = true
                ConfirmPaymentStep.Rejected(orderOutcome.message)
            }
        }
    }

    /**
     * Called after the native Razorpay checkout UI returns. Never trusts a [RazorpayCheckoutResult.Success]
     * directly — waits for the real, webhook-verified booking status (see
     * [BookingFunctions.waitForConfirmation]) before running the local Group bookkeeping and
     * reporting success, matching this codebase's rule throughout: only a confirmed server-side
     * state change counts, never the client's own read of what just happened.
     */
    suspend fun finalizeCheckout(bookingId: String, result: RazorpayCheckoutResult): Boolean {
        if (result is RazorpayCheckoutResult.Failure) {
            bookingHoldError = result.description
            paymentFailed = true
            return false
        }
        val confirmed = bookingFunctions.waitForConfirmation(bookingId)
        if (confirmed) {
            confirmPaymentLocalOnly(selectedGroup)
        } else {
            bookingHoldError = "We couldn't confirm your payment yet — check My Bookings shortly, " +
                "or contact support if the amount was deducted."
            paymentFailed = true
        }
        return confirmed
    }

    /** The pre-existing mock booking/payment bookkeeping — only meaningful when there's a real
     *  Group to update (a pure solo booking has no Group, nothing local left to do once the real
     *  hold above already succeeded). */
    private fun confirmPaymentLocalOnly(g: Group?) {
        if (g == null) return
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

    fun confirmHigherPrice() {
        selectedGroup?.let {
            it.priceNormal = it.priceIfDeadlineMissed
            it.paymentStatus = PaymentStatus.PENDING
            groupRepository.save(it)
        }
    }

    /** Clears the fields visibly tied to who's signed in, so a different account signing in on
     *  this same device afterward doesn't briefly see the outgoing user's name/interests before
     *  its own profile loads. Deliberately NOT a full reset of every field (groups/invites/etc.
     *  reload from their own repositories once someone's actually signed back in) — just what
     *  would otherwise leak into the UI in the gap between sign-out and the next sign-in. */
    fun resetForSignOut() {
        currentUser = User(name = "")
        interests = DEFAULT_SELECTED_INTERESTS
        selectedGroup = null
        selectedSession = null
        selectedVenueName = null
        selectedPersonName = null
    }

    private fun increasedPrice(price: Int): Int {
        val bumped = (price * 1.35).roundToInt()
        return if (bumped == 0) 150 else bumped
    }
}
