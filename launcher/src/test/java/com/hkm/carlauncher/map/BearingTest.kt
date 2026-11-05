package com.hkm.carlauncher.map

import com.hkm.carlauncher.location.Fix
import org.junit.Assert.assertEquals
import org.junit.Test

class BearingTest {
    private fun fix(bearing: Float?) = Fix(25.2, 55.27, bearing, speedMps = null, timeMs = 0)

    @Test
    fun keepsLastBearingWhenLocationHasNone() {
        assertEquals(62f, markerBearing(62f, fix(null)), 0f)
    }

    @Test
    fun usesFixBearing() {
        assertEquals(90f, markerBearing(62f, fix(90f)), 0f)
    }
}
