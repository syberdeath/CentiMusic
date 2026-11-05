package com.hkm.carlauncher.map

import java.io.File

/** Where the map's tiles come from. */
sealed interface MapSource {
    data class Offline(val file: File) : MapSource
    data object Online : MapSource
    data object Unavailable : MapSource
}

/** A verified offline file always wins (fast, works anywhere); otherwise online tiles if connected. */
fun selectMapSource(installed: File?, online: Boolean): MapSource = when {
    installed != null -> MapSource.Offline(installed)
    online -> MapSource.Online
    else -> MapSource.Unavailable
}
