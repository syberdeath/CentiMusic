package com.hkm.carlauncher.map

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class MapSourceTest {
    private val file = File("/data/x/uae.pmtiles")

    @Test
    fun offlineFileWinsEvenWhenOnline() {
        assertEquals(MapSource.Offline(file), selectMapSource(file, online = true))
        assertEquals(MapSource.Offline(file), selectMapSource(file, online = false))
    }

    @Test
    fun onlineWithoutFile() {
        assertEquals(MapSource.Online, selectMapSource(null, online = true))
    }

    @Test
    fun unavailableWithNeither() {
        assertEquals(MapSource.Unavailable, selectMapSource(null, online = false))
    }
}
