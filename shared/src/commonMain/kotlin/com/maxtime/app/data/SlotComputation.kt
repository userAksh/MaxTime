package com.maxtime.app.data

/** Inverse of [parseClockTimeToMinutes] — "12:00 AM"/"12:00 PM" noon/midnight handled explicitly. */
fun minutesToClockTime(totalMinutes: Int): String {
    val normalized = ((totalMinutes % (24 * 60)) + 24 * 60) % (24 * 60)
    val hour24 = normalized / 60
    val minute = normalized % 60
    val meridiem = if (hour24 < 12) "AM" else "PM"
    val hour12 = if (hour24 % 12 == 0) 12 else hour24 % 12
    return "$hour12:${minute.toString().padStart(2, '0')} $meridiem"
}
