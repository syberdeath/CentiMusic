package com.centimusic.player.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.centimusic.player.ui.theme.NmIcons
import com.centimusic.player.ui.theme.NmTheme
import com.centimusic.player.ui.theme.Spacing

/** 48dp touch target around a 24dp icon. */
@Composable
fun NmIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = NmTheme.colors.textPrimary,
    iconSize: Dp = 24.dp,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .size(Spacing.touchTarget)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = if (enabled) tint else tint.copy(alpha = 0.3f), modifier = Modifier.size(iconSize))
    }
}

/** Icon on a soft circular surface — used for library categories and settings rows. */
@Composable
fun CircleIcon(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 48.dp, tint: Color = NmTheme.colors.textPrimary) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(NmTheme.colors.surfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.46f))
    }
}

/** The red circular play button used on collection headers. */
@Composable
fun AccentPlayButton(onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 52.dp, contentDescription: String = "Play") {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(NmTheme.colors.accent)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(NmIcons.Play, contentDescription, tint = NmTheme.colors.onAccent, modifier = Modifier.size(size * 0.42f))
    }
}

/** Compact outlined pill used for secondary actions (Shuffle, Add songs, Browse files). */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    filled: Boolean = false,
    trailingIcon: ImageVector? = null,
) {
    val colors = NmTheme.colors
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .height(40.dp)
            .clip(shape)
            .background(if (filled) colors.inverse else colors.surfaceHigh)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.l),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val content = if (filled) colors.onInverse else colors.textPrimary
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.s))
        }
        Text(text, style = NmTheme.type.label, color = content)
        if (trailingIcon != null) {
            Spacer(Modifier.width(Spacing.xs))
            Icon(trailingIcon, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun NmDivider(modifier: Modifier = Modifier, startIndent: Dp = 0.dp) {
    // Nothing OS-style dotted rule.
    val color = NmTheme.colors.textPrimary
    androidx.compose.foundation.Canvas(
        modifier
            .padding(start = startIndent)
            .fillMaxWidth()
            .height(2.dp),
    ) {
        val step = 5.dp.toPx()
        val r = 0.7.dp.toPx()
        var x = r
        while (x < size.width) {
            drawCircle(color, radius = r, center = androidx.compose.ui.geometry.Offset(x, size.height / 2), alpha = 0.22f)
            x += step
        }
    }
}

/** Small uppercase dot-matrix label with a red dot: "● COLLECTION". */
@Composable
fun Kicker(text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(NmTheme.colors.accent),
        )
        Spacer(Modifier.width(Spacing.s))
        Text(text.uppercase(), style = NmTheme.type.dotCaption, color = NmTheme.colors.textSecondary)
    }
}

/** Faint dot lattice drawn behind content, like Nothing OS wallpapers and widgets. */
fun Modifier.dotGrid(color: androidx.compose.ui.graphics.Color, spacing: Dp = 18.dp, alpha: Float = 0.07f): Modifier =
    this.then(
        Modifier.drawBehind {
            val step = spacing.toPx()
            val r = 0.9.dp.toPx()
            var y = step / 2
            while (y < size.height) {
                var x = step / 2
                while (x < size.width) {
                    drawCircle(color, radius = r, center = androidx.compose.ui.geometry.Offset(x, y), alpha = alpha)
                    x += step
                }
                y += step
            }
        },
    )

/**
 * Nothing-style widget tile: rounded surface, icon in a circle, a big dot-matrix value and a small
 * uppercase label.
 */
@Composable
fun StatTile(
    icon: ImageVector,
    value: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: androidx.compose.ui.graphics.Color = NmTheme.colors.textPrimary,
    height: Dp = 118.dp,
) {
    val colors = NmTheme.colors
    Column(
        modifier
            .height(height)
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(Spacing.l),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(colors.surfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(17.dp))
        }
        Column {
            Text(value, style = NmTheme.type.screenTitle.copy(fontSize = 34.sp, lineHeight = 36.sp), color = colors.textPrimary, maxLines = 1)
            Text(label.uppercase(), style = NmTheme.type.dotCaption.copy(fontSize = 11.sp), color = colors.textSecondary, maxLines = 1)
        }
    }
}

/** "Recently Played ........ See all" */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(NmTheme.colors.accent),
        )
        Spacer(Modifier.width(Spacing.s))
        Text(title, style = NmTheme.type.sectionTitle, color = NmTheme.colors.textPrimary, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(
                action,
                style = NmTheme.type.dotCaption,
                color = NmTheme.colors.textSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = Spacing.xs, vertical = Spacing.s),
            )
        }
    }
}

