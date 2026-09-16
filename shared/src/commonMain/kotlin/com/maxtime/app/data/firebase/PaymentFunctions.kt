package com.maxtime.app.data.firebase

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.functions.FirebaseFunctionsException
import dev.gitlive.firebase.functions.functions
import kotlinx.serialization.Serializable

@Serializable
data class RazorpayOrderInfo(
    val orderId: String,
    val amount: Int,
    val currency: String,
    val keyId: String,
)

sealed interface CreateOrderOutcome {
    data class Ready(val order: RazorpayOrderInfo) : CreateOrderOutcome
    data class Rejected(val message: String) : CreateOrderOutcome
}

@Serializable
private data class CreateRazorpayOrderRequest(val bookingId: String)

/** Calls the real `createRazorpayOrder` Cloud Function (functions/src/razorpay.ts) — the only
 *  place the Razorpay key secret is ever used; the client only ever receives the key id. */
class PaymentFunctions {
    suspend fun createRazorpayOrder(bookingId: String): CreateOrderOutcome =
        try {
            val order = Firebase.functions
                .httpsCallable("createRazorpayOrder")
                .invoke(CreateRazorpayOrderRequest(bookingId))
                .data<RazorpayOrderInfo>()
            CreateOrderOutcome.Ready(order)
        } catch (e: FirebaseFunctionsException) {
            CreateOrderOutcome.Rejected(e.message ?: "Could not start payment for this booking.")
        } catch (e: Exception) {
            CreateOrderOutcome.Rejected("Something went wrong — please try again.")
        }
}
