package com.maxtime.app.data.repository

import androidx.compose.runtime.mutableStateMapOf
import com.maxtime.app.data.ActivityInfo
import com.maxtime.app.data.ActivityIntent
import com.maxtime.app.data.Group
import com.maxtime.app.data.GroupMember
import com.maxtime.app.data.GroupMemberRole
import com.maxtime.app.data.GroupStatus
import com.maxtime.app.data.Session
import com.maxtime.app.data.UserLocation
import com.maxtime.app.data.parseClockTimeToMinutes
import com.maxtime.app.data.weekdayIndexOf

/** Swappable behind this interface — MockGroupRepository today, a Firestore-backed one later,
 *  with no change required on the AppState/UI side. */
interface GroupRepository {
    val groups: MutableMap<String, Group>

    /** Invariant: one group per (activity, when, location) key. [creatorUid] is the real Firebase
     *  Auth UID when known — see [Group.memberUids]'s doc comment for why. */
    fun getOrCreateMyGroup(
        intent: ActivityIntent,
        location: UserLocation,
        creatorName: String,
        info: ActivityInfo,
        priceIfDeadlineMissed: Int,
        creatorPhotoUrl: String? = null,
        creatorUid: String? = null
    ): Group

    /** [joinerUid] — the real signed-in user who tapped into this unclaimed session — is tagged
     *  into [Group.memberUids] even though the demo-seeded members ("Ayesha", "Guest") aren't,
     *  so the real caller isn't locked out of their own group's chat/updates by the UID-based
     *  firestore.rules membership check. Does not add them to the visible member list/count —
     *  that cosmetic (fabricated membership) is a separate, pre-existing issue, not in scope here. */
    fun joinExistingGroup(
        intent: ActivityIntent,
        location: UserLocation,
        session: Session,
        info: ActivityInfo,
        priceIfDeadlineMissed: Int,
        joinerUid: String? = null
    ): Group

    /** Invariant: only ever changes membership/status — never payment or booking state.
     *  [personUid] is the real Firebase Auth UID when known (see [Group.memberUids]) — left null
     *  for invite-by-name/simulated members, where no real UID exists to record. */
    fun addMember(group: Group, personName: String, personUid: String? = null): Group

    /** Restores whatever groups [myName] created or joined before this app process started.
     *  A no-op for a purely in-memory implementation — there's nothing to restore from. */
    suspend fun loadMyGroups(myName: String)

    /** ALL groups for [activity], regardless of who created or joined them — unlike [loadMyGroups],
     *  which only ever returns the caller's own. Used to check real "is anyone actually doing this"
     *  signals rather than fabricating them. */
    suspend fun groupsForActivity(activity: String): List<Group>

    /** Persists a group's current state after AppState mutates fields the repository itself
     *  didn't touch (payment, booking, venue resolution, deadline-driven status changes) — a
     *  no-op for a purely in-memory implementation, since the object is already live in [groups]. */
    fun save(group: Group)
}

class MockGroupRepository : GroupRepository {
    override val groups: MutableMap<String, Group> = mutableStateMapOf()

    override fun getOrCreateMyGroup(
        intent: ActivityIntent,
        location: UserLocation,
        creatorName: String,
        info: ActivityInfo,
        priceIfDeadlineMissed: Int,
        creatorPhotoUrl: String?,
        creatorUid: String?
    ): Group {
        val key = "my-${intent.activity}-${intent.whenLabel}-${location.area}"
        groups[key]?.let { return it }
        val g = Group(
            id = key,
            activity = intent.activity,
            whenLabel = intent.whenLabel,
            where = location.area,
            venue = null,
            sessionId = null,
            needed = intent.peopleNeeded,
            capacity = 1 + intent.peopleNeeded,
            creator = creatorName,
            priceNormal = info.price,
            priceIfDeadlineMissed = priceIfDeadlineMissed,
            icon = info.icon
        )
        g.memberList.add(GroupMember(creatorName, GroupMemberRole.CREATOR, "Just now", creatorPhotoUrl))
        if (creatorUid != null) g.memberUids.add(creatorUid)
        groups[key] = g
        return g
    }

    override fun joinExistingGroup(
        intent: ActivityIntent,
        location: UserLocation,
        session: Session,
        info: ActivityInfo,
        priceIfDeadlineMissed: Int,
        joinerUid: String?
    ): Group {
        val key = "open-${intent.activity}-${session.id}"
        groups[key]?.let { return it }
        val g = Group(
            id = key,
            activity = intent.activity,
            whenLabel = session.whenLabel,
            where = location.area,
            venue = session.venueName,
            sessionId = session.id,
            needed = 1,
            capacity = 4,
            creator = "Ayesha",
            priceNormal = session.price,
            priceIfDeadlineMissed = priceIfDeadlineMissed,
            icon = info.icon
        )
        g.sessionDate = session.date
        g.eventWeekdayIndex = weekdayIndexOf(session.date)
        g.eventStartMinutes = parseClockTimeToMinutes(session.startTime)
        g.memberList.add(GroupMember("Ayesha", GroupMemberRole.CREATOR, "Just now"))
        g.memberList.add(GroupMember("Guest", GroupMemberRole.MEMBER, "Just now"))
        g.memberList.add(GroupMember("Guest", GroupMemberRole.MEMBER, "Just now"))
        g.connections.add("Ayesha")
        if (joinerUid != null) g.memberUids.add(joinerUid)
        groups[key] = g
        return g
    }

    override fun addMember(group: Group, personName: String, personUid: String?): Group {
        // Enforced here, not just in the UI that hides the button — an invite accepted right at
        // the deadline shouldn't sneak someone in after joining has actually closed.
        if (group.isPastFillDeadline) return group
        if (personName !in group.connections) {
            group.connections.add(personName)
            if (group.members < group.capacity) {
                group.memberList.add(GroupMember(personName, GroupMemberRole.MEMBER, "Just now"))
            }
        }
        if (personUid != null && personUid !in group.memberUids) group.memberUids.add(personUid)
        if (group.members >= group.capacity) group.status = GroupStatus.FULL
        return group
    }

    override suspend fun loadMyGroups(myName: String) {
        // Nothing to restore — this implementation never survives process death anyway.
    }

    override suspend fun groupsForActivity(activity: String): List<Group> =
        groups.values.filter { it.activity == activity }.toList()

    override fun save(group: Group) {
        // Already live in `groups` the moment it's mutated — nothing further to do.
    }
}
