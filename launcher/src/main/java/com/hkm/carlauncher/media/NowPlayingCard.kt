package com.hkm.carlauncher.media

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hkm.carlauncher.ui.theme.LauncherCard
import com.hkm.carlauncher.ui.theme.LauncherColors
import com.hkm.carlauncher.ui.theme.LauncherIcons

@Composable
fun NowPlayingCard(
    state: NowPlaying,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onOpenSource: () -> Unit,
    onGrantAccess: () -> Unit,
    onOpenMusic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LauncherCard(modifier) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp)) {
            when (state) {
                NowPlaying.NoAccess -> Message(
                    "Allow access to show what's playing",
                    "Android needs notification access before the launcher can show and control music from other apps.",
                    "Allow",
                    onGrantAccess,
                )
                NowPlaying.Nothing -> Message("Nothing playing", "Start music, radio or Bluetooth audio and it appears here.", "Open Music", onOpenMusic)
                is NowPlaying.Playing -> Playing(state, onPlayPause, onNext, onPrevious, onOpenSource)
            }
        }
    }
}

@Composable
private fun Label(text: String) =
    Text(text, color = LauncherColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.Playing(
    s: NowPlaying.Playing,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onOpenSource: () -> Unit,
) {
    Label("NOW PLAYING · ${s.sourceLabel.uppercase()}")
    Column(Modifier.fillMaxWidth().clickable(onClick = onOpenSource)) {
        Box(
            Modifier.padding(top = 12.dp).size(96.dp).clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF1D3D7A), Color(0xFF0B1630)))),
            contentAlignment = Alignment.Center,
        ) {
            if (s.artwork != null) {
                Image(s.artwork.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                LauncherIcons.Note(Modifier.size(44.dp), Color(0xFF9FBCFF))
            }
        }
        Text(s.title, color = LauncherColors.Text, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 12.dp))
        if (s.artist.isNotBlank()) {
            Text(s.artist, color = LauncherColors.Muted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        }
    }
    Box(Modifier.padding(top = 14.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF2A313C))) {
        Box(Modifier.fillMaxWidth(s.progress.coerceIn(0f, 1f)).height(4.dp).background(LauncherColors.Accent))
    }
    Spacer(Modifier.weight(1f))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        RoundButton(58.dp, LauncherColors.Raised, "Previous", onPrevious) { LauncherIcons.Previous(it, LauncherColors.Text) }
        RoundButton(72.dp, LauncherColors.Accent, if (s.isPlaying) "Pause" else "Play", onPlayPause) {
            if (s.isPlaying) LauncherIcons.Pause(it, Color.White) else LauncherIcons.Play(it, Color.White)
        }
        RoundButton(58.dp, LauncherColors.Raised, "Next", onNext) { LauncherIcons.Next(it, LauncherColors.Text) }
    }
}

@Composable
private fun RoundButton(size: Dp, color: Color, description: String, onClick: () -> Unit, icon: @Composable (Modifier) -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).background(color).clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { icon(Modifier.size(size * 0.42f)) }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.Message(title: String, body: String, action: String, onAction: () -> Unit) {
    Label("NOW PLAYING")
    Text(title, color = LauncherColors.Text, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp))
    Text(body, color = LauncherColors.Muted, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
    Spacer(Modifier.weight(1f))
    Box(
        Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(16.dp)).background(LauncherColors.Accent).clickable(onClick = onAction),
        contentAlignment = Alignment.Center,
    ) { Text(action, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
}
