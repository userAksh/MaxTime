package com.maxtime.app.data.firebase

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import kotlinx.serialization.Serializable

@Serializable
data class UserProfileDoc(
    val name: String = "",
    val phoneNumber: String? = null,
    val interests: Map<String, List<String>> = emptyMap(),
)

class UserProfileRepository {
    private val users get() = Firebase.firestore.collection("users")

    suspend fun saveProfile(uid: String, name: String, phoneNumber: String?) {
        // Preserve any interests already saved for this uid — this only ever runs before the
        // Interests step in onboarding, but loading first keeps it safe if that order changes.
        val existing = loadProfile(uid)
        users.document(uid).set(UserProfileDoc(name = name, phoneNumber = phoneNumber, interests = existing?.interests ?: emptyMap()))
    }

    suspend fun saveInterests(uid: String, interests: Map<String, Set<String>>) {
        val existing = loadProfile(uid) ?: UserProfileDoc()
        users.document(uid).set(existing.copy(interests = interests.mapValues { it.value.toList() }))
    }

    suspend fun loadProfile(uid: String): UserProfileDoc? {
        val snapshot = users.document(uid).get()
        return if (snapshot.exists) snapshot.data<UserProfileDoc>() else null
    }
}