/** Large dot-matrix screen title with optional trailing actions (Library, Search, Settings). */
@Composable
fun ScreenTitle(title: String, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = Spacing.gutter, end = Spacing.s, top = Spacing.l, bottom = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = NmTheme.type.screenTitle, color = NmTheme.colors.textPrimary, modifier = Modifier.weight(1f))
        actions()
    }
}

/** Back arrow + optional small title + actions, for detail screens. */
@Composable
fun TopBar(onBack: () -> Unit, modifier: Modifier = Modifier, title: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NmIconButton(NmIcons.Back, "Back", onBack)
        Text(
            title.orEmpty(),
            style = NmTheme.type.title,
            color = NmTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

/** List row with a circular icon, title, subtitle and chevron (Library categories, Settings). */
@Composable
fun NavigationRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    iconSize: Dp = 48.dp,
    showChevron: Boolean = true,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.gutter, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIcon(icon, size = iconSize)
        Spacer(Modifier.width(Spacing.l))
        Column(Modifier.weight(1f)) {
            Text(title, style = NmTheme.type.title, color = NmTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Spacer(Modifier.height(Spacing.xxs))
                Text(subtitle, style = NmTheme.type.bodySmall, color = NmTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (value != null) {
            Text(value, style = NmTheme.type.bodySmall, color = NmTheme.colors.textSecondary)
            Spacer(Modifier.width(Spacing.xs))
        }
        if (showChevron) {
            Icon(NmIcons.ChevronRight, contentDescription = null, tint = NmTheme.colors.textTertiary, modifier = Modifier.size(18.dp))
        }
    }
}

/** Centered message with an optional action, for empty and error states. */
@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = NmIcons.Note,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xxxl, vertical = Spacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            CircleIcon(icon, size = 64.dp)
            Box(
                Modifier
                    .padding(top = 2.dp, end = 2.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(NmTheme.colors.accent),
            )
        }
        Spacer(Modifier.height(Spacing.xl))
        Text(title, style = NmTheme.type.sectionTitle, color = NmTheme.colors.textPrimary)
        Spacer(Modifier.height(Spacing.s))
        Text(
            message,
            style = NmTheme.type.body,
            color = NmTheme.colors.textSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Spacing.xl))
            PillButton(actionLabel, onAction, filled = true)
        }
        if (secondaryLabel != null && onSecondary != null) {
            Spacer(Modifier.height(Spacing.s))
            PillButton(secondaryLabel, onSecondary)
        }
    }
}

/** Dot-matrix "listening" loader: a 5×5 grid of dots bouncing like a tiny equalizer. Purely decorative. */
@Composable
fun DotLoader(modifier: Modifier = Modifier) {
    val color = NmTheme.colors.textPrimary
    val accent = NmTheme.colors.accent
    val phase by rememberInfiniteTransition(label = "loader").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_100, easing = androidx.compose.animation.core.LinearEasing)),
        label = "phase",
    )
    androidx.compose.foundation.Canvas(modifier.size(width = 40.dp, height = 28.dp)) {
        val cols = 5
        val rows = 5
        val sx = size.width / cols
        val sy = size.height / rows
        val r = minOf(sx, sy) * 0.3f
        for (c in 0 until cols) {
            val wave = 0.5f + 0.5f * kotlin.math.sin(2f * kotlin.math.PI.toFloat() * (phase + c * 0.19f))
            val lit = 1 + (wave * (rows - 1)).toInt()
            for (row in 0 until rows) {
                val on = row < lit
                val top = row == lit - 1 && c == 2
                drawCircle(
                    if (top) accent else color,
                    radius = r,
                    center = androidx.compose.ui.geometry.Offset(sx * (c + 0.5f), size.height - sy * (row + 0.5f)),
                    alpha = if (on) 0.95f else 0.12f,
                )
            }
        }
    }
}

@Composable
fun LoadingState(label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(Spacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DotLoader()
        Spacer(Modifier.height(Spacing.l))
        Text(label, style = NmTheme.type.bodySmall, color = NmTheme.colors.textSecondary)
    }
}

fun bottomContentPadding(chrome: Dp, extra: Dp = Spacing.l) = PaddingValues(bottom = chrome + extra)
