package com.hkm.carlauncher.location

/** One GPS reading. [bearing] is null when the receiver didn't report one (e.g. standing still). */
data class Fix(
    val lat: Double,
    val lon: Double,
    val bearing: Float?,
    val speedMps: Float?,
    val timeMs: Long,
    /** Which location source produced it: "gps", "fused", "network" or "passive". */
    val provider: String = "gps",
)
