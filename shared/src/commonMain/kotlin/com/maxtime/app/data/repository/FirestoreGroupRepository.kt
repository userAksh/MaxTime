package com.maxtime.app.data.repository

import androidx.compose.runtime.mutableStateMapOf
import com.maxtime.app.data.ActivityInfo
import com.maxtime.app.data.ActivityIntent
import com.maxtime.app.data.Group
import com.maxtime.app.data.GroupMember
import com.maxtime.app.data.GroupMemberRole
import com.maxtime.app.data.GroupStatus
import com.maxtime.app.data.BookingStatus
import com.maxtime.app.data.PaymentStatus
import com.maxtime.app.data.Session
import com.maxtime.app.data.UserLocation
import com.maxtime.app.data.parseClockTimeToMinutes
import com.maxtime.app.data.weekdayIndexOf
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.firestore.where
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
private data class GroupMemberDoc(
    val personName: String = "",
    val role: String = GroupMemberRole.MEMBER.name,
    val joinedAt: String = "",
    val photoUrl: String? = null
)

@Serializable
private data class GroupDoc(
    val activity: String = "",
    val whenLabel: String = "",
    val where: String = "",
    val venue: String? = null,
    val sessionId: String? = null,
    val sessionDate: String? = null,
    val needed: Int = 0,
    val capacity: Int = 0,
    val creator: String = "",
    val priceNormal: Int = 0,
    val priceIfDeadlineMissed: Int = 0,
    val icon: String = "",
    val status: String = GroupStatus.FILLING.name,
    val paymentStatus: String = PaymentStatus.NOT_REQUESTED.name,
    val bookingStatus: String = BookingStatus.UNBOOKED.name,
    val eventWeekdayIndex: Int? = null,
    val eventStartMinutes: Int? = null,
    val members: List<GroupMemberDoc> = emptyList(),
    val connections: List<String> = emptyList(),
    /** Real Firebase Auth UIDs — see Group.memberUids' doc comment. What firestore.rules'
     *  groups/{groupId} membership check actually reads. */
    val memberUids: List<String> = emptyList(),
    /** Mirrors `members.map { it.personName }` — the only reason this is its own field is so
     *  Firestore can run an `array-contains` query on it directly ("groups I'm part of"). */
    val participantNames: List<String> = emptyList()
)

/**
 * Firestore-backed — groups, membership, and status actually survive an app restart, stored the
 * same place sessions/venue availability already are. In-memory `groups` stays the live, Compose-
 * reactive source of truth for the running process (unchanged from MockGroupRepository); every
 * mutation additionally gets written through to Firestore in the background, and [loadMyGroups]
 * hydrates that map back from Firestore once at app launch.
 */
class FirestoreGroupRepository : GroupRepository {
    override val groups: MutableMap<String, Group> = mutableStateMapOf()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val col get() = Firebase.firestore.collection("groups")

    /** Keyed by activity — Home re-derives its "people & groups doing this" card via
     *  [groupsForActivity] every time it's revisited (screens are fully recomposed on tab
     *  switch, see [com.maxtime.app.ui.nav.Router]), so without this every switch back to Home
     *  re-issues the same Firestore query. Invalidated on [save] since that's the one path that
     *  can change what a query for the affected group's activity would return. */
    private val groupsByActivityCache = mutableMapOf<String, List<Group>>()

    override suspend fun loadMyGroups(myName: String) {
        val docs = col.where { "participantNames" contains myName }.get().documents
        for (doc in docs) {
            if (groups.containsKey(doc.id)) continue
            groups[doc.id] = doc.data<GroupDoc>().toGroup(doc.id)
        }
    }

    override suspend fun groupsForActivity(activity: String): List<Group> =
        groupsByActivityCache.getOrPut(activity) {
            col.where { "activity" equalTo activity }.get().documents.map { it.data<GroupDoc>().toGroup(it.id) }
        }

    override fun save(group: Group) {
        groupsByActivityCache.remove(group.activity)
        scope.launch {
            try {
                col.document(group.id).set(group.toDoc())
            } catch (_: Exception) {
                // Best-effort — a dropped write just means this particular change won't survive
                // a restart; it never blocks or crashes the screen that triggered it.
            }
        }
    }

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
        save(g)
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
            sessionDate = session.date,
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
        if (joinerUid != null) g.memberUids.add(joinerUid)
        groups[key] = g
        save(g)
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
        save(group)
        return group
    }

    private fun GroupDoc.toGroup(id: String): Group {
        val g = Group(
            id = id,
            activity = activity,
            whenLabel = whenLabel,
            where = where,
            venue = venue,
            sessionId = sessionId,
            sessionDate = sessionDate,
            needed = needed,
            capacity = capacity,
            creator = creator,
            priceNormal = priceNormal,
            priceIfDeadlineMissed = priceIfDeadlineMissed,
            icon = icon
        )
        g.status = GroupStatus.valueOf(status)
        g.paymentStatus = PaymentStatus.valueOf(paymentStatus)
        g.bookingStatus = BookingStatus.valueOf(bookingStatus)
        g.eventWeekdayIndex = eventWeekdayIndex
        g.eventStartMinutes = eventStartMinutes
        members.forEach { g.memberList.add(GroupMember(it.personName, GroupMemberRole.valueOf(it.role), it.joinedAt, it.photoUrl)) }
        g.connections.addAll(connections)
        g.memberUids.addAll(memberUids)
        return g
    }

    private fun Group.toDoc(): GroupDoc = GroupDoc(
        activity = activity,
        whenLabel = whenLabel,
        where = where,
        venue = venue,
        sessionId = sessionId,
        sessionDate = sessionDate,
        needed = needed,
        capacity = capacity,
        creator = creator,
        priceNormal = priceNormal,
        priceIfDeadlineMissed = priceIfDeadlineMissed,
        icon = icon,
        status = status.name,
        paymentStatus = paymentStatus.name,
        bookingStatus = bookingStatus.name,
        eventWeekdayIndex = eventWeekdayIndex,
        eventStartMinutes = eventStartMinutes,
        members = memberList.map { GroupMemberDoc(it.personName, it.role.name, it.joinedAt, it.photoUrl) },
        connections = connections.toList(),
        memberUids = memberUids.toList(),
        participantNames = memberList.map { it.personName }
    )
}
