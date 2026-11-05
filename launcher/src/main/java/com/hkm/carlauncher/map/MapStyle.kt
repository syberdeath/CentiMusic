package com.hkm.carlauncher.map

const val ONLINE_TILES_URL = "https://tiles.openfreemap.org/planet"

/** Fills the bundled dark style's tile source for [source]. */
fun styleJsonFor(template: String, source: MapSource): String {
    val url = when (source) {
        is MapSource.Offline -> "pmtiles://file://${source.file.absolutePath}"
        MapSource.Online, MapSource.Unavailable -> ONLINE_TILES_URL
    }
    return template.replace("{{TILES}}", url)
}
