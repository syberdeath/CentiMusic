package com.centimusic.files.ui

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.centimusic.files.data.FileItem
import com.centimusic.files.data.FileKind
import com.centimusic.files.data.Thumbnails
import com.centimusic.files.data.formatSize
import com.centimusic.files.theme.FIcons
import com.centimusic.files.theme.NmTheme
import com.centimusic.files.theme.Spacing
import java.text.DateFormat
import java.util.Date

fun FileKind.icon(): ImageVector = when (this) {
    FileKind.FOLDER -> FIcons.Folder
    FileKind.IMAGE -> FIcons.Image
    FileKind.VIDEO -> FIcons.Video
    FileKind.AUDIO -> FIcons.Audio
    FileKind.DOCUMENT -> FIcons.Document
    FileKind.APK -> FIcons.Apk
    FileKind.ARCHIVE -> FIcons.Archive
    FileKind.OTHER -> FIcons.File
}

private val dateFormat: DateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM)

fun FileItem.subtitle(): String {
    val date = if (modified > 0) dateFormat.format(Date(modified)) else ""
    val what = when {
        isDirectory && childCount >= 0 -> if (childCount == 1) "1 item" else "$childCount items"
        isDirectory -> formatSize(size)
        else -> formatSize(size)
    }
    return listOf(what, date).filter { it.isNotEmpty() }.joinToString("  ·  ")
}

/** Screen header: big dot-matrix title with optional back button and trailing actions. */
@Composable
fun Header(
    title: String,
    onBack: (() -> Unit)? = null,
    kicker: String? = null,
    actions: @Composable () -> Unit = {},
) {
    val c = NmTheme.colors
    Column(Modifier.fillMaxWidth().padding(start = Spacing.gutter, end = Spacing.s, top = Spacing.l, bottom = Spacing.s)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            if (onBack != null) {
                IconButtonNm(FIcons.Back, "Back", onBack)
                Spacer(Modifier.width(Spacing.xs))
            }
            Spacer(Modifier.weight(1f))
            actions()
        }
        if (kicker != null) {
            Text(kicker.uppercase(), style = NmTheme.type.dotCaption, color = c.accent)
            Spacer(Modifier.height(Spacing.xs))
        }
        Text(
            title.uppercase(),
            style = NmTheme.type.screenTitle,
            color = c.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = Spacing.m),
        )
    }
}

@Composable
fun IconButtonNm(icon: ImageVector, description: String, onClick: () -> Unit, tint: Color = NmTheme.colors.textPrimary) {
    Box(
        Modifier.size(Spacing.touchTarget).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** Pill button in the Nothing style: outlined by default, solid red when [primary]. */
@Composable
fun PillButton(text: String, onClick: () -> Unit, primary: Boolean = false, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val c = NmTheme.colors
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .clip(shape)
            .then(if (primary) Modifier.background(c.accent) else Modifier.border(1.dp, c.divider, shape))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.xl, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (primary) c.onAccent else c.textPrimary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.s))
        }
        Text(text.uppercase(), style = NmTheme.type.label, color = if (primary) c.onAccent else c.textPrimary)
    }
}

/** Storage gauge drawn as a row of dots, used/free, with the last used dot in red. */
@Composable
fun DotGauge(fraction: Float, modifier: Modifier = Modifier, dots: Int = 28, dotSize: Dp = 7.dp) {
    val c = NmTheme.colors
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(900), label = "gauge")
    val filled = (animated * dots).toInt().coerceIn(if (fraction > 0f) 1 else 0, dots)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val gap = ((maxWidth - dotSize * dots) / (dots - 1)).coerceAtLeast(1.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            repeat(dots) { i ->
                val color = when {
                    i == filled - 1 -> c.accent
                    i < filled -> c.textPrimary
                    else -> c.surfaceHigh
                }
                Box(Modifier.size(dotSize).clip(CircleShape).background(color))
            }
        }
    }
}

