package com.centimusic.player

import com.centimusic.player.data.media.Duplicates
import com.centimusic.player.data.media.Library
import com.centimusic.player.data.media.Song
import com.centimusic.player.data.media.SongSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicatesTest {
    private var next = 0
    private fun song(
        title: String,
        artist: String = "KIRO",
        duration: Long = 282_000,
        added: Long = 100,
        source: SongSource = SongSource.MEDIA_STORE,
    ) = Song(
        id = "content://media/external/audio/media/${next++}",
        title = title, artist = artist, album = "Album", albumId = 1, durationMs = duration,
        trackNumber = 0, year = 0, dateAddedSec = added, folder = "Music", source = source,
    )

    @Test
    fun `copies with noisy titles are merged into the oldest file`() {
        val original = song("Huwag Sumikap Ka", added = 10)
        val copies = listOf(
            song("Huwag Sumikap Ka (Official Music Video)", added = 20),
            song("KIRO - Huwag Sumikap Ka", added = 30),
            song("02 - Huwag Sumikap Ka", duration = 283_500, added = 40),
            song("Huwag Sumikap Ka - Copy", added = 50),
            song("Huwag Sumikap Ka (1)", added = 60),
            song("huwag sumikap ka [Lyrics]", added = 70),
        )
        val aliases = Duplicates.find(listOf(original) + copies)
        assertEquals(copies.size, aliases.size)
        assertTrue(aliases.values.all { it == original.id })
    }

    @Test
    fun `different songs are never merged`() {
        val songs = listOf(
            song("Sunog na Kaluluwa", artist = "VincH Music"),
            song("Sunog na Kaluluwa", artist = "Another Artist"), // different artist
            song("Sunog na Kaluluwa", artist = "VincH Music", duration = 200_000), // different length: other version
            song("Sunog", artist = "VincH Music"), // different title
            song("Unknown title", artist = "Unknown artist"),
            song("Unknown title", artist = "Unknown artist"), // too generic to compare
        )
        assertTrue(Duplicates.find(songs).isEmpty())
    }

    @Test
    fun `scanned copy is preferred over an imported one`() {
        val imported = song("Good Days", artist = "SZA", added = 1, source = SongSource.IMPORTED)
        val scanned = song("Good Days", artist = "SZA", added = 99)
        assertEquals(mapOf(imported.id to scanned.id), Duplicates.find(listOf(imported, scanned)))
    }

    @Test
    fun `hidden copies still resolve so playlists and likes keep working`() {
        val a = song("Good Days", artist = "SZA", added = 1)
        val b = song("Good Days (Official Audio)", artist = "SZA", added = 2)
        val library = Library.from(listOf(a, b), hideDuplicates = true)
        assertEquals(listOf(a), library.songs)
        assertEquals(1, library.hiddenDuplicates)
        assertEquals(a, library.byId[b.id])

        val unfiltered = Library.from(listOf(a, b), hideDuplicates = false)
        assertEquals(2, unfiltered.songs.size)
    }
}
