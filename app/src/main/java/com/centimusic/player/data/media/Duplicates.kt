package com.centimusic.player.data.media

import java.text.Normalizer
import kotlin.math.abs

/**
 * Finds songs that are the same recording stored more than once (copies in different folders,
 * re-downloads, a file both scanned and imported). Two songs are duplicates when their cleaned-up
 * title and artist match and their durations are within [DURATION_TOLERANCE_MS].
 *
 * Returns hidden song id → id of the copy that is kept.
 */
object Duplicates {
    const val DURATION_TOLERANCE_MS = 3_000L

    fun find(songs: List<Song>): Map<String, String> {
        val aliases = HashMap<String, String>()
        songs.groupBy { key(it) }
            .filterKeys { it != null }
            .values
            .filter { it.size > 1 }
            .forEach { group ->
                // Prefer the MediaStore copy, then the oldest one.
                val ordered = group.sortedWith(compareBy<Song>({ it.source != SongSource.MEDIA_STORE }, { it.dateAddedSec }, { it.id }))
                val kept = ArrayList<Song>()
                for (song in ordered) {
                    val match = kept.firstOrNull { abs(it.durationMs - song.durationMs) <= DURATION_TOLERANCE_MS }
                    if (match == null) kept += song else aliases[song.id] = match.id
                }
            }
        return aliases
    }

    /** Grouping key, or null when the tags are too generic to compare safely. */
    internal fun key(song: Song): String? {
        val artist = clean(song.artist).takeUnless { it == "unknownartist" }.orEmpty()
        var rawTitle = song.title
        // "Artist - Title" in the title tag is common for downloaded files.
        if (song.artist.isNotBlank()) {
            val prefix = Regex("^\\s*" + Regex.escape(song.artist) + "\\s*[-–—:|]\\s*", RegexOption.IGNORE_CASE)
            rawTitle = rawTitle.replace(prefix, "")
        }
        val title = clean(rawTitle)
        if (title.length < 2 || title == "unknowntitle") return null
        return "$title|$artist"
    }

    private val noise = Regex(
        "[(\\[{]\\s*(official( music)?( video| audio| lyric video)?|music video|lyric video|lyrics?|audio|video|visuali[sz]er|hd|hq|4k|mv|explicit|clean|copy|\\d{1,2})\\s*[)\\]}]",
        RegexOption.IGNORE_CASE,
    )

    internal fun clean(text: String): String {
        var t = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        t = t.replace(noise, " ")
            .replace(Regex("^\\s*\\d{1,3}\\s*[-.)_ ]\\s*"), "") // leading track number "01 - ", "3. "
            .replace(Regex("\\s*[-_ ]\\s*copy(\\s*\\(?\\d+\\)?)?\\s*$"), "") // "Song - Copy", "Song copy (2)"
        return t.replace(Regex("[^\\p{L}\\p{N}]+"), "")
    }
}
