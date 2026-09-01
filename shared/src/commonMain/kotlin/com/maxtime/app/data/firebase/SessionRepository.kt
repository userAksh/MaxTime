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

class SessionRepository {
    private val sessions get() = Firebase.firestore.collection("sessions")

    suspend fun sessionsFor(activity: String, radiusKm: Int): List<Session> {
        val snapshot = sessions.where { "activity" equalTo activity }.get()
        return snapshot.documents
            .map { it.id to it.data<SessionDoc>() }
            .filter { (_, doc) -> doc.distanceKm <= radiusKm }
            .map { (id, doc) -> doc.toSession(id) }
    }

    suspend fun sessionsForActivities(activities: List<String>, radiusKm: Int): List<Session> {
        if (activities.isEmpty()) return emptyList()
        val snapshot = sessions.where { "activity" inArray activities }.get()
        return snapshot.documents
            .map { it.id to it.data<SessionDoc>() }
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
