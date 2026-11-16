package com.hkm.carlauncher.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FixPolicyTest {
    private fun fix(provider: String, timeMs: Long) = Fix(25.2, 55.27, null, null, timeMs, provider)

    @Test
    fun anyFixIsTakenWhenNoneYet() {
        assertTrue(shouldUse(current = null, candidate = fix("network", 1_000), nowMs = 1_000))
    }

    @Test
    fun gpsAlwaysReplacesOtherSources() {
        assertTrue(shouldUse(fix("network", 1_000), fix("gps", 1_500), nowMs = 1_500))
    }

    @Test
    fun otherSourceDoesNotOverrideFreshGps() {
        assertFalse(shouldUse(fix("gps", 10_000), fix("network", 12_000), nowMs = 12_000))
    }

    @Test
    fun otherSourceUsedWhenGpsWentQuiet() {
        assertTrue(shouldUse(fix("gps", 10_000), fix("fused", 25_000), nowMs = 25_000))
    }

    @Test
    fun waitingTextShowsSatellites() {
        assertEquals("Waiting for GPS… 3 of 11 satellites", waitingText(GpsDiagnostics(satellitesUsed = 3, satellitesInView = 11)))
    }

    @Test
    fun waitingTextWithoutGnssData() {
        assertEquals("Waiting for GPS…", waitingText(GpsDiagnostics()))
    }

    @Test
    fun waitingTextReportsNoSatellites() {
        assertEquals(
            "Waiting for GPS… no satellites in view (check the GPS antenna or park outside)",
            waitingText(GpsDiagnostics(satellitesUsed = 0, satellitesInView = 0)),
        )
    }

    @Test
    fun waitingTextReportsMissingGpsProvider() {
        assertEquals(
            "No GPS on this unit's location service — using other sources",
            waitingText(GpsDiagnostics(providers = listOf("network", "passive"))),
        )
    }

    @Test
    fun statusReportForSettings() {
        val d = GpsDiagnostics(listOf("gps", "network"), satellitesUsed = 0, satellitesInView = 4, errors = listOf("gps: IllegalArgumentException"))
        assertEquals(
            "No position yet\nSources: gps, network\nSatellites: 0 used of 4 in view\nProblem: gps: IllegalArgumentException",
            gpsStatusText(d, LocationState.Waiting(null), nowMs = 0),
        )
        assertEquals("Position from network", gpsStatusText(GpsDiagnostics(), LocationState.Live(fix("network", 0)), nowMs = 0))
    }

    @Test
    fun statusShowsUpdatesAndLastFixAge() {
        val last = Fix(25.204812, 55.270781, null, null, timeMs = 100_000, provider = "network")
        val d = GpsDiagnostics(updates = mapOf("gps" to 0, "network" to 12), lastFix = last, lastFixAtMs = 100_000)
        val text = gpsStatusText(d, LocationState.Live(last), nowMs = 104_000)
        assertTrue(text, text.contains("Updates: gps 0, network 12"))
        assertTrue(text, text.contains("Last position: 25.20481, 55.27078 from network, 4 s ago"))
    }

    @Test
    fun statusFlagsWrongGpsClock() {
        // Cheap GNSS chips with the week-rollover bug report times ~19.6 years in the past.
        val last = Fix(25.2, 55.27, null, null, timeMs = 1_000_000_000_000, provider = "gps")
        val d = GpsDiagnostics(lastFix = last, lastFixAtMs = 1_762_000_000_000)
        val text = gpsStatusText(d, LocationState.Live(last), nowMs = 1_762_000_000_000)
        assertTrue(text, text.contains("GPS clock is wrong"))
    }

    @Test
    fun statusQuietWhenClockMatches() {
        val last = Fix(25.2, 55.27, null, null, timeMs = 1_762_000_000_000, provider = "gps")
        val d = GpsDiagnostics(lastFix = last, lastFixAtMs = 1_762_000_000_500)
        assertFalse(gpsStatusText(d, LocationState.Live(last), nowMs = 1_762_000_001_000).contains("clock"))
    }
}
