package com.maxtime.app.data.firebase

import com.maxtime.app.data.media.PickedImage
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.storage.storage
import kotlinx.serialization.Serializable

@Serializable
data class UserProfileDoc(
    val name: String = "",
    val phoneNumber: String? = null,
    val dateOfBirth: String? = null,
    val photoUrl: String? = null,
    val interests: Map<String, List<String>> = emptyMap(),
)

class UserProfileRepository {
    private val users get() = Firebase.firestore.collection("users")

    suspend fun saveProfile(uid: String, name: String, phoneNumber: String?, dateOfBirth: String?) {
        // Preserve any interests/photo already saved for this uid — this only ever runs before
        // the Interests step in onboarding, but loading first keeps it safe if that order changes.
        val existing = loadProfile(uid)
        users.document(uid).set(
            UserProfileDoc(
                name = name,
                phoneNumber = phoneNumber,
                dateOfBirth = dateOfBirth,
                photoUrl = existing?.photoUrl,
                interests = existing?.interests ?: emptyMap()
            )
        )
    }

    /** Uploads to `users/{uid}/profile_photo` — storage.rules only grants read/write under the
     *  `users/{uid}` subtree for that uid, so this has to live in that namespace, not a separate
     *  top-level one. Overwrites any previous photo for this user and saves the resulting
     *  download URL onto the profile doc, returned here too so the caller can show it immediately
     *  without a second round trip through [loadProfile]. */
    suspend fun uploadProfilePhoto(uid: String, image: PickedImage): String {
        val ref = Firebase.storage.reference("users/$uid/profile_photo")
        ref.putData(image.data)
        val url = ref.getDownloadUrl()
        val existing = loadProfile(uid) ?: UserProfileDoc()
        users.document(uid).set(existing.copy(photoUrl = url))
        return url
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
