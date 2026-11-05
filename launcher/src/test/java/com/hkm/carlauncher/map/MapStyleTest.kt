package com.hkm.carlauncher.map

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapStyleTest {
    private val template: String =
        File(System.getProperty("launcher.dir") ?: ".", "src/main/assets/map/style-dark.json").readText()

    private fun tilesUrl(style: String) =
        JSONObject(style).getJSONObject("sources").getJSONObject("openmaptiles").getString("url")

    @Test
    fun offlineUsesLocalPmtiles() {
        val style = styleJsonFor(template, MapSource.Offline(File("/data/x/uae.pmtiles")))
        assertEquals("pmtiles://file:///data/x/uae.pmtiles", tilesUrl(style))
    }

    @Test
    fun onlineUsesOpenFreeMap() {
        assertEquals("https://tiles.openfreemap.org/planet", tilesUrl(styleJsonFor(template, MapSource.Online)))
    }

    @Test
    fun placeholderIsGoneAndJsonParses() {
        val style = styleJsonFor(template, MapSource.Online)
        assertFalse(style.contains("{{TILES}}"))
        JSONObject(style)
    }

    @Test
    fun glyphsAreBundled() {
        assertEquals("asset://map/fonts/{fontstack}/{range}.pbf", JSONObject(template).getString("glyphs"))
    }

    @Test
    fun attributionShown() {
        assertTrue(styleJsonFor(template, MapSource.Online).contains("© OpenStreetMap contributors"))
    }

    @Test
    fun usesOnlyBundledFonts() {
        val fonts = Regex("\"text-font\"\\s*:\\s*\\[([^]]*)]").findAll(template)
            .flatMap { Regex("\"([^\"]+)\"").findAll(it.groupValues[1]).map { m -> m.groupValues[1] } }.toSet()
        assertTrue(fonts.isNotEmpty())
        assertTrue(fonts.toString(), fonts.all { it in setOf("NotoSans-Regular", "NotoSans-Bold") })
    }
}
