package com.centimusic.player.data.preferences

/** Selectable looks for the Now Playing pulse; all are driven by the same audio pulse. */
enum class PulseStyle(val label: String, val description: String) {
    HALO("Halo", "Red ring, rising dot columns and ripples"),
    ORBIT("Orbit", "Dots circling on three orbits"),
    SONAR("Sonar", "Dot rings sent out with the beat"),
    GALAXY("Galaxy", "Spinning spiral arms of dots"),
    WAVE("Wave", "Dotted waves rolling around the cover");

    fun next(): PulseStyle = entries[(ordinal + 1) % entries.size]
}
