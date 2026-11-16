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
    /** Location updates received so far, per source. */
    val updates: Map<String, Int> = emptyMap(),
    val lastFix: Fix? = null,
    /** System time when [lastFix] arrived (its own time comes from the GPS chip and may be wrong). */
    val lastFixAtMs: Long? = null,
)

fun waitingText(d: GpsDiagnostics): String = when {
    d.providers != null && "gps" !in d.providers -> "No GPS on this unit's location service — using other sources"
    d.satellitesInView == 0 -> "Waiting for GPS… no satellites in view (check the GPS antenna or park outside)"
    d.satellitesUsed != null && d.satellitesInView != null ->
        "Waiting for GPS… ${d.satellitesUsed} of ${d.satellitesInView} satellites"
    else -> "Waiting for GPS…"
}

/** More than this between the GPS chip's clock and the system clock means the chip's time is wrong. */
private const val CLOCK_TOLERANCE_MS = 60_000L

/** Multi-line GPS report for the settings screen, so the unit's behaviour can be read off it. */
fun gpsStatusText(d: GpsDiagnostics, state: LocationState, nowMs: Long): String = buildList {
    add(
        when (state) {
            LocationState.NoPermission -> "Location permission not granted"
            is LocationState.Waiting -> "No position yet"
            is LocationState.Live -> "Position from ${state.fix.provider}"
        },
    )
    d.providers?.let { add("Sources: " + it.ifEmpty { listOf("none") }.joinToString(", ")) }
    if (d.updates.isNotEmpty()) add("Updates: " + d.updates.entries.joinToString(", ") { "${it.key} ${it.value}" })
    if (d.satellitesInView != null) add("Satellites: ${d.satellitesUsed ?: 0} used of ${d.satellitesInView} in view")
    val fix = d.lastFix
    val at = d.lastFixAtMs
    if (fix != null && at != null) {
        val age = ((nowMs - at) / 1000).coerceAtLeast(0)
        add("Last position: %.5f, %.5f from %s, %d s ago".format(java.util.Locale.ROOT, fix.lat, fix.lon, fix.provider, age))
        if (kotlin.math.abs(fix.timeMs - at) > CLOCK_TOLERANCE_MS) {
            val days = kotlin.math.abs(fix.timeMs - at) / 86_400_000
            add("GPS clock is wrong (off by $days days)")
        }
    }
    d.errors.forEach { add("Problem: $it") }
}.joinToString("\n")
