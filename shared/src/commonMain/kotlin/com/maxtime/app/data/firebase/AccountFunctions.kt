package com.maxtime.app.data.firebase

import com.maxtime.app.analytics.AnalyticsTracker
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.functions.functions
import kotlinx.serialization.Serializable

/** Thin client for deleteMyAccount (functions/src/account.ts) — required by Apple App Review
 *  Guideline 5.1.1(v) for any app that supports account creation. All it does is call the
 *  function; the caller is responsible for confirming with the user first (this is irreversible)
 *  and for clearing local session state afterwards, same as AuthRepository.signOut(). */
@Serializable
private data class DeleteMyAccountRequest(val appVersion: String)

@Serializable
private data class DeleteMyAccountResult(val deleted: Boolean)

class AccountFunctions {
    suspend fun deleteMyAccount() {
        Firebase.functions.httpsCallable("deleteMyAccount")
            .invoke(DeleteMyAccountRequest(AnalyticsTracker.appVersion))
            .data<DeleteMyAccountResult>()
    }
}
