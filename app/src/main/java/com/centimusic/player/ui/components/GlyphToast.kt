package com.centimusic.player.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.centimusic.player.ui.theme.NmTheme

/** Nothing-style toast: a compact pill with a small dot and dot-matrix text. */
@Composable
fun GlyphToast(message: String, modifier: Modifier = Modifier) {
    val colors = NmTheme.colors
    Box(modifier.fillMaxWidth().padding(bottom = 8.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .wrapContentWidth()
                .clip(RoundedCornerShape(50))
                .background(colors.inverse)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(colors.onInverse.copy(alpha = 0.5f)),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                message.uppercase(),
                style = NmTheme.type.dotCaption,
                color = colors.onInverse,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
