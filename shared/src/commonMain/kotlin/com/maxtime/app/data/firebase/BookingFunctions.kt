package com.maxtime.app.data.firebase

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.functions.FirebaseFunctionsException
import dev.gitlive.firebase.functions.functions
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable

@Serializable
data class ParticipantPayload(val userId: String? = null, val displayName: String)

@Serializable
private data class CreateBookingHoldRequest(
    val slotId: String,
    val participants: List<ParticipantPayload>,
    val idempotencyKey: String,
)

@Serializable
data class BookingHoldResult(
    val bookingId: String,
    val alreadyExisted: Boolean = false,
    val amount: Int = 0,
    val holdExpiresAtMillis: Long = 0,
)

sealed interface BookingHoldOutcome {
    data class Held(val result: BookingHoldResult) : BookingHoldOutcome
    data class Rejected(val message: String) : BookingHoldOutcome
}

@Serializable
private data class BookingStatusDoc(val status: String = "")

/**
 * Calls the real `createBookingHold` Cloud Function (functions/src/booking.ts) — the only thing
 * that's ever allowed to decrement `InventorySlot.remaining` (firestore.rules denies direct
 * client writes to inventory_slots/bookings). Runs inside a Firestore transaction server-side, so
 * two customers racing for the same last court can never both succeed.
 */
class BookingFunctions {
    suspend fun createBookingHold(
        slotId: String,
        participants: List<ParticipantPayload>,
        idempotencyKey: String,
    ): BookingHoldOutcome =
        try {
            val result = Firebase.functions
                .httpsCallable("createBookingHold")
                .invoke(CreateBookingHoldRequest(slotId, participants, idempotencyKey))
                .data<BookingHoldResult>()
            BookingHoldOutcome.Held(result)
        } catch (e: FirebaseFunctionsException) {
            BookingHoldOutcome.Rejected(e.message ?: "This slot could not be booked.")
        } catch (e: Exception) {
            BookingHoldOutcome.Rejected("Something went wrong — please try again.")
        }

    /**
     * Never trust the checkout SDK's own success callback alone — the real confirmation is
     * whatever `paymentWebhook` (verified against Razorpay's signature) writes to this booking's
     * `status`. Returns true only once that write has actually landed as CONFIRMED; false on a
     * real PAYMENT_FAILED or if neither arrives within [timeoutMillis] (webhook delivery delay,
     * or a genuinely stuck payment — either way, false here means "don't claim success").
     */
    suspend fun waitForConfirmation(bookingId: String, timeoutMillis: Long = 20_000): Boolean =
        withTimeoutOrNull(timeoutMillis) {
            Firebase.firestore.collection("bookings").document(bookingId).snapshots
                .map { it.data<BookingStatusDoc>().status }
                .first { it == "CONFIRMED" || it == "PAYMENT_FAILED" }
        } == "CONFIRMED"
}
