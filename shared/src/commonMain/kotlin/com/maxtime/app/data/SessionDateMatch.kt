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
    if (sessionIdx != -1) {
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

    // Real InventorySlot-derived Sessions carry an ISO yyyy-MM-dd date instead of a bare weekday
    // abbreviation — same day-phrase matching, just computed from the real calendar date rather
    // than a recurring weekday index (needed since real dates are absolute, e.g. "This weekend"
    // means one specific upcoming Sat/Sun, not any Saturday ever).
    val (y, mo, d) = parseIsoDate(sessionDate) ?: return null
    val offset = isoDateOffsetFromToday(sessionDate) ?: return null
    val relativeLabel = formatSessionDate(sessionDate)
    val isoWeekday = isoWeekdayFromEpochDay(daysFromCivil(y, mo, d))
    val phrase = dayPhraseOf(whenLabel)
    val matchesRequest = when {
        phrase.startsWith("Today") || phrase == "Tonight" -> offset == 0
        phrase == "Tomorrow" -> offset == 1
        phrase == "This weekend" -> isoWeekday == 5 || isoWeekday == 6
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
    if (sessionIdx != -1) {
        if (sessionIdx != todayWeekdayIndex()) return false
        val startMinutes = parseClockTimeToMinutes(startTime) ?: return false
        return nowMinutesSinceMidnight() >= startMinutes
    }

    // Real InventorySlot-derived Sessions carry an ISO yyyy-MM-dd date instead — generation only
    // ever produces slots from today forward, but "today" can still have already-passed morning
    // slots by the time this is evaluated, and without this branch they'd wrongly stay bookable.
    val offset = isoDateOffsetFromToday(sessionDate) ?: return false
    if (offset < 0) return true
    if (offset > 0) return false
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
 *  from actual sessions rather than a fixed list) — unparseable strings sort last. Accepts both
 *  the mock vocabulary's 12h "9:00 AM" and real `InventorySlot.startTime`'s bare 24h "07:00". */
fun parseClockTimeToMinutes(time: String): Int? {
    val trimmed = time.trim()
    val parts = trimmed.split(" ")
    if (parts.size == 1) {
        val hm = parts[0].split(":")
        if (hm.size != 2) return null
        val hour = hm[0].toIntOrNull() ?: return null
        val minute = hm[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }
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

/** Normalizes a real `InventorySlot.startTime` ("07:00", 24h) to the mock vocabulary's display
 *  format ("7:00 AM") — already-12h strings pass through unchanged so mock Sessions render
 *  exactly as before. Falls back to the raw string if it's neither shape. */
fun formatSessionTime(time: String): String {
    val trimmed = time.trim()
    if (trimmed.contains(" ")) return trimmed // already "9:00 AM"-style
    val hm = trimmed.split(":")
    if (hm.size != 2) return trimmed
    val hour = hm[0].toIntOrNull() ?: return trimmed
    val minute = hm[1].toIntOrNull() ?: return trimmed
    if (hour !in 0..23 || minute !in 0..59) return trimmed
    val suffix = if (hour >= 12) "PM" else "AM"
    val hour12 = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return "$hour12:${minute.toString().padStart(2, '0')} $suffix"
}

private val MONTH_ABBREVIATIONS =
    listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

private val ISO_DATE_REGEX = Regex("""^(\d{4})-(\d{2})-(\d{2})$""")

/** Days since the Unix epoch for a proleptic-Gregorian y/m/d, via Howard Hinnant's
 *  civil_from_days/days_from_civil algorithm — pure integer math, no platform date API needed
 *  (the project has no kotlinx-datetime dependency). Valid for any real calendar date. */
private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
    val y = if (month <= 2) year - 1 else year
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = y - era * 400
    val mp = (month + 9) % 12
    val doy = (153 * mp + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146097L + doe - 719468L
}

/** ISO weekday index (0 = Monday ... 6 = Sunday) for a days-since-epoch value — 1970-01-01 was a
 *  Thursday, i.e. ISO index 3. */
private fun isoWeekdayFromEpochDay(epochDay: Long): Int = (((epochDay + 3) % 7 + 7) % 7).toInt()

private fun parseIsoDate(date: String): Triple<Int, Int, Int>? {
    val m = ISO_DATE_REGEX.matchEntire(date.trim()) ?: return null
    val (y, mo, d) = m.destructured
    return Triple(y.toInt(), mo.toInt(), d.toInt())
}

/** Day offset from today for an ISO `yyyy-MM-dd` date (0 = today, 1 = tomorrow, negative = past),
 *  or null if [date] isn't in that format. */
fun isoDateOffsetFromToday(date: String): Int? {
    val (y, mo, d) = parseIsoDate(date) ?: return null
    val (ty, tmo, td) = parseIsoDate(todayIsoDate()) ?: return null
    return (daysFromCivil(y, mo, d) - daysFromCivil(ty, tmo, td)).toInt()
}

/**
 * Human-friendly label for a Session's `date`, handling BOTH formats real data can carry: bare
 * weekday abbreviations from mock `sessions` docs (delegates to [relativeDayLabel]) and real
 * `InventorySlot`-derived ISO `yyyy-MM-dd` dates ("Today" / "Tomorrow" / "Thu, Sep 18"). Falls
 * back to the raw string if neither shape matches.
 */
fun formatSessionDate(date: String): String {
    relativeDayLabel(date)?.let { return it }
    val offset = isoDateOffsetFromToday(date) ?: return date
    return when (offset) {
        0 -> "Today"
        1 -> "Tomorrow"
        else -> {
            val (y, mo, d) = parseIsoDate(date) ?: return date
            val weekday = isoWeekdayFromEpochDay(daysFromCivil(y, mo, d))
            "${WEEKDAY_ABBREVIATIONS_ISO[weekday]}, ${MONTH_ABBREVIATIONS[mo - 1]} $d"
        }
    }
}

private val WEEKDAY_ABBREVIATIONS_ISO = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/** Chronological sort key for a Session regardless of source format (bare weekday abbreviation
 *  from mock data, or ISO date from real inventory) — day offset from today × 1440 + minutes
 *  since midnight. Unparseable dates/times sort last within their group. */
fun sessionChronoKey(session: Session): Long {
    val dayOffset = isoDateOffsetFromToday(session.date)
        ?: weekdayIndexOf(session.date)?.let { (it - todayWeekdayIndex() + 7) % 7 }
        ?: Int.MAX_VALUE
    val minutes = parseClockTimeToMinutes(session.startTime) ?: Int.MAX_VALUE
    return dayOffset.toLong() * 1440L + minutes
}
