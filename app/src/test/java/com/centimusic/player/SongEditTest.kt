package com.centimusic.player

import com.centimusic.player.data.database.SongEditEntity
import com.centimusic.player.data.media.Song
import com.centimusic.player.data.media.SongSource
import com.centimusic.player.data.media.withEdit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SongEditTest {
    private val song = Song(
        id = "content://media/external/audio/media/1", title = "sunog na kaluluwa (official video)", artist = "VincH Music",
        album = "Unknown album", albumId = 42, durationMs = 255_000, trackNumber = 0, year = 0, dateAddedSec = 0,
        folder = "Music", source = SongSource.MEDIA_STORE,
    )

    @Test
    fun `edited fields replace the tags and untouched ones stay`() {
        val edited = song.withEdit(SongEditEntity(song.id, title = "Sunog na Kaluluwa", artist = null, album = null, artworkPath = null, updatedAt = 0))
        assertEquals("Sunog na Kaluluwa", edited.title)
        assertEquals("VincH Music", edited.artist)
        assertEquals(42L, edited.albumId) // album untouched: still grouped by MediaStore album
    }

    @Test
    fun `a new album name regroups by name and a custom cover wins`() {
        val edited = song.withEdit(SongEditEntity(song.id, null, null, album = "Kaluluwa", artworkPath = "/data/cover.jpg", updatedAt = 0))
        assertEquals("Kaluluwa", edited.album)
        assertTrue(edited.albumKey.startsWith("name:"))
        assertEquals("file:/data/cover.jpg", edited.artworkKey)
    }
}
