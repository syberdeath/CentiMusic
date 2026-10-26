package com.centimusic.player.data.preferences

/** Selectable looks for the Now Playing pulse; all are driven by the same audio pulse. */
enum class PulseStyle(val label: String, val description: String) {
    HALO("Halo", "Red ring with rising dot columns"),
    GLYPH("Glyph", "Light segments around the artwork"),
    DOT_RING("Dot Ring", "Dots that swell with the music"),
    ECHO("Echo", "Three breathing rings"),
    MATRIX("Matrix", "Round dot-matrix equalizer");

    fun next(): PulseStyle = entries[(ordinal + 1) % entries.size]
}
