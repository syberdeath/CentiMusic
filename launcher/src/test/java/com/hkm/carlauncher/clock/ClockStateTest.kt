package com.hkm.carlauncher.clock

import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class ClockStateTest {
    private val dubai = ZoneId.of("Asia/Dubai")
    private fun at(day: Int, h: Int, m: Int) = ZonedDateTime.of(2026, 11, day, h, m, 0, 0, dubai)

    @Test
    fun formats24h() {
        assertEquals(
            ClockText("14:13", "Thursday, 5 November 2026"),
            formatClock(at(5, 14, 13), is24h = true, locale = Locale.ENGLISH),
        )
    }

    @Test
    fun followsTwelveHourSetting() {
        assertEquals("2:13", formatClock(at(5, 14, 13), is24h = false, locale = Locale.ENGLISH).time)
    }

    @Test
    fun dateChangesAtMidnight() {
        assertEquals("Thursday, 5 November 2026", formatClock(at(5, 23, 59), true, Locale.ENGLISH).date)
        assertEquals("Friday, 6 November 2026", formatClock(at(6, 0, 0), true, Locale.ENGLISH).date)
    }

    @Test
    fun midnightIn24hHasLeadingZero() {
        assertEquals("00:00", formatClock(at(6, 0, 0), true, Locale.ENGLISH).time)
    }
}
