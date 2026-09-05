package com.maxtime.app.data

/** New members can no longer join once it's this close to the confirmed start time. */
private const val DEADLINE_LEAD_MINUTES = 120

/** (weekdayIndex, minutesSinceMidnight) of the fill deadline for an event at [eventStartMinutes]
 *  on [eventWeekdayIndex] — handles rolling back onto the previous day correctly (e.g. a 1:00 AM
 *  start puts the deadline at 11:00 PM the day before, not a negative time). */
private fun fillDeadline(eventWeekdayIndex: Int, eventStartMinutes: Int): Pair<Int, Int> {
    val raw = eventStartMinutes - DEADLINE_LEAD_MINUTES
    val dayShift = if (raw < 0) -1 else 0
    val minutes = ((raw % (24 * 60)) + 24 * 60) % (24 * 60)
    val weekday = ((eventWeekdayIndex + dayShift) % 7 + 7) % 7
    return weekday to minutes
}

/** "Today, 4:00 PM" style label for when new members stop being able to join. */
fun fillDeadlineLabel(eventWeekdayIndex: Int, eventStartMinutes: Int): String {
    val (weekday, minutes) = fillDeadline(eventWeekdayIndex, eventStartMinutes)
    return "${relativeDayLabelForWeekday(weekday)}, ${minutesToClockTime(minutes)}"
}

/** True once the real device clock has actually reached the fill deadline — not a manual flag,
 *  computed fresh against [todayWeekdayIndex]/[nowMinutesSinceMidnight] every time it's read. */
fun fillDeadlinePassed(eventWeekdayIndex: Int, eventStartMinutes: Int): Boolean {
    val (weekday, minutes) = fillDeadline(eventWeekdayIndex, eventStartMinutes)
    if (weekday != todayWeekdayIndex()) return false
    return nowMinutesSinceMidnight() >= minutes
}
