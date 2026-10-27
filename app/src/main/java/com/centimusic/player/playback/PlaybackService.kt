package com.centimusic.player.playback

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.centimusic.player.MainActivity
import com.centimusic.player.CentiMusicApp
import com.centimusic.player.R
import com.centimusic.player.data.preferences.ResumePoint
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Hosts the ExoPlayer instance and its MediaSession. Media3 turns this into a foreground service
 * with a media notification while playing, which also drives lock-screen and Bluetooth controls.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val container by lazy { (application as CentiMusicApp).container }

    private lateinit var player: ExoPlayer
    private var session: MediaSession? = null
    private lateinit var fader: SongFader

    private var saveQueueJob: Job? = null
    private var progressJob: Job? = null
    private var restoring = false
    private var consecutiveErrors = 0
    private var currentRecorded = false

    override fun onCreate() {
        super.onCreate()
        // MeteringRenderersFactory feeds the in-app visualizer from decoded audio (no extra permissions).
        player = ExoPlayer.Builder(this, MeteringRenderersFactory(this))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.addListener(playerListener)
        fader = SongFader(player, scope).also(player::addListener)

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(SessionCallback())
            .setBitmapLoader(ArtworkBitmapLoader(this, CacheBitmapLoader(DataSourceBitmapLoader(this)), scope))
            .build()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply { setSmallIcon(R.drawable.ic_notification) },
        )

        scope.launch {
            container.preferences.preferences.collect { prefs ->
                player.setHandleAudioBecomingNoisy(prefs.pauseOnDisconnect)
                player.skipSilenceEnabled = prefs.skipSilence
                fader.fadeMs = prefs.fadeSeconds * 1_000L
            }
        }
        scope.launch { restoreQueueIfEmpty(playWhenReady = false) }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep playing in the background if music is playing; otherwise there is no reason to stay alive.
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            saveStateBlocking()
            stopSelf()
        }
    }

    override fun onDestroy() {
        saveStateBlocking()
        session?.run {
            player.release()
            release()
        }
        session = null
        scope.cancel()
        super.onDestroy()
    }

    // region Persistence

    private suspend fun restoreQueueIfEmpty(playWhenReady: Boolean): MediaSession.MediaItemsWithStartPosition? {
        val prefs = container.preferences.current()
        val restored = loadSavedQueue() ?: return null
        if (player.mediaItemCount == 0) {
            restoring = true
            if (prefs.rememberPlaybackModes) {
                player.shuffleModeEnabled = prefs.shuffleEnabled
                player.repeatMode = prefs.repeatMode
            }
            player.setMediaItems(restored.mediaItems, restored.startIndex, restored.startPositionMs)
            player.prepare()
            player.playWhenReady = playWhenReady
            restoring = false
        }
        return restored
    }

    private suspend fun loadSavedQueue(): MediaSession.MediaItemsWithStartPosition? {
        if (!container.preferences.current().resumeLastQueue) return null
        val saved = container.database.queueDao().load()
        if (saved.isEmpty()) return null
        val library = container.music.awaitLibrary()
        val point = container.preferences.resumePoint()
        val savedCurrentId = saved.getOrNull(point.index)?.mediaId
        val songs = saved.mapNotNull { library.byId[it.mediaId] }
        if (songs.isEmpty()) return null
        val index = songs.indexOfFirst { it.id == savedCurrentId }
        return MediaSession.MediaItemsWithStartPosition(
            songs.map { it.toMediaItem() },
            index.coerceAtLeast(0),
            if (index >= 0) point.positionMs else 0L,
        )
    }

    private fun scheduleQueueSave() {
        if (restoring) return
        val ids = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
        saveQueueJob?.cancel()
        saveQueueJob = scope.launch {
            delay(500)
            container.database.queueDao().replace(ids)
        }
    }

    private fun saveResumePoint() {
        if (player.mediaItemCount == 0) return
        val point = ResumePoint(player.currentMediaItemIndex, player.currentPosition)
        scope.launch { container.preferences.saveResumePoint(point) }
    }

    private fun saveStateBlocking() {
        if (!::player.isInitialized || player.mediaItemCount == 0) return
        val ids = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
        val point = ResumePoint(player.currentMediaItemIndex, player.currentPosition)
        runBlocking {
            withTimeoutOrNull(1_500) {
                container.database.queueDao().replace(ids)
                container.preferences.saveResumePoint(point)
            }
        }
    }

    // endregion

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_TIMELINE_CHANGED)) scheduleQueueSave()
            if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_POSITION_DISCONTINUITY, Player.EVENT_IS_PLAYING_CHANGED)) {
                if (!restoring) saveResumePoint()
            }
            if (events.containsAny(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED, Player.EVENT_REPEAT_MODE_CHANGED) && !restoring) {
                val shuffle = player.shuffleModeEnabled
                val repeat = player.repeatMode
                scope.launch {
                    if (container.preferences.current().rememberPlaybackModes) container.preferences.savePlaybackModes(shuffle, repeat)
                }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            currentRecorded = false
            if (player.isPlaying) recordCurrentPlay()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                recordCurrentPlay()
                startProgressSaver()
            } else {
                progressJob?.cancel()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) consecutiveErrors = 0
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.w(TAG, "Playback error ${error.errorCodeName} on ${player.currentMediaItem?.mediaId}", error)
            consecutiveErrors++
            // A missing or unreadable file shouldn't stop the whole queue: skip ahead unless every item failed.
            if (consecutiveErrors < player.mediaItemCount && player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
            }
        }
    }

    private fun recordCurrentPlay() {
        if (currentRecorded) return
        val mediaId = player.currentMediaItem?.mediaId ?: return
        currentRecorded = true
        scope.launch { container.history.recordPlay(mediaId) }
    }

    private fun startProgressSaver() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                delay(10_000)
                saveResumePoint()
            }
        }
    }

    private inner class SessionCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(mediaItems.map { it.withPlayableUri() }.toMutableList())

        /** System media resumption (e.g. the "play" button on a headset after the app was closed). */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
            loadSavedQueue() ?: throw UnsupportedOperationException("Nothing to resume")
        }
    }

    companion object {
        private const val TAG = "PlaybackService"
    }
}
