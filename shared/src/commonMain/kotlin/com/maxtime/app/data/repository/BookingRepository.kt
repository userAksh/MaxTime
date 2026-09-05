package com.maxtime.app.data.repository

import androidx.compose.runtime.mutableStateMapOf
import com.maxtime.app.data.Booking
import com.maxtime.app.data.BookingState

/** One booking per group. [createOrGet] is idempotent on groupId so a retried confirm-payment
 *  call never spawns a second booking for the same group. */
interface BookingRepository {
    val bookings: MutableMap<String, Booking>

    fun createOrGet(groupId: String, sessionId: String?, userId: String, amount: Int): Booking
    fun updateState(groupId: String, state: BookingState): Booking?
}

class MockBookingRepository : BookingRepository {
    override val bookings: MutableMap<String, Booking> = mutableStateMapOf()

    override fun createOrGet(groupId: String, sessionId: String?, userId: String, amount: Int): Booking {
        bookings[groupId]?.let { return it }
        val booking = Booking(
            id = "bk-$groupId",
            groupId = groupId,
            sessionId = sessionId,
            userId = userId,
            amount = amount,
            state = BookingState.INITIATED
        )
        bookings[groupId] = booking
        return booking
    }

    override fun updateState(groupId: String, state: BookingState): Booking? {
        val current = bookings[groupId] ?: return null
        val updated = current.copy(state = state)
        bookings[groupId] = updated
        return updated
    }
}
