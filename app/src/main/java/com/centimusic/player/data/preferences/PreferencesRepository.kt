package com.centimusic.player.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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
    /** Fade between songs, in seconds; 0 = off (gapless). */
    val fadeSeconds: Int = 0,
    val ignoreShortTracks: Boolean = true,
    /** Let the visualizer stay over the keyguard while it is open (showWhenLocked). */
    val visualizerOnLockScreen: Boolean = false,
    /** Collapse the same song stored more than once into a single entry. */
    val hideDuplicates: Boolean = true,
    val pulseStyle: PulseStyle = PulseStyle.HALO,
    /** Folders (relative paths) left out of the library, e.g. WhatsApp audio or recordings. */
    val hiddenFolders: Set<String> = emptySet(),
    val splashChoice: SplashChoice = SplashChoice.SURPRISE,
    /** Name used in the launch greeting ("Good evening, Vincent"). */
    val greetingName: String = "Vincent",
    /** Day (yyyy-DDD) the full launch animation last played; later launches that day are quick. */
    val lastFullSplashDay: String = "",
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
        val fadeSeconds = intPreferencesKey("fade_seconds")
        val ignoreShort = booleanPreferencesKey("ignore_short_tracks")
        val visualizerLock = booleanPreferencesKey("visualizer_on_lock_screen")
        val hideDuplicates = booleanPreferencesKey("hide_duplicates")
        val pulseStyle = stringPreferencesKey("pulse_style")
        val hiddenFolders = stringSetPreferencesKey("hidden_folders")
        val splashChoice = stringPreferencesKey("splash_choice")
        val greetingName = stringPreferencesKey("greeting_name")
        val lastFullSplashDay = stringPreferencesKey("last_full_splash_day")
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
            fadeSeconds = p[Keys.fadeSeconds] ?: 0,
            ignoreShortTracks = p[Keys.ignoreShort] ?: true,
            visualizerOnLockScreen = p[Keys.visualizerLock] ?: false,
            hideDuplicates = p[Keys.hideDuplicates] ?: true,
            pulseStyle = p[Keys.pulseStyle]?.let { runCatching { PulseStyle.valueOf(it) }.getOrNull() } ?: PulseStyle.HALO,
            hiddenFolders = p[Keys.hiddenFolders] ?: emptySet(),
            splashChoice = p[Keys.splashChoice]?.let { runCatching { SplashChoice.valueOf(it) }.getOrNull() } ?: SplashChoice.SURPRISE,
            greetingName = p[Keys.greetingName] ?: "Vincent",
            lastFullSplashDay = p[Keys.lastFullSplashDay] ?: "",
        )
    }

    suspend fun current(): UserPreferences = preferences.first()

    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[Keys.theme] = mode.name }
    suspend fun setSongSort(sort: SongSort) = context.dataStore.edit { it[Keys.sort] = sort.name }
    suspend fun setRememberPlaybackModes(value: Boolean) = context.dataStore.edit { it[Keys.rememberModes] = value }
    suspend fun setResumeLastQueue(value: Boolean) = context.dataStore.edit { it[Keys.resume] = value }
    suspend fun setPauseOnDisconnect(value: Boolean) = context.dataStore.edit { it[Keys.pauseOnDisconnect] = value }
    suspend fun setSkipSilence(value: Boolean) = context.dataStore.edit { it[Keys.skipSilence] = value }
    suspend fun setFadeSeconds(value: Int) = context.dataStore.edit { it[Keys.fadeSeconds] = value }
    suspend fun setIgnoreShortTracks(value: Boolean) = context.dataStore.edit { it[Keys.ignoreShort] = value }
    suspend fun setVisualizerOnLockScreen(value: Boolean) = context.dataStore.edit { it[Keys.visualizerLock] = value }
    suspend fun setHideDuplicates(value: Boolean) = context.dataStore.edit { it[Keys.hideDuplicates] = value }
    suspend fun setPulseStyle(style: PulseStyle) = context.dataStore.edit { it[Keys.pulseStyle] = style.name }
    suspend fun setSplashChoice(value: SplashChoice) = context.dataStore.edit { it[Keys.splashChoice] = value.name }
    suspend fun setGreetingName(value: String) = context.dataStore.edit { it[Keys.greetingName] = value.trim() }
    suspend fun setLastFullSplashDay(value: String) = context.dataStore.edit { it[Keys.lastFullSplashDay] = value }
    suspend fun setFolderHidden(folder: String, hidden: Boolean) = context.dataStore.edit {
        val current = it[Keys.hiddenFolders] ?: emptySet()
        it[Keys.hiddenFolders] = if (hidden) current + folder else current - folder
    }

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
