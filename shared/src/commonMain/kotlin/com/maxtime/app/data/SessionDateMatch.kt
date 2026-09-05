package com.maxtime.app.data

private val WEEKDAY_ABBREVIATIONS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val WEEKDAY_NAMES = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

data class SessionDateMatch(val relativeLabel: String, val matchesRequest: Boolean)

/**
 * Compares a Session's `date` (a bare weekday abbreviation, e.g. "Sat") against what the user
 * actually asked for in `whenLabel` (one of the relative presets from FindWhenScreen — "Today,
 * evening", "Tonight", "Tomorrow", "This weekend"). Returns null when either side isn't in a
 * vocabulary we can reason about (custom picked date, unrecognized date string) — callers should
 * skip mismatch/relabeling UI rather than fabricate a claim we can't back up.
 */
fun sessionDateMatch(sessionDate: String, whenLabel: String): SessionDateMatch? {
    val sessionIdx = WEEKDAY_ABBREVIATIONS.indexOf(sessionDate)
    if (sessionIdx == -1) return null

    val relativeLabel = relativeDayLabel(sessionDate) ?: return null
    val offset = (sessionIdx - todayWeekdayIndex() + 7) % 7
    val phrase = dayPhraseOf(whenLabel)
    val matchesRequest = when {
        phrase.startsWith("Today") || phrase == "Tonight" -> offset == 0
        phrase == "Tomorrow" -> offset == 1
        phrase == "This weekend" -> sessionIdx == 5 || sessionIdx == 6
        else -> return null
    }
    return SessionDateMatch(relativeLabel, matchesRequest)
}

/**
 * Strips a specific time back off a whenLabel that may already be a combined "Day, Time" string
 * — e.g. "Tomorrow, 5:00 PM — 6:00 PM", written back onto `intent` by a prior CreateGroupScreen
 * submission — leaving just the day/period part ("Today", "Tonight", "Tomorrow", "This weekend",
 * or a custom day already picked earlier). Without this, matching a whenLabel against these exact
 * preset strings silently breaks the moment it already carries a time suffix: a stale "Tomorrow,
 * 5:00 PM — 6:00 PM" doesn't equal "Tomorrow", so day-filtering would see it as an unrecognized
 * custom date and skip filtering entirely instead of scoping to tomorrow.
 */
fun dayPhraseOf(whenLabel: String): String = when {
    whenLabel.startsWith("Today") -> "Today"
    whenLabel == "Tonight" || whenLabel.startsWith("Tonight,") -> "Tonight"
    whenLabel == "Tomorrow" || whenLabel.startsWith("Tomorrow,") -> "Tomorrow"
    whenLabel == "This weekend" || whenLabel.startsWith("This weekend,") -> "This weekend"
    else -> whenLabel.substringBefore(",").trim().ifBlank { whenLabel }
}

/** "Today"/"Tomorrow"/"This Sunday" for a bare weekday abbreviation, computed against the
 *  device's real current date — null if the abbreviation isn't recognized. */
fun relativeDayLabel(dateAbbrev: String): String? {
    val sessionIdx = WEEKDAY_ABBREVIATIONS.indexOf(dateAbbrev)
    if (sessionIdx == -1) return null
    return relativeDayLabelForWeekday(sessionIdx)
}

/** Same as [relativeDayLabel] but for a weekday already known as an ISO index (0 = Monday). */
fun relativeDayLabelForWeekday(weekdayIndex: Int): String {
    val offset = (weekdayIndex - todayWeekdayIndex() + 7) % 7
    return when (offset) {
        0 -> "Today"
        1 -> "Tomorrow"
        else -> "This ${WEEKDAY_NAMES[weekdayIndex]}"
    }
}

/**
 * True once a session's own start time is behind us — a session for "Sat, 9:00 AM" is a past
 * session the moment it's actually Saturday afternoon, regardless of what day the user asked
 * for. Unrecognized date/time strings return false (never hide a session we can't reason about).
 */
fun hasSessionPassed(sessionDate: String, startTime: String): Boolean {
    val sessionIdx = WEEKDAY_ABBREVIATIONS.indexOf(sessionDate)
    if (sessionIdx == -1 || sessionIdx != todayWeekdayIndex()) return false
    val startMinutes = parseClockTimeToMinutes(startTime) ?: return false
    return nowMinutesSinceMidnight() >= startMinutes
}

/** ISO weekday index (0 = Monday ... 6 = Sunday) for a Session's bare `date` abbreviation, or
 *  null if it's not one of the recognized abbreviations. */
fun weekdayIndexOf(dateAbbrev: String): Int? {
    val idx = WEEKDAY_ABBREVIATIONS.indexOf(dateAbbrev)
    return if (idx == -1) null else idx
}

/**
 * Which real weekdays actually satisfy `whenLabel` — e.g. "Tomorrow" only ever means one specific
 * day, never "any day this activity happens to run". Returns null for a custom picked date we
 * can't map back onto a real calendar day (same escape hatch as [sessionDateMatch]) — callers
 * should skip day-filtering in that case rather than wrongly exclude everything.
 */
fun matchingWeekdayIndices(whenLabel: String): Set<Int>? {
    val phrase = dayPhraseOf(whenLabel)
    return when {
        phrase.startsWith("Today") || phrase == "Tonight" -> setOf(todayWeekdayIndex())
        phrase == "Tomorrow" -> setOf((todayWeekdayIndex() + 1) % 7)
        phrase == "This weekend" -> setOf(5, 6)
        else -> null
    }
}

/** Exposed for sorting real startTime strings chronologically (e.g. a time-slot picker built
 *  from actual sessions rather than a fixed list) — unparseable strings sort last. */
fun parseClockTimeToMinutes(time: String): Int? {
    val parts = time.trim().split(" ")
    if (parts.size != 2) return null
    val (hourMinute, meridiem) = parts
    val hm = hourMinute.split(":")
    if (hm.size != 2) return null
    var hour = hm[0].toIntOrNull() ?: return null
    val minute = hm[1].toIntOrNull() ?: return null
    when (meridiem.uppercase()) {
        "AM" -> if (hour == 12) hour = 0
        "PM" -> if (hour != 12) hour += 12
        else -> return null
    }
    return hour * 60 + minute
}
