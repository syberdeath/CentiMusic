package com.centimusic.player.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.centimusic.player.data.media.SongSort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val songSort: SongSort = SongSort.TITLE,
    /** Restore shuffle/repeat between sessions; otherwise start every session with both off. */
    val rememberPlaybackModes: Boolean = true,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = 0,
    val resumeLastQueue: Boolean = true,
    val pauseOnDisconnect: Boolean = true,
    val skipSilence: Boolean = false,
    val ignoreShortTracks: Boolean = true,
    /** Let the visualizer stay over the keyguard while it is open (showWhenLocked). */
    val visualizerOnLockScreen: Boolean = false,
    /** Collapse the same song stored more than once into a single entry. */
    val hideDuplicates: Boolean = true,
)

/** Playback position bookkeeping for queue restoration. */
data class ResumePoint(val index: Int, val positionMs: Long)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesRepository(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme_mode")
        val sort = stringPreferencesKey("song_sort")
        val rememberModes = booleanPreferencesKey("remember_playback_modes")
        val shuffle = booleanPreferencesKey("shuffle")
        val repeat = intPreferencesKey("repeat_mode")
        val resume = booleanPreferencesKey("resume_last_queue")
        val pauseOnDisconnect = booleanPreferencesKey("pause_on_disconnect")
        val skipSilence = booleanPreferencesKey("skip_silence")
        val ignoreShort = booleanPreferencesKey("ignore_short_tracks")
        val visualizerLock = booleanPreferencesKey("visualizer_on_lock_screen")
        val hideDuplicates = booleanPreferencesKey("hide_duplicates")
        val resumeIndex = intPreferencesKey("resume_index")
        val resumePosition = longPreferencesKey("resume_position")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data.map { p ->
        UserPreferences(
            themeMode = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            songSort = p[Keys.sort]?.let { runCatching { SongSort.valueOf(it) }.getOrNull() } ?: SongSort.TITLE,
            rememberPlaybackModes = p[Keys.rememberModes] ?: true,
            shuffleEnabled = p[Keys.shuffle] ?: false,
            repeatMode = p[Keys.repeat] ?: 0,
            resumeLastQueue = p[Keys.resume] ?: true,
            pauseOnDisconnect = p[Keys.pauseOnDisconnect] ?: true,
            skipSilence = p[Keys.skipSilence] ?: false,
            ignoreShortTracks = p[Keys.ignoreShort] ?: true,
            visualizerOnLockScreen = p[Keys.visualizerLock] ?: false,
            hideDuplicates = p[Keys.hideDuplicates] ?: true,
        )
    }

    suspend fun current(): UserPreferences = preferences.first()

    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[Keys.theme] = mode.name }
    suspend fun setSongSort(sort: SongSort) = context.dataStore.edit { it[Keys.sort] = sort.name }
    suspend fun setRememberPlaybackModes(value: Boolean) = context.dataStore.edit { it[Keys.rememberModes] = value }
    suspend fun setResumeLastQueue(value: Boolean) = context.dataStore.edit { it[Keys.resume] = value }
    suspend fun setPauseOnDisconnect(value: Boolean) = context.dataStore.edit { it[Keys.pauseOnDisconnect] = value }
    suspend fun setSkipSilence(value: Boolean) = context.dataStore.edit { it[Keys.skipSilence] = value }
    suspend fun setIgnoreShortTracks(value: Boolean) = context.dataStore.edit { it[Keys.ignoreShort] = value }
    suspend fun setVisualizerOnLockScreen(value: Boolean) = context.dataStore.edit { it[Keys.visualizerLock] = value }
    suspend fun setHideDuplicates(value: Boolean) = context.dataStore.edit { it[Keys.hideDuplicates] = value }

    suspend fun savePlaybackModes(shuffle: Boolean, repeatMode: Int) = context.dataStore.edit {
        it[Keys.shuffle] = shuffle
        it[Keys.repeat] = repeatMode
    }

    suspend fun saveResumePoint(point: ResumePoint) = context.dataStore.edit {
        it[Keys.resumeIndex] = point.index
        it[Keys.resumePosition] = point.positionMs
    }

    suspend fun resumePoint(): ResumePoint {
        val p = context.dataStore.data.first()
        return ResumePoint(p[Keys.resumeIndex] ?: 0, p[Keys.resumePosition] ?: 0L)
    }
}
