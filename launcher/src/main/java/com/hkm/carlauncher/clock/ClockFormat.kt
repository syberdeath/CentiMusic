package com.hkm.carlauncher.clock

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The two lines of the clock card. */
data class ClockText(val time: String, val date: String)

/** Large digits never carry AM/PM; the 12-hour form is just "2:13". */
fun formatClock(now: ZonedDateTime, is24h: Boolean, locale: Locale): ClockText {
    val time = DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm", locale)
    val date = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", locale)
    return ClockText(now.format(time), now.format(date))
}
