package com.centimusic.player.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.centimusic.player.ui.theme.NmTheme
import com.centimusic.player.ui.theme.Spacing
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val WidgetHeight = 140.dp

/** Week-at-a-glance widget: one dotted column per day, today's column topped in red. */
@Composable
fun WeekWidget(stats: WeekStats, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = NmTheme.colors
    val max = stats.plays.maxOrNull()?.coerceAtLeast(1) ?: 1
    Column(
        modifier
            .height(WidgetHeight)
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(Spacing.l),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text("THIS WEEK", style = NmTheme.type.dotCaption, color = colors.textSecondary)
            Text(formatMinutes(stats.minutes), style = NmTheme.type.screenTitle.copy(fontSize = 22.sp, lineHeight = 24.sp), color = colors.textPrimary, maxLines = 1)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            stats.plays.forEachIndexed { i, plays ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    DotColumn(lit = if (plays == 0) 0 else ((plays.toFloat() / max) * ROWS).toInt().coerceIn(1, ROWS), today = i == stats.plays.lastIndex)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stats.dayLabels.getOrElse(i) { "" },
                        style = NmTheme.type.dotCaption.copy(fontSize = 9.sp, lineHeight = 10.sp),
                        color = if (i == stats.plays.lastIndex) colors.accent else colors.textTertiary,
                    )
                }
            }
        }
    }
}

private const val ROWS = 6

@Composable
private fun DotColumn(lit: Int, today: Boolean, dot: Dp = 6.dp, gap: Dp = 3.dp) {
    val colors = NmTheme.colors
    Canvas(Modifier.size(width = dot, height = dot * ROWS + gap * (ROWS - 1))) {
        val d = dot.toPx()
        val g = gap.toPx()
        for (row in 0 until ROWS) {
            val on = row < lit
            val top = on && row == lit - 1
            drawCircle(
                when {
                    today && top -> colors.accent
                    else -> colors.textPrimary
                },
                radius = d / 2,
                center = Offset(d / 2, size.height - d / 2 - row * (d + g)),
                alpha = if (on) 0.95f else 0.1f,
            )
        }
    }
}

private fun formatMinutes(min: Int): String = when {
    min < 60 -> "${min}M"
    else -> "${min / 60}H ${min % 60}M"
}
