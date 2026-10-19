package com.maxtime.app.data.firebase

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

@Serializable
private data class BlockedUserDoc(val blockedAt: Timestamp = Timestamp(0, 0))

/** A user's own block list (`users/{uid}/blocked_users/{blockedId}`) — purely client-side chat
 *  filtering, never visible to the blocked user or anyone else. Part of the same Guideline 1.2
 *  UGC safety mechanism as [ModerationFunctions.reportUser]. */
class BlockedUsersRepository {
    private fun blockedUsers(myUid: String) =
        Firebase.firestore.collection("users").document(myUid).collection("blocked_users")

    fun observeBlockedUserIds(myUid: String): Flow<Set<String>> =
        blockedUsers(myUid).snapshots.map { snapshot -> snapshot.documents.map { it.id }.toSet() }
            .catch { emit(emptySet()) }

    suspend fun blockUser(myUid: String, otherUserId: String) {
        try {
            blockedUsers(myUid).document(otherUserId).set(BlockedUserDoc(blockedAt = Timestamp.now()))
        } catch (_: Exception) {
            // Best-effort, same failure posture as ChatRepository.sendMessage.
        }
    }
}
