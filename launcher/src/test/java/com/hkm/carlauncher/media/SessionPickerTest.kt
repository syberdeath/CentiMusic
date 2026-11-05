package com.hkm.carlauncher.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionPickerTest {
    @Test
    fun prefersPlaying() {
        val pausedNewer = SessionInfo("a", isPlaying = false, lastActiveMillis = 200)
        val playing = SessionInfo("b", isPlaying = true, lastActiveMillis = 100)
        assertEquals(playing, pickSession(listOf(pausedNewer, playing)))
    }

    @Test
    fun fallsBackToMostRecent() {
        val old = SessionInfo("a", false, 100)
        val recent = SessionInfo("b", false, 300)
        assertEquals(recent, pickSession(listOf(old, recent)))
    }

    @Test
    fun emptyIsNull() {
        assertNull(pickSession(emptyList()))
    }

    @Test
    fun pausedPositionIsNotExtrapolated() {
        assertEquals(0.25f, progressFraction(30_000, 0, 1f, false, 120_000, 99_999), 0.0001f)
    }

    @Test
    fun playingExtrapolates() {
        assertEquals(0.5f, progressFraction(30_000, 1_000, 1f, true, 120_000, 31_000), 0.0001f)
    }

    @Test
    fun durationUnknown() {
        assertEquals(0f, progressFraction(5_000, 0, 1f, true, -1, 6_000), 0f)
        assertEquals(0f, progressFraction(5_000, 0, 1f, true, 0, 6_000), 0f)
    }

    @Test
    fun pastEndClamps() {
        assertEquals(1f, progressFraction(200_000, 0, 1f, false, 120_000, 0), 0f)
    }

    @Test
    fun negativePositionClamps() {
        assertEquals(0f, progressFraction(-5_000, 0, 1f, false, 120_000, 0), 0f)
    }

    @Test
    fun zeroSpeedIsNotNaN() {
        val f = progressFraction(30_000, 0, 0f, true, 120_000, 50_000)
        assertEquals(0.25f, f, 0.0001f)
    }
}
