package com.hkm.carlauncher.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreetNameTest {
    @Test
    fun prefersMajorRoad() {
        val features = listOf(RoadFeature("Al Sufouh St", "minor"), RoadFeature("Sheikh Zayed Rd", "motorway"))
        assertEquals("Sheikh Zayed Rd", pickStreetName(features))
    }

    @Test
    fun skipsBlankNames() {
        val features = listOf(RoadFeature(null, "motorway"), RoadFeature("  ", "trunk"), RoadFeature("Al Khail Rd", "primary"))
        assertEquals("Al Khail Rd", pickStreetName(features))
    }

    @Test
    fun unknownClassStillCounts() {
        assertEquals("Lane 4", pickStreetName(listOf(RoadFeature("Lane 4", null))))
    }

    @Test
    fun emptyIsNull() {
        assertNull(pickStreetName(emptyList()))
    }
}
