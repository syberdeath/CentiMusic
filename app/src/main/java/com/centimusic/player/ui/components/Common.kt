package com.centimusic.player.ui.components

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    }
}

@Composable
fun NmDivider(modifier: Modifier = Modifier, startIndent: Dp = 0.dp) {
    HorizontalDivider(modifier.padding(start = startIndent), thickness = 0.5.dp, color = NmTheme.colors.divider)
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
        Text(title, style = NmTheme.type.sectionTitle, color = NmTheme.colors.textPrimary, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(
                action,
                style = NmTheme.type.bodySmall,
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

/** Dot-matrix style loading indicator: three dots pulsing in sequence. */
@Composable
fun DotLoader(modifier: Modifier = Modifier) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "dots")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { i ->
            val alpha = transition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = androidx.compose.animation.core.tween(500, delayMillis = i * 160),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
                ),
                label = "dot$i",
            )
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(NmTheme.colors.textPrimary.copy(alpha = alpha.value)),
            )
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
