package com.maxtime.app.data

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitWeekday
import platform.Foundation.NSDate

/** NSCalendar's `weekday` component is 1 = Sunday ... 7 = Saturday (Gregorian). `(weekday + 5)
 *  % 7` remaps that to ISO's 0 = Monday ... 6 = Sunday. */
actual fun todayWeekdayIndex(): Int {
    val weekday = NSCalendar.currentCalendar.component(NSCalendarUnitWeekday, NSDate()).toInt()
    return (weekday + 5) % 7
}

actual fun nowMinutesSinceMidnight(): Int {
    val calendar = NSCalendar.currentCalendar
    val now = NSDate()
    val hour = calendar.component(NSCalendarUnitHour, now).toInt()
    val minute = calendar.component(NSCalendarUnitMinute, now).toInt()
    return hour * 60 + minute
}

private val NS_WEEKDAY_ABBREVIATIONS = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

actual fun upcomingDayLabels(count: Int): List<String> {
    val calendar = NSCalendar.currentCalendar
    val nowReference = NSDate().timeIntervalSinceReferenceDate
    return (0 until count).map { offset ->
        val date = NSDate(timeIntervalSinceReferenceDate = nowReference + offset.toDouble() * 86400.0)
        val weekday = calendar.component(NSCalendarUnitWeekday, date).toInt()
        val dayOfMonth = calendar.component(NSCalendarUnitDay, date).toInt()
        "${NS_WEEKDAY_ABBREVIATIONS[weekday - 1]} $dayOfMonth"
    }
}
