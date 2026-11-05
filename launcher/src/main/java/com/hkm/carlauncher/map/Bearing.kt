package com.hkm.carlauncher.map

import com.hkm.carlauncher.location.Fix

/** Below this speed (m/s, ~5 km/h) GPS heading is noise; many chips report 0 when stopped. */
private const val MIN_HEADING_SPEED = 1.5f

/** Keeps pointing the way the car was going while it is stopped or the heading is unknown. */
fun markerBearing(previous: Float, fix: Fix): Float {
    val moving = (fix.speedMps ?: 0f) >= MIN_HEADING_SPEED
    return fix.bearing?.takeIf { moving } ?: previous
}
