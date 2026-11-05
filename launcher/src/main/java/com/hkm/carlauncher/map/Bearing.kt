package com.hkm.carlauncher.map

import com.hkm.carlauncher.location.Fix

/** GPS gives no heading when the car is stopped; keep pointing the way it was going. */
fun markerBearing(previous: Float, fix: Fix): Float = fix.bearing ?: previous
