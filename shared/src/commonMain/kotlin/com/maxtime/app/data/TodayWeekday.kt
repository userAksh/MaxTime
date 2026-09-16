package com.maxtime.app.data

/** ISO weekday index for the device's real current date: 0 = Monday ... 6 = Sunday. */
expect fun todayWeekdayIndex(): Int

/** The device's real current date as ISO `yyyy-MM-dd` — used to compute day offsets for real
 *  InventorySlot-derived Sessions, which carry ISO dates rather than weekday abbreviations. */
expect fun todayIsoDate(): String

/** Minutes since local midnight, right now on the device. */
expect fun nowMinutesSinceMidnight(): Int

/** [count] real upcoming dates starting today, e.g. ["Sat 5", "Sun 6", "Mon 7", ...] — weekday
 *  abbreviation plus actual day-of-month, computed from the device's real calendar. Never a
 *  fabricated sequence that can drift out of sync with what day it actually is. */
expect fun upcomingDayLabels(count: Int): List<String>
