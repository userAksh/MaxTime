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

    /** Invariant: one group per (activity, when, location) key. */
    fun getOrCreateMyGroup(
        intent: ActivityIntent,
        location: UserLocation,
        creatorName: String,
        info: ActivityInfo,
        priceIfDeadlineMissed: Int
    ): Group

    fun joinExistingGroup(
        intent: ActivityIntent,
        location: UserLocation,
        session: Session,
        info: ActivityInfo,
        priceIfDeadlineMissed: Int
    ): Group

    /** Invariant: only ever changes membership/status — never payment or booking state. */
    fun addMember(group: Group, personName: String): Group

    /** Restores whatever groups [myName] created or joined before this app process started.
     *  A no-op for a purely in-memory implementation — there's nothing to restore from. */
    suspend fun loadMyGroups(myName: String)

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
        priceIfDeadlineMissed: Int
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
        g.memberList.add(GroupMember(creatorName, GroupMemberRole.CREATOR, "Just now"))
        groups[key] = g
        return g
    }

    override fun joinExistingGroup(
        intent: ActivityIntent,
        location: UserLocation,
        session: Session,
        info: ActivityInfo,
        priceIfDeadlineMissed: Int
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
        g.eventWeekdayIndex = weekdayIndexOf(session.date)
        g.eventStartMinutes = parseClockTimeToMinutes(session.startTime)
        g.memberList.add(GroupMember("Ayesha", GroupMemberRole.CREATOR, "Just now"))
        g.memberList.add(GroupMember("Guest", GroupMemberRole.MEMBER, "Just now"))
        g.memberList.add(GroupMember("Guest", GroupMemberRole.MEMBER, "Just now"))
        g.connections.add("Ayesha")
        groups[key] = g
        return g
    }

    override fun addMember(group: Group, personName: String): Group {
        // Enforced here, not just in the UI that hides the button — an invite accepted right at
        // the deadline shouldn't sneak someone in after joining has actually closed.
        if (group.isPastFillDeadline) return group
        if (personName !in group.connections) {
            group.connections.add(personName)
            if (group.members < group.capacity) {
                group.memberList.add(GroupMember(personName, GroupMemberRole.MEMBER, "Just now"))
            }
        }
        if (group.members >= group.capacity) group.status = GroupStatus.FULL
        return group
    }

    override suspend fun loadMyGroups(myName: String) {
        // Nothing to restore — this implementation never survives process death anyway.
    }

    override fun save(group: Group) {
        // Already live in `groups` the moment it's mutated — nothing further to do.
    }
}
