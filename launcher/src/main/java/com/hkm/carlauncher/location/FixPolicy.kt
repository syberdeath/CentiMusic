package com.hkm.carlauncher.location

/** After this long without a GPS fix, other sources (fused, network, passive) may move the car. */
private const val GPS_QUIET_MS = 10_000L

/**
 * GPS is preferred because it gives heading and speed; other sources are used when GPS has no fix
 * (indoors, cold start, or a head unit that routes its GPS through another provider).
 */
fun shouldUse(current: Fix?, candidate: Fix, nowMs: Long): Boolean = when {
    current == null -> true
    candidate.provider == "gps" -> true
    current.provider != "gps" -> true
    else -> nowMs - current.timeMs >= GPS_QUIET_MS
}

/** What the unit's location service reports, shown while there is no fix and in settings. */
data class GpsDiagnostics(
    val providers: List<String>? = null,
    val satellitesUsed: Int? = null,
    val satellitesInView: Int? = null,
    val errors: List<String> = emptyList(),
)

fun waitingText(d: GpsDiagnostics): String = when {
    d.providers != null && "gps" !in d.providers -> "No GPS on this unit's location service — using other sources"
    d.satellitesInView == 0 -> "Waiting for GPS… no satellites in view (check the GPS antenna or park outside)"
    d.satellitesUsed != null && d.satellitesInView != null ->
        "Waiting for GPS… ${d.satellitesUsed} of ${d.satellitesInView} satellites"
    else -> "Waiting for GPS…"
}

/** Multi-line GPS report for the settings screen, so the unit's behaviour can be read off it. */
fun gpsStatusText(d: GpsDiagnostics, state: LocationState): String = buildList {
    add(
        when (state) {
            LocationState.NoPermission -> "Location permission not granted"
            is LocationState.Waiting -> "No position yet"
            is LocationState.Live -> "Position from ${state.fix.provider}"
        },
    )
    d.providers?.let { add("Sources: " + it.ifEmpty { listOf("none") }.joinToString(", ")) }
    if (d.satellitesInView != null) add("Satellites: ${d.satellitesUsed ?: 0} used of ${d.satellitesInView} in view")
    d.errors.forEach { add("Problem: $it") }
}.joinToString("\n")
