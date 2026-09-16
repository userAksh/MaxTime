package com.maxtime.app.data.repository

import androidx.compose.runtime.mutableStateMapOf
import com.maxtime.app.data.Payment
import com.maxtime.app.data.PaymentState

/** [idempotencyKey] (the group id) makes [charge] safe to call again on a retry/double-tap —
 *  a payment already SUCCEEDED for that key is returned as-is rather than charged twice. This
 *  mock always succeeds; a real gateway swaps in behind the same interface, driven by a
 *  Cloud Functions webhook instead of the client marking success directly. */
interface PaymentRepository {
    val payments: MutableMap<String, Payment>

    fun charge(bookingId: String, amount: Int, idempotencyKey: String): Payment
}

class MockPaymentRepository : PaymentRepository {
    override val payments: MutableMap<String, Payment> = mutableStateMapOf()

    override fun charge(bookingId: String, amount: Int, idempotencyKey: String): Payment {
        payments[idempotencyKey]?.let { existing ->
            if (existing.state == PaymentState.SUCCEEDED) return existing
        }
        val payment = Payment(
            id = "pay-$idempotencyKey",
            bookingId = bookingId,
            amount = amount,
            idempotencyKey = idempotencyKey,
            state = PaymentState.SUCCEEDED
        )
        payments[idempotencyKey] = payment
        return payment
    }
}
