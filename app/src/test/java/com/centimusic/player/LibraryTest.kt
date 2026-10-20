package com.centimusic.player

import com.centimusic.player.data.media.Library
import com.centimusic.player.data.media.Song
import com.centimusic.player.data.media.SongSort
import com.centimusic.player.data.media.SongSource
import com.centimusic.player.data.media.cleanTag
import com.centimusic.player.data.media.formatDuration
import com.centimusic.player.data.media.formatTotalDuration
import com.centimusic.player.data.media.sortedFor
import com.centimusic.player.playback.PlaybackState
import com.centimusic.player.playback.QueueEntry
import com.centimusic.player.ui.search.matches
import com.centimusic.player.ui.search.normalized
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTest {
    private fun song(
        id: Int,
        title: String,
        artist: String = "Artist",
        album: String = "Album",
        albumId: Long = 1,
        track: Int = 0,
        added: Long = 0,
        duration: Long = 180_000,
        folder: String = "Music",
    ) = Song(
        id = "content://media/external/audio/media/$id",
        title = title,
        artist = artist,
        album = album,
        albumId = albumId,
        durationMs = duration,
        trackNumber = track,
        year = 2020,
        dateAddedSec = added,
        folder = folder,
        source = SongSource.MEDIA_STORE,
    )

    @Test
    fun `library groups albums, artists and folders and removes duplicates`() {
        val songs = listOf(
            song(1, "B side", track = 2),
            song(2, "A side", track = 1),
            song(3, "Other", artist = "Someone", album = "Elsewhere", albumId = 2, folder = "Downloads"),
            song(1, "B side", track = 2), // duplicate id
        )
        val library = Library.from(songs)

        assertEquals(3, library.songs.size)
        assertEquals(2, library.albums.size)
        val album = library.album("id:1")!!
        assertEquals(listOf("A side", "B side"), album.songs.map { it.title })
        assertEquals(2, library.artists.size)
        assertEquals(setOf("Music", "Downloads"), library.folders.map { it.path }.toSet())
        assertEquals("Downloads", library.folder("Downloads")!!.name)
    }

    @Test
    fun `sorting by date added puts newest first`() {
        val sorted = listOf(song(1, "old", added = 10), song(2, "new", added = 30), song(3, "mid", added = 20))
            .sortedFor(SongSort.DATE_ADDED)
        assertEquals(listOf("new", "mid", "old"), sorted.map { it.title })
    }

    @Test
    fun `durations are formatted like a player clock`() {
        assertEquals("0:00", 0L.formatDuration())
        assertEquals("1:05", 65_000L.formatDuration())
        assertEquals("1:02:05", 3_725_000L.formatDuration())
        assertEquals("42 min", (42 * 60_000L).formatTotalDuration())
        assertEquals("1 hr 5 min", (65 * 60_000L).formatTotalDuration())
    }

    @Test
    fun `search matching ignores case and accents`() {
        assertEquals("beyonce", "Beyoncé".normalized())
        val s = song(1, "Halo", artist = "Beyoncé", album = "I Am... Sasha Fierce")
        assertTrue(s.matches("beyonce"))
        assertTrue(s.matches("SASHA"))
        assertFalse(s.matches("adele"))
    }

    @Test
    fun `MediaStore unknown tags are dropped`() {
        assertNull("<unknown>".cleanTag())
        assertNull("   ".cleanTag())
        assertEquals("Muse", " Muse ".cleanTag())
    }

    @Test
    fun `up next lists the songs after the current one in play order`() {
        val entries = (0 until 4).map { QueueEntry("k$it", it, song(it, "t$it")) }
        // Shuffled play order: 2, 0, 3, 1 with item 0 currently playing.
        val shuffled = listOf(entries[2], entries[0], entries[3], entries[1])
        val state = PlaybackState(currentIndex = 0, queue = shuffled)
        assertEquals(1, state.queuePosition)
        assertEquals(listOf(3, 1), state.upNext.map { it.index })
    }
}
