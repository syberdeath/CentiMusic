package com.centimusic.player.ui.visualizer

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.centimusic.player.data.media.formatDuration
import com.centimusic.player.data.preferences.UserPreferences
import com.centimusic.player.navigation.LocalAppContainer
import com.centimusic.player.navigation.LocalNavigator
import com.centimusic.player.playback.AudioLevelMeter
import com.centimusic.player.ui.components.Artwork
import com.centimusic.player.ui.pulse.rememberIsVisible
import com.centimusic.player.ui.theme.AccentRed
import com.centimusic.player.ui.theme.DefaultNmType
import com.centimusic.player.ui.theme.NmIcons

private val Black = Color(0xFF080808)
private val Secondary = Color(0xFFA0A0A0)
private val Tertiary = Color(0xFF6B6B6B)

/**
 * Full-screen, always-black visualizer. The waveform is driven by the decoded audio's real
 * loudness (see AudioLevelMeter); the red dot follows the real playback position. All controls go
 * through the same MediaController as the rest of the app.
 */
@Composable
fun VisualizerScreen() {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val player = container.player
    val playback by player.state.collectAsStateWithLifecycle()
    val prefs by container.preferences.preferences.collectAsStateWithLifecycle(initialValue = UserPreferences())
    val position by remember(player) { player.positionTicker(200) }.collectAsStateWithLifecycle(0L)

    // Battery: animate only while playing AND the screen is actually visible.
    val visible = rememberIsVisible()
    val song = playback.current

    BoxWithConstraints(Modifier.fillMaxSize().background(Black)) {
        val columns = ((maxWidth.value - 40f) / 10f).toInt().coerceIn(20, 64)
        val waveform = rememberWaveformState(columns, running = playback.isPlaying && visible, trackKey = song?.id)

        LightSystemBars()
        ShowOverLockScreen(enabled = prefs.visualizerOnLockScreen, activity = activity)
        val leave = {
            leaveVisualizer(activity, lockScreenMode = prefs.visualizerOnLockScreen) { navigator.back() }
        }
        BackHandler(onBack = leave)

        if (song == null) {
            LaunchedEffect(Unit) { navigator.back() }
            return@BoxWithConstraints
        }

        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTap(NmIcons.ChevronDown, "Close visualizer", leave)
                Spacer(Modifier.weight(1f))
                Text("VISUALIZER", style = DefaultNmType.dotCaption, color = Tertiary)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(48.dp))
            }

            Spacer(Modifier.height(24.dp))
            AnimatedContent(song, transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) }, contentKey = { it.id }, label = "track") { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Artwork(s, Modifier.size(64.dp), corner = 10.dp, sizeHint = 128.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.title, style = DefaultNmType.trackTitle.copy(fontSize = 24.sp, lineHeight = 28.sp), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        Text("${s.artist} · ${s.album}", style = DefaultNmType.body, color = Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            DotWaveform(waveform, height = 220.dp)
            Spacer(Modifier.height(12.dp))
            SignalCaption(supported = AudioLevelMeter.supported, hasSignal = waveform.hasSignal, playing = playback.isPlaying)
            Spacer(Modifier.weight(1f))

            DotProgress(position, playback.durationMs, playback.isPlaying, onSeek = player::seekTo)
            Row(Modifier.fillMaxWidth()) {
                Text(position.formatDuration(), style = DefaultNmType.dotCaption, color = Secondary)
                Spacer(Modifier.weight(1f))
                Text(playback.durationMs.formatDuration(), style = DefaultNmType.dotCaption, color = Secondary)
            }

            Spacer(Modifier.height(24.dp))
            Row(
                Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconTap(NmIcons.Previous, "Previous track", player::previous, size = 28)
                Box(
                    Modifier.size(72.dp).clip(CircleShape).background(AccentRed)
                        .clickable(role = Role.Button, onClick = player::togglePlayPause),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (playback.isPlaying) NmIcons.Pause else NmIcons.Play,
                        if (playback.isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp),
                    )
                }
                IconTap(NmIcons.Next, "Next track", player::next, size = 28, enabled = playback.hasNext)
            }
        }
    }
}

/** States plainly whether the waveform is live, so a flat line is never mistaken for the music. */
@Composable
private fun SignalCaption(supported: Boolean, hasSignal: Boolean, playing: Boolean) {
    val (dot, text) = when {
        !supported -> Tertiary to "Live levels aren't available for this audio format"
        !playing -> Tertiary to "Paused"
        hasSignal -> AccentRed to "Live · measured from the audio"
        else -> Tertiary to "Listening…"
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(8.dp))
        Text(text, style = DefaultNmType.caption, color = Secondary)
    }
}

@Composable
private fun IconTap(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    size: Int = 24,
    enabled: Boolean = true,
) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = if (enabled) Color.White else Color.White.copy(alpha = 0.3f), modifier = Modifier.size(size.dp))
    }
}

/** The screen is always black, so system bar icons must be light regardless of the app theme. */
@Composable
private fun LightSystemBars() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val status = controller?.isAppearanceLightStatusBars
        val nav = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            status?.let { controller.isAppearanceLightStatusBars = it }
            nav?.let { controller.isAppearanceLightNavigationBars = it }
        }
    }
}

/**
 * Optional (Settings → "Visualizer on lock screen"): while this screen is open, the activity may
 * be shown over the keyguard, so waking the phone returns straight to the visualizer without
 * unlocking. This uses the standard showWhenLocked window flag; it does not modify the system lock
 * screen, and leaving the visualizer while locked requires unlocking first.
 */
@Composable
private fun ShowOverLockScreen(enabled: Boolean, activity: Activity?) {
    DisposableEffect(enabled, activity) {
        if (enabled && activity != null) setShowWhenLocked(activity, true)
        onDispose { if (enabled && activity != null) setShowWhenLocked(activity, false) }
    }
}

private fun setShowWhenLocked(activity: Activity, show: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        activity.setShowWhenLocked(show)
    } else {
        @Suppress("DEPRECATION")
        if (show) activity.window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
    }
}

/** Leaving while shown over the keyguard asks the user to unlock first, so the rest of the app stays protected. */
private fun leaveVisualizer(activity: Activity?, lockScreenMode: Boolean, back: () -> Unit) {
    val keyguard = activity?.getSystemService(KeyguardManager::class.java)
    if (lockScreenMode && activity != null && keyguard?.isKeyguardLocked == true) {
        keyguard.requestDismissKeyguard(
            activity,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = back()
            },
        )
    } else {
        back()
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
