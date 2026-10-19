package com.maxtime.app.data.firebase

import com.maxtime.app.analytics.AnalyticsTracker
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.functions.functions
import kotlinx.serialization.Serializable

enum class ReportReason(val wireValue: String, val label: String) {
    SPAM("spam", "Spam"),
    HARASSMENT("harassment", "Harassment or abuse"),
    INAPPROPRIATE("inappropriate_content", "Inappropriate content"),
    OTHER("other", "Other"),
}

@Serializable
private data class ReportUserRequest(
    val groupId: String,
    val reportedUserId: String,
    val reason: String,
    val messageText: String? = null,
    val appVersion: String,
)

@Serializable
private data class ReportUserResult(val reported: Boolean)

/** Thin client for reportUser (functions/src/moderation.ts) — Apple Guideline 1.2 UGC safety
 *  requirement for apps with user-to-user chat. */
class ModerationFunctions {
    suspend fun reportUser(groupId: String, reportedUserId: String, reason: ReportReason, messageText: String? = null) {
        Firebase.functions.httpsCallable("reportUser")
            .invoke(ReportUserRequest(groupId, reportedUserId, reason.wireValue, messageText, AnalyticsTracker.appVersion))
            .data<ReportUserResult>()
    }
}
