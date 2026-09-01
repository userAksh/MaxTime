package com.maxtime.app.data.firebase

import com.maxtime.app.data.Session
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.firestore.where
import kotlinx.serialization.Serializable

@Serializable
private data class SessionDoc(
    val activity: String = "",
    val venueName: String = "",
    val distanceKm: Double = 0.0,
    val date: String = "",
    val startTime: String = "",
    val durationMinutes: Int = 60,
    val price: Int = 0,
    val capacity: Int = 8,
    val spotsLeft: Int = 0,
    val cancellationPolicy: String = "Free cancellation up to 2 hours before start.",
)

/**
 * Caches raw (unfiltered) query results in memory, keyed by activity/activities — session
 * inventory is fixed reference data for the lifetime of the app process (not user-generated,
 * not written from the app), so re-fetching on every screen visit only adds latency and causes
 * a visible reload/reflow with no benefit. Radius filtering is applied on each call from the
 * cached docs, so changing radius never needs a new network round trip either.
 */
class SessionRepository {
    private val sessions get() = Firebase.firestore.collection("sessions")
    private val byActivityCache = mutableMapOf<String, List<Pair<String, SessionDoc>>>()
    private val byActivitiesCache = mutableMapOf<List<String>, List<Pair<String, SessionDoc>>>()

    suspend fun sessionsFor(activity: String, radiusKm: Int): List<Session> {
        val docs = byActivityCache.getOrPut(activity) {
            sessions.where { "activity" equalTo activity }.get().documents.map { it.id to it.data<SessionDoc>() }
        }
        return docs
            .filter { (_, doc) -> doc.distanceKm <= radiusKm }
            .map { (id, doc) -> doc.toSession(id) }
    }

    suspend fun sessionsForActivities(activities: List<String>, radiusKm: Int): List<Session> {
        if (activities.isEmpty()) return emptyList()
        val docs = byActivitiesCache.getOrPut(activities) {
            sessions.where { "activity" inArray activities }.get().documents.map { it.id to it.data<SessionDoc>() }
        }
        return docs
            .filter { (_, doc) -> doc.distanceKm <= radiusKm }
            .map { (id, doc) -> doc.toSession(id) }
    }

    private fun SessionDoc.toSession(id: String) = Session(
        id = id,
        venueName = venueName,
        distanceKm = distanceKm,
        activity = activity,
        date = date,
        startTime = startTime,
        durationMinutes = durationMinutes,
        price = price,
        capacity = capacity,
        spotsLeft = spotsLeft,
        cancellationPolicy = cancellationPolicy,
    )
}