/** A thumbnail when one exists (images, videos, artwork, APK icons), otherwise the kind's icon. */
@Composable
fun FileThumb(item: FileItem, thumbs: Thumbnails, size: Dp, modifier: Modifier = Modifier, corner: Dp = Spacing.thumbCorner) {
    val c = NmTheme.colors
    val px = with(LocalDensity.current) { size.roundToPx() }.coerceAtMost(512)
    val wantsThumb = item.kind in setOf(FileKind.IMAGE, FileKind.VIDEO, FileKind.AUDIO, FileKind.APK)
    var bmp by remember(item.path, item.modified) { mutableStateOf<Bitmap?>(thumbs.cached(item)) }
    if (wantsThumb && bmp == null) {
        LaunchedEffect(item.path, item.modified) { bmp = thumbs.load(item, px) }
    }
    Box(
        modifier.size(size).clip(RoundedCornerShape(corner)).background(c.surfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) {
            Image(
                b.asImageBitmap(), null,
                contentScale = if (item.kind == FileKind.APK) ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier.fillMaxSize().then(if (item.kind == FileKind.APK) Modifier.padding(size / 6) else Modifier),
            )
        } else {
            Icon(
                item.kind.icon(), null,
                tint = if (item.isDirectory) c.textPrimary else c.textSecondary,
                modifier = Modifier.size(size * 0.46f),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRow(
    item: FileItem,
    thumbs: Thumbnails,
    selected: Boolean,
    selecting: Boolean,
    favourite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMore: () -> Unit,
    subtitle: String = item.subtitle(),
) {
    val c = NmTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) c.surface else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = Spacing.gutter, end = Spacing.xs)
            .height(Spacing.rowHeight + 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            FileThumb(item, thumbs, 44.dp)
            if (selected) SelectedBadge(Modifier.align(Alignment.BottomEnd))
        }
        Spacer(Modifier.width(Spacing.l))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.name, style = NmTheme.type.title, color = c.textPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                if (favourite) {
                    Spacer(Modifier.width(Spacing.xs))
                    Icon(FIcons.StarFilled, "Favourite", tint = c.accent, modifier = Modifier.size(12.dp))
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = NmTheme.type.bodySmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (selecting) {
            Box(Modifier.size(Spacing.touchTarget), contentAlignment = Alignment.Center) { SelectCircle(selected) }
        } else {
            IconButtonNm(FIcons.More, "More", onMore, tint = c.textSecondary)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileGridCell(
    item: FileItem,
    thumbs: Thumbnails,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val c = NmTheme.colors
    Column(
        Modifier
            .clip(RoundedCornerShape(Spacing.artworkCorner))
            .background(if (selected) c.surface else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(Spacing.xs),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f)) {
            FileThumb(item, thumbs, maxWidth, corner = Spacing.artworkCorner)
            if (selected) SelectedBadge(Modifier.align(Alignment.TopEnd).padding(Spacing.s))
        }
        Spacer(Modifier.height(Spacing.s))
        Text(
            item.name, style = NmTheme.type.bodySmall, color = c.textPrimary, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = Spacing.xs),
        )
        Text(
            if (item.isDirectory) item.subtitle().substringBefore("  ·") else formatSize(item.size),
            style = NmTheme.type.caption, color = c.textTertiary, maxLines = 1,
            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = 2.dp),
        )
    }
}

@Composable
fun SelectedBadge(modifier: Modifier = Modifier) {
    val c = NmTheme.colors
    Box(
        modifier.size(20.dp).clip(CircleShape).background(c.accent),
        contentAlignment = Alignment.Center,
    ) { Icon(FIcons.Check, null, tint = c.onAccent, modifier = Modifier.size(14.dp)) }
}

@Composable
fun SelectCircle(selected: Boolean) {
    val c = NmTheme.colors
    if (selected) SelectedBadge(Modifier.size(22.dp))
    else Box(Modifier.size(22.dp).border(1.4.dp, c.textTertiary, CircleShape))
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.xxl, bottom = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text.uppercase(), style = NmTheme.type.dotCaption, color = NmTheme.colors.textSecondary, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun EmptyState(title: String, body: String, icon: ImageVector = FIcons.Folder) {
    val c = NmTheme.colors
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.xxxl, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = c.textTertiary, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(Spacing.l))
        Text(title.uppercase(), style = NmTheme.type.sectionTitle, color = c.textPrimary)
        Spacer(Modifier.height(Spacing.s))
        Text(body, style = NmTheme.type.body, color = c.textSecondary)
    }
}

/** Dot loader: three dots pulsing in turn. */
@Composable
fun DotLoader(modifier: Modifier = Modifier) {
    val c = NmTheme.colors
    var phase by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) { kotlinx.coroutines.delay(280); phase = (phase + 1) % 3 }
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(3) { i ->
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (i == phase) c.accent else c.surfaceHigh))
        }
    }
}

/** Bottom pill with a dot-matrix message, like a Glyph toast. */
@Composable
fun GlyphToast(message: String?, modifier: Modifier = Modifier) {
    val c = NmTheme.colors
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier,
    ) {
        var last by remember { mutableStateOf("") }
        if (message != null) last = message
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(c.inverse).padding(horizontal = Spacing.xl, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(c.accent))
            Spacer(Modifier.width(Spacing.m))
            Text(last.uppercase(), style = NmTheme.type.label, color = c.onInverse)
        }
    }
}
