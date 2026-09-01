package com.maxtime.app.data

import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class AppStateInvariantsTest {

    // Sessions now live in real Firestore (see SessionRepository) rather than being
    // generated in-memory, so these need a Firebase Emulator Suite connection to run —
    // out of scope for the sessions-migration pass that moved them off mock data.
    @Ignore
    @Test
    fun invariant1_sessionIsNeverSynthesizedFromRequestedWhen() = runBlocking {
        val state = AppState()
        state.intent = state.intent.copy(activity = "Badminton", whenLabel = "Sat, 9 AM")
        state.location = UserLocation(area = "Saket, South Delhi", radiusKm = 10)
        state.selectSession("DLF Phase 3 Courts")

        val session = state.selectedSession!!
        // The session's own time is independent inventory, not a copy of what was requested.
        assertNotEquals(state.intent.whenLabel, session.whenLabel)
        assertEquals("DLF Phase 3 Courts", session.venueName)
    }

    @Ignore
    @Test
    fun invariant2_creatingActivityDoesNotAssignVenue() = runBlocking {
        val state = AppState()
        val group = state.getOrCreateMyGroup()

        assertNull(group.venue)
        assertNull(group.sessionId)
        assertEquals(GroupStatus.FILLING, group.status)

        state.resolveSessionForGroup(group)
        assertTrue(group.venue != null, "resolveSessionForGroup should assign a venue when sessions exist in radius")
    }

    @Test
    fun invariant3_joiningGroupNeverTouchesPaymentOrBookingStatus() {
        val state = AppState()
        state.getOrCreateMyGroup()
        val group = state.addPersonToMyGroup("Harsh")

        assertEquals(PaymentStatus.NOT_REQUESTED, group.paymentStatus)
        assertEquals(BookingStatus.UNBOOKED, group.bookingStatus)

        state.selectedGroup = group
        state.confirmPayment()
        assertEquals(PaymentStatus.PAID, group.paymentStatus)
        assertEquals(BookingStatus.CONFIRMED, group.bookingStatus)
    }

    @Test
    fun invariant4_multipleAcceptedInvitesGrowOneGroupNotSeparateGroups() {
        val state = AppState()
        state.intent = state.intent.copy(activity = "Badminton", whenLabel = "Sat, 9 AM")

        state.sendInvite("Harsh")
        state.simulateAccept("Harsh")
        val groupAfterFirst = state.selectedGroup!!

        state.sendInvite("Karan")
        state.simulateAccept("Karan")
        val groupAfterSecond = state.selectedGroup!!

        assertEquals(groupAfterFirst.id, groupAfterSecond.id, "accepted invites for the same intent must grow one group")
        assertEquals(1, state.groups.values.count { it.id == groupAfterFirst.id })
        assertTrue("Harsh" in groupAfterSecond.connections && "Karan" in groupAfterSecond.connections)
    }

    @Test
    fun invariant5_inviteCreditOnlyConsumedOnceOnFirstAccept() {
        val state = AppState()
        val startingCredits = state.freeInvitesLeft

        state.sendInvite("Harsh")
        assertEquals(startingCredits, state.freeInvitesLeft, "sending an invite must never consume a credit")

        state.simulateAccept("Harsh")
        assertEquals(startingCredits - 1, state.freeInvitesLeft, "first acceptance consumes exactly one credit")

        // Reopening / re-simulating an already-accepted invite must not double-charge.
        state.simulateAccept("Harsh")
        assertEquals(startingCredits - 1, state.freeInvitesLeft)

        state.sendInvite("Karan")
        state.simulateDecline("Karan")
        assertEquals(startingCredits - 1, state.freeInvitesLeft, "declined invites consume nothing")
    }

    @Test
    fun invariant6_peopleAreActivitySpecificAndBoxingHasZeroEligiblePeople() {
        val state = AppState()
        state.intent = state.intent.copy(activity = "Boxing")
        assertTrue(state.eligiblePeopleForIntent().isEmpty(), "Boxing is the reference empty-state case")

        state.intent = state.intent.copy(activity = "Badminton")
        assertTrue(state.eligiblePeopleForIntent().isNotEmpty())
    }

    @Ignore
    @Test
    fun invariant7_locationAndRadiusChangesPropagateLiveToSessions() = runBlocking {
        val state = AppState()
        state.intent = state.intent.copy(activity = "Badminton")
        state.location = UserLocation(area = "Saket, South Delhi", radiusKm = 2)
        val narrowResults = state.sessionsFor()

        state.location = UserLocation(area = "Saket, South Delhi", radiusKm = 10)
        val wideResults = state.sessionsFor()

        assertTrue(wideResults.size >= narrowResults.size, "widening radius must never shrink results")
        assertTrue(wideResults.size > narrowResults.size, "DLF Phase 3 Courts (6.4km) should only appear once radius expands past 2km")
    }

    @Test
    fun invariant8_backNavigationNeverImplicitlyResetsIntentOrLocation() {
        val state = AppState()
        val originalIntent = state.intent
        val originalLocation = state.location

        // Simulate wandering deep into the flow and back — nothing here should touch intent/location
        // unless a mutator explicitly meant to (getOrCreateMyGroup, etc. never do).
        state.getOrCreateMyGroup()

        assertEquals(originalIntent, state.intent)
        assertEquals(originalLocation, state.location)
    }

    @Test
    fun freeInvitesExhaustedBlocksFurtherFreeInvites() {
        val state = AppState()
        state.freeInvitesLeft = 1
        state.sendInvite("Harsh")
        state.simulateAccept("Harsh")
        assertEquals(0, state.freeInvitesLeft)

        // Further accepts must not go negative.
        state.sendInvite("Karan")
        state.simulateAccept("Karan")
        assertFalse(state.freeInvitesLeft < 0)
    }
}
