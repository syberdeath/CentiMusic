package com.centimusic.player.ui.pulse

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.centimusic.player.data.media.Song
import com.centimusic.player.data.preferences.PulseStyle
import com.centimusic.player.ui.components.Artwork
import com.centimusic.player.ui.theme.NmTheme
import com.centimusic.player.ui.theme.Spacing

/** Bottom sheet on Now Playing: every pulse style previewed live with the current song. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PulsePickerSheet(
    current: PulseStyle,
    song: Song,
    pulse: ArtworkPulse,
    timeSec: () -> Float,
    onSelect: (PulseStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = NmTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = Spacing.m, bottom = Spacing.s)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.divider),
            )
        },
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = Spacing.xl)) {
            Text("Pulse", style = NmTheme.type.screenTitle, color = colors.textPrimary, modifier = Modifier.padding(horizontal = Spacing.gutter))
            Text(
                "How the artwork reacts to the music",
                style = NmTheme.type.bodySmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = Spacing.gutter),
            )
            Spacer(Modifier.height(Spacing.l))
            LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.gutter),
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                items(PulseStyle.entries, key = { it.name }) { style ->
                    val selected = style == current
                    Column(
                        Modifier
                            .width(136.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .border(if (selected) 1.5.dp else 1.dp, if (selected) colors.accent else colors.divider, RoundedCornerShape(18.dp))
                            .clickable { onSelect(style) }
                            .padding(Spacing.m),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                            PulseHalo(
                                pulse = pulse,
                                artworkFraction = 0.62f,
                                timeSec = timeSec,
                                style = style,
                                modifier = Modifier.fillMaxSize(),
                                dotColor = colors.textPrimary,
                                accent = colors.accent,
                                restColor = colors.textPrimary,
                            )
                            Artwork(song, Modifier.size(112.dp * 0.62f).clip(CircleShape), corner = 40.dp, sizeHint = 96.dp)
                        }
                        Spacer(Modifier.height(Spacing.s))
                        Text(style.label, style = NmTheme.type.label, color = if (selected) colors.accent else colors.textPrimary)
                        Text(
                            style.description,
                            style = NmTheme.type.caption,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                        )
                    }
                }
            }
        }
    }
}
