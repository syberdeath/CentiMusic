package com.centimusic.player.data.preferences

/** Selectable looks for the Now Playing pulse. All are driven by the same [ArtworkPulse]. */
enum class PulseStyle(val label: String, val description: String) {
    HALO("Halo", "Red ring with a ring of dot columns"),
    RIPPLE("Ripple", "Rings ripple outward from the artwork"),
    SUNBURST("Sunburst", "Radial bars, like a round equalizer"),
    ORBIT("Orbit", "Dotted rings orbiting the artwork"),
    GLOW("Glow", "Soft red glow that breathes behind the artwork"),
    MINIMAL("Minimal", "A single thin red ring");

    fun next(): PulseStyle = entries[(ordinal + 1) % entries.size]
}
