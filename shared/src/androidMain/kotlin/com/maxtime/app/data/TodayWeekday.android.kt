package com.maxtime.app.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

actual fun todayWeekdayIndex(): Int = LocalDate.now().dayOfWeek.value - 1

actual fun todayIsoDate(): String = LocalDate.now().toString()

actual fun nowMinutesSinceMidnight(): Int = LocalTime.now().let { it.hour * 60 + it.minute }

actual fun upcomingDayLabels(count: Int): List<String> {
    val today = LocalDate.now()
    return (0 until count).map { offset ->
        val d = today.plusDays(offset.toLong())
        val abbrev = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
        "$abbrev ${d.dayOfMonth}"
    }
}
