package com.hkm.carlauncher.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import com.hkm.carlauncher.apps.AppOpener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Follows whichever app is playing audio (Bluetooth, radio, music apps) through Android's media sessions. */
class NowPlayingRepository(private val context: Context, private val scope: CoroutineScope) {
    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val listenerComponent = ComponentName(context, LauncherNotificationListener::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow<NowPlaying>(NowPlaying.NoAccess)
    val state: StateFlow<NowPlaying> = _state.asStateFlow()

    private var controllers: List<MediaController> = emptyList()
    private var current: MediaController? = null
    private var listening = false
    private var ticker: Job? = null
    private val lastActive = mutableMapOf<String, Long>()

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = recompute()
        override fun onMetadataChanged(metadata: MediaMetadata?) = recompute()
        override fun onSessionDestroyed() = recompute()
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        setControllers(list.orEmpty())
    }

    init {
        checkAccess()
    }

    /** Call on resume: picks up access granted in system settings. */
    fun checkAccess() {
        val granted = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        if (!granted) {
            stopListening()
            _state.value = NowPlaying.NoAccess
            return
        }
        if (listening) return
        try {
            manager.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent, handler)
            listening = true
            setControllers(manager.getActiveSessions(listenerComponent))
        } catch (e: SecurityException) {
            _state.value = NowPlaying.NoAccess
        }
    }

    fun playPause() {
        val c = current ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() { current?.transportControls?.skipToNext() }

    fun previous() { current?.transportControls?.skipToPrevious() }

    /** Opens the playing app's own screen, falling back to launching the app. */
    fun openSource(opener: AppOpener) {
        val c = current ?: return
        val sent = runCatching { c.sessionActivity?.send() != null }.getOrDefault(false)
        if (!sent) opener.launchPackage(c.packageName)
    }

    private fun stopListening() {
        if (listening) manager.removeOnActiveSessionsChangedListener(sessionsListener)
        listening = false
        setControllers(emptyList())
    }

    private fun setControllers(list: List<MediaController>) {
        controllers.forEach { it.unregisterCallback(controllerCallback) }
        controllers = list
        list.forEach { it.registerCallback(controllerCallback, handler) }
        recompute()
    }

    private fun recompute() {
        val infos = controllers.map { c ->
            val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
            if (playing) lastActive[c.packageName] = SystemClock.elapsedRealtime()
            SessionInfo(c.packageName, playing, lastActive[c.packageName] ?: (c.playbackState?.lastPositionUpdateTime ?: 0L))
        }
        val chosen = pickSession(infos)
        current = chosen?.let { info -> controllers.firstOrNull { it.packageName == info.packageName } }
        val c = current
        if (c == null) {
            _state.value = NowPlaying.Nothing
            ticker?.cancel()
            return
        }
        _state.value = describe(c)
        val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
        ticker?.cancel()
        if (playing) {
            ticker = scope.launch {
                while (isActive) {
                    delay(1_000)
                    val now = current ?: break
                    _state.value = describe(now)
                }
            }
        }
    }

    private fun describe(c: MediaController): NowPlaying.Playing {
        val meta = c.metadata
        val playback = c.playbackState
        val label = appLabel(c.packageName)
        val title = meta?.text(MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: label
        val artist = meta?.text(
            MediaMetadata.METADATA_KEY_ARTIST,
            MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
            MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE,
        ).orEmpty()
        val art: Bitmap? = meta?.let {
            it.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: it.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: it.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        }
        val isPlaying = playback?.state == PlaybackState.STATE_PLAYING
        val progress = if (playback == null) 0f else progressFraction(
            positionMs = playback.position,
            updateTimeMs = playback.lastPositionUpdateTime,
            speed = playback.playbackSpeed,
            isPlaying = isPlaying,
            durationMs = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            nowMs = SystemClock.elapsedRealtime(),
        )
        return NowPlaying.Playing(label, c.packageName, title, artist, art, isPlaying, progress)
    }

    private fun MediaMetadata.text(vararg keys: String): String? =
        keys.asSequence().mapNotNull { getString(it)?.trim() }.firstOrNull { it.isNotEmpty() }

    private fun appLabel(pkg: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)
}
