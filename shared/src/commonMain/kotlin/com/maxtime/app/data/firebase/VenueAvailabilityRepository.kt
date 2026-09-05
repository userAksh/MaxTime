package com.maxtime.app.data.firebase

import com.maxtime.app.data.VenueAvailability
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.firestore.where
import kotlinx.serialization.Serializable

@Serializable
private data class VenueAvailabilityDoc(
    val activity: String = "",
    val venueName: String = "",
    val distanceKm: Double = 0.0,
    val date: String = "",
    val opensAt: String = "",
    val closesAt: String = "",
    val price: Int = 0,
    val capacity: Int = 4,
)

/** Real open-hours windows per venue/activity/date — reference data, cached in memory per
 *  activity for the process lifetime, same rationale as [SessionRepository]. */
class VenueAvailabilityRepository {
    private val availability get() = Firebase.firestore.collection("venue_availability")
    private val byActivityCache = mutableMapOf<String, List<Pair<String, VenueAvailabilityDoc>>>()

    suspend fun availabilityFor(activity: String, radiusKm: Int): List<VenueAvailability> {
        val docs = byActivityCache.getOrPut(activity) {
            availability.where { "activity" equalTo activity }.get().documents.map { it.id to it.data<VenueAvailabilityDoc>() }
        }
        return docs
            .filter { (_, doc) -> doc.distanceKm <= radiusKm }
            .map { (id, doc) ->
                VenueAvailability(
                    id = id,
                    activity = doc.activity,
                    venueName = doc.venueName,
                    distanceKm = doc.distanceKm,
                    date = doc.date,
                    opensAt = doc.opensAt,
                    closesAt = doc.closesAt,
                    price = doc.price,
                    capacity = doc.capacity
                )
            }
    }
}
