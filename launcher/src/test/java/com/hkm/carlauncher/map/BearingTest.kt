package com.hkm.carlauncher.map

import com.hkm.carlauncher.location.Fix
import org.junit.Assert.assertEquals
import org.junit.Test

class BearingTest {
    private fun fix(bearing: Float?, speed: Float? = 10f) = Fix(25.2, 55.27, bearing, speedMps = speed, timeMs = 0)

    @Test
    fun keepsLastBearingWhenLocationHasNone() {
        assertEquals(62f, markerBearing(62f, fix(null)), 0f)
    }

    @Test
    fun usesFixBearing() {
        assertEquals(90f, markerBearing(62f, fix(90f)), 0f)
    }

    @Test
    fun ignoresBearingWhenStopped() {
        // Many GNSS chips report bearing 0 (or jitter) at standstill.
        assertEquals(62f, markerBearing(62f, fix(0f, speed = 0.3f)), 0f)
    }

    @Test
    fun ignoresBearingWithoutSpeed() {
        assertEquals(62f, markerBearing(62f, fix(0f, speed = null)), 0f)
    }
}
