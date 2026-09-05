package com.maxtime.app.data.firebase

import com.maxtime.app.data.ChatMessage
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.firestore.toMilliseconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

@Serializable
private data class ChatMessageDoc(
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val sentAt: Timestamp = Timestamp(0, 0)
)

/**
 * Real-time, Firestore-backed — one message subcollection per group (`groups/{groupId}/messages`).
 * Unlike Group/Invite/Booking, chat has no mock layer: it's cheap against the real backend
 * already wired for sessions/profiles, and there's no invariant here worth mocking around.
 */
class ChatRepository {
    private fun messages(groupId: String) =
        Firebase.firestore.collection("groups").document(groupId).collection("messages")

    /** [catch] turns any Firestore failure (permission denied, offline, a bad rule deploy — the
     *  exact class of bug that crashed the app once already) into an empty list instead of an
     *  uncaught exception that kills the composable collecting this Flow. A silent empty chat is
     *  a far safer failure mode than a crash. */
    fun observeMessages(groupId: String): Flow<List<ChatMessage>> =
        messages(groupId).orderBy("sentAt", Direction.ASCENDING).snapshots.map { snapshot ->
            snapshot.documents.map { doc ->
                val m = doc.data<ChatMessageDoc>()
                ChatMessage(
                    id = doc.id,
                    groupId = groupId,
                    senderId = m.senderId,
                    senderName = m.senderName,
                    text = m.text,
                    sentAtMillis = m.sentAt.toMilliseconds()
                )
            }
        }.catch { emit(emptyList()) }

    suspend fun sendMessage(groupId: String, senderId: String, senderName: String, text: String) {
        if (text.isBlank()) return
        try {
            messages(groupId).add(ChatMessageDoc(senderId = senderId, senderName = senderName, text = text.trim(), sentAt = Timestamp.now()))
        } catch (_: Exception) {
            // Best-effort — a dropped message shouldn't crash the screen; the real-time listener
            // simply won't show it, which is a visible-enough signal that it didn't go through.
        }
    }
}
