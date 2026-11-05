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
}
