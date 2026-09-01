package com.maxtime.app.data.firebase

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import kotlinx.serialization.Serializable

@Serializable
data class UserProfileDoc(
    val name: String = "",
    val phoneNumber: String? = null,
)

class UserProfileRepository {
    private val users get() = Firebase.firestore.collection("users")

    suspend fun saveProfile(uid: String, name: String, phoneNumber: String?) {
        users.document(uid).set(UserProfileDoc(name = name, phoneNumber = phoneNumber))
    }

    suspend fun loadProfile(uid: String): UserProfileDoc? {
        val snapshot = users.document(uid).get()
        return if (snapshot.exists) snapshot.data<UserProfileDoc>() else null
    }
}
