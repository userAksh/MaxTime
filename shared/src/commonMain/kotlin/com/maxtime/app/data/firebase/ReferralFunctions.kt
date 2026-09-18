package com.maxtime.app.data.firebase

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.functions.FirebaseFunctionsException
import dev.gitlive.firebase.functions.functions
import kotlinx.serialization.Serializable

/**
 * Thin client for the referral Cloud Functions (functions/src/referral.ts). All referral business
 * logic — attribution rules, fraud checks, qualification, the reward state machine, admin-
 * configurable percentages/caps — lives server-side and is considered frozen; this class only
 * calls it and shapes the response for the UI. Never duplicate any of that logic here.
 */

@Serializable
data class ReferralCodeInfo(
    val code: String,
    val refereeDiscountPercent: Int,
    val refereeDiscountCapAmount: Int,
    val referrerDiscountPercent: Int,
    val referrerDiscountCapAmount: Int,
)

@Serializable
private data class RedeemReferralRequest(val code: String)

@Serializable
data class RedeemReferralResult(
    val referralId: String,
    val alreadyAttributed: Boolean,
    // Only present when alreadyAttributed == false — a repeat/already-attributed call doesn't
    // re-send them, matching redeemReferralCode's actual response shape server-side.
    val refereeDiscountPercent: Int? = null,
    val refereeDiscountCapAmount: Int? = null,
    // Snapshotted server-side from the same config read as the referee fields above — the caller
    // shouldn't need a separate, earlier generateMyReferralCode() call to have already succeeded
    // just to know the referrer's own percentage.
    val referrerDiscountPercent: Int? = null,
    val referrerDiscountCapAmount: Int? = null,
)

/** Mirrors the BookingHoldOutcome/CreateOrderOutcome sealed-result pattern already used by
 *  BookingFunctions.kt/PaymentFunctions.kt — a validation rejection (bad code, self-referral,
 *  rate-limited, account too old) is an expected, displayable outcome, not an app-level error. */
sealed interface RedeemReferralOutcome {
    data class Redeemed(val result: RedeemReferralResult) : RedeemReferralOutcome
    data class Rejected(val message: String) : RedeemReferralOutcome
}

@Serializable
data class ReferralRewardSummary(
    val status: String,
    val discountPercent: Int? = null,
    val discountCapAmount: Int? = null,
)

@Serializable
data class ReferralAsReferrerEntry(
    val referredUserId: String,
    val status: String,
    val rewardStatus: String? = null,
)

@Serializable
data class ReferralAsRefereeInfo(
    val referrerUserId: String,
    val code: String,
    val status: String,
    val refereeReward: ReferralRewardSummary? = null,
)

@Serializable
data class ReferralSummary(
    val myCode: String? = null,
    val asReferrer: List<ReferralAsReferrerEntry> = emptyList(),
    val asReferee: ReferralAsRefereeInfo? = null,
)

class ReferralFunctions {
    suspend fun generateMyReferralCode(): ReferralCodeInfo =
        Firebase.functions.httpsCallable("generateMyReferralCode").invoke().data()

    /** Cloud Function messages here (unlike raw Firebase Auth provider exceptions elsewhere in
     *  this app) are already developer-authored, user-appropriate strings — see
     *  functions/src/referral.ts's HttpsError calls — safe to show directly, no
     *  friendlyErrorMessage filtering needed. */
    suspend fun redeemReferralCode(code: String): RedeemReferralOutcome =
        try {
            val result = Firebase.functions
                .httpsCallable("redeemReferralCode")
                .invoke(RedeemReferralRequest(code))
                .data<RedeemReferralResult>()
            RedeemReferralOutcome.Redeemed(result)
        } catch (e: FirebaseFunctionsException) {
            RedeemReferralOutcome.Rejected(e.message ?: "That referral code isn't valid.")
        } catch (e: Exception) {
            RedeemReferralOutcome.Rejected("Something went wrong. Please try again.")
        }

    suspend fun getMyReferralSummary(): ReferralSummary =
        Firebase.functions.httpsCallable("getMyReferralSummary").invoke().data()
}
