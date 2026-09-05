package com.maxtime.app.data

/** Candidate start times are generated on this granularity — matches the half-hour rhythm most
 *  court/facility bookings actually run on. */
private const val SLOT_STEP_MINUTES = 30

/**
 * Every exact (start, end) pair that genuinely fits inside real venue open-hours windows for the
 * requested duration. A window open 7:00–10:00 PM with a 90-minute request only ever yields
 * starts at 7:00, 7:30, 8:00, and 8:30 — never 9:00 (would end at 10:30, past closing) and never
 * a slot the venue didn't actually report being open for. This is the only source of truth for
 * what's selectable — a picker built from this list can never present an invalid combination.
 */
fun computeAvailableSlots(windows: List<VenueAvailability>, durationMinutes: Int): List<TimeSlotOption> =
    windows.flatMap { w ->
        val opens = parseClockTimeToMinutes(w.opensAt)
        val closes = parseClockTimeToMinutes(w.closesAt)
        if (opens == null || closes == null) {
            emptyList()
        } else {
            generateSequence(opens) { it + SLOT_STEP_MINUTES }
                .takeWhile { start -> start + durationMinutes <= closes }
                .map { start ->
                    TimeSlotOption(
                        venueName = w.venueName,
                        distanceKm = w.distanceKm,
                        date = w.date,
                        startTime = minutesToClockTime(start),
                        endTime = minutesToClockTime(start + durationMinutes),
                        price = w.price,
                        capacity = w.capacity
                    )
                }
                .toList()
        }
    }

/** Inverse of [parseClockTimeToMinutes] — "12:00 AM"/"12:00 PM" noon/midnight handled explicitly. */
fun minutesToClockTime(totalMinutes: Int): String {
    val normalized = ((totalMinutes % (24 * 60)) + 24 * 60) % (24 * 60)
    val hour24 = normalized / 60
    val minute = normalized % 60
    val meridiem = if (hour24 < 12) "AM" else "PM"
    val hour12 = if (hour24 % 12 == 0) 12 else hour24 % 12
    return "$hour12:${minute.toString().padStart(2, '0')} $meridiem"
}
