package com.centimusic.player.ui.splash

import android.app.Activity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.centimusic.player.ui.components.rememberHaptics
import com.centimusic.player.ui.theme.AccentRed
import com.centimusic.player.ui.theme.DefaultNmType
import com.centimusic.player.ui.theme.NmIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

enum class SplashStyle { BOOT, GLYPH, PULSE, VINYL }

/** What to play: the intro [style], the full or quick version, and the name to greet. */
data class SplashConfig(val style: SplashStyle, val full: Boolean, val name: String)

private val SplashBlack = Color(0xFF080808)
private const val APP_NAME = "CentiMusic"
private const val SCRAMBLE = "ABCDEFGHJKLMNPRSTUVWXYZ0123456789#*+"

// The CentiMusic mark on its 108-unit grid (same as res/drawable/ic_launcher_foreground.xml).
private val restHeights = intArrayOf(2, 4, 6, 3, 5)
private val columnX = floatArrayOf(36f, 45f, 54f, 63f, 72f)
private const val BASELINE = 71f
private const val STEP = 7f
private const val DOT_RADIUS = 2.6f
private const val ACCENT_COLUMN = 2

/** All timings in milliseconds. */
private class Timeline(
    val intro: Float,
    val liftStart: Float,
    val liftDur: Float,
    val wordStart: Float,
    val stagger: Float,
    val scrambleLead: Float,
    val greetStart: Float,
    val sigStart: Float,
    val end: Float,
)

// Unhurried pacing: ~7.4 s full, ~3.8 s quick.
private val FULL = Timeline(intro = 3300f, liftStart = 3100f, liftDur = 950f, wordStart = 3700f, stagger = 110f, scrambleLead = 420f,
    greetStart = 5000f, sigStart = 5500f, end = 7400f)
private val QUICK = Timeline(intro = 1200f, liftStart = 1000f, liftDur = 750f, wordStart = 1300f, stagger = 70f, scrambleLead = 260f,
    greetStart = 2100f, sigStart = 2400f, end = 3800f)

private fun clamp01(x: Float) = x.coerceIn(0f, 1f)
private fun easeOutCubic(x: Float) = 1f - (1f - clamp01(x)).pow(3)
private fun easeInOut(x: Float): Float { val c = clamp01(x); return c * c * (3f - 2f * c) }
private fun easeOutBack(x: Float): Float { val c = clamp01(x) - 1f; return 1f + 2.2f * c * c * c + 1.2f * c * c }
private fun bounce(x: Float): Float {
    var t = clamp01(x)
    val n = 7.5625f
    val d = 2.75f
    return when {
        t < 1f / d -> n * t * t
        t < 2f / d -> { t -= 1.5f / d; n * t * t + 0.75f }
        t < 2.5f / d -> { t -= 2.25f / d; n * t * t + 0.9375f }
        else -> { t -= 2.625f / d; n * t * t + 0.984375f }
    }
}

/** "Good morning" / "Good afternoon" / "Good evening" / "Late night", by the clock. */
fun timeOfDayGreeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    in 18..21 -> "Good evening"
    else -> "Late night"
}

private fun greeting(name: String): String {
    val part = timeOfDayGreeting()
    return if (name.isBlank()) part else "$part, ${name.trim()}"
}

/**
 * CentiMusic launch animation. Four intros ([SplashStyle]) form the dot-equalizer mark, then a
 * shared outro lifts it, scrambles in the wordmark, greets the user and signs off with a
 * heartbeat. [SplashConfig.full] = false plays a ~3.8 s quick version. Tap anywhere to skip.
 */
@Composable
fun AnimatedSplash(
    config: SplashConfig,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    /** Called when the splash starts fading out, so the app underneath can start drawing again. */
    onReveal: () -> Unit = {},
) {
    val finish by rememberUpdatedState(onFinished)
    val reveal by rememberUpdatedState(onReveal)
    val tl = if (config.full) FULL else QUICK
    val clock = remember(config) { Animatable(0f) }
    val exit = remember(config) { Animatable(1f) }
    val haptics = rememberHaptics()

    LaunchedEffect(config) {
        // Let the app underneath finish its first heavy frames before starting, so the opening
        // of the animation isn't skipped on slower phones.
        repeat(3) { withFrameNanos { } }
        delay(120)
        // Haptic cues: the mark completing, then the heartbeat.
        launch { delay(tl.intro.toLong()); haptics.confirm() }
        if (config.full) launch { delay((tl.sigStart + 120).toLong()); haptics.tick() }
        clock.animateTo(tl.end, tween(tl.end.toInt(), easing = LinearEasing))
        reveal()
        exit.animateTo(0f, tween(600))
        finish()
    }
    LightBars()

    // The clock is only read inside draw / graphicsLayer blocks (and a derived wordmark), so each
    // frame just redraws instead of recomposing the whole screen.
    val wordmark by remember(config) {
        derivedStateOf { scrambledWordmark((clock.value / 45f).toInt() * 45f, tl) }
    }
    val grid = remember { DotGridCache() }

    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer { alpha = exit.value }
            .background(SplashBlack)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { reveal(); finish() }
            .semantics { contentDescription = "CentiMusic, made by Vincent with love" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val t = clock.value
            val introP = clamp01(t / tl.intro)
            val outro = clamp01((t - tl.intro) / 650f)
            val lift = easeInOut((t - tl.liftStart) / tl.liftDur)
            val unit = 288.dp.toPx() / 108f * (1f - 0.28f * lift)
            val center = Offset(size.width / 2f, size.height / 2f - 64.dp.toPx() * lift)
            val logo = Logo(center, unit)
            if (config.full) {
                when (config.style) {
                    SplashStyle.BOOT -> boot(logo, introP, outro, grid)
                    SplashStyle.GLYPH -> glyph(logo, introP, outro)
                    SplashStyle.PULSE -> pulse(logo, introP, outro)
                    SplashStyle.VINYL -> vinyl(logo, introP, outro)
                }
            } else {
                // Quick version (later launches the same day): the mark simply pops in.
                val q = easeOutBack(introP)
                drawLogo(logo) { _, _ -> q }
            }
        }

        Column(Modifier.offset(y = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                wordmark,
                style = DefaultNmType.screenTitle.copy(fontSize = 38.sp, letterSpacing = 1.sp),
                color = Color.White,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                greeting(config.name),
                style = DefaultNmType.dotCaption.copy(fontSize = 14.sp),
                color = Color(0xFFA0A0A0),
                modifier = Modifier.graphicsLayer {
                    val greet = easeOutCubic((clock.value - tl.greetStart) / 700f)
                    alpha = greet
                    translationY = 8.dp.toPx() * (1f - greet)
                },
            )
        }
        // Signature sits at the bottom of the screen.
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 40.dp)
                .graphicsLayer {
                    val sig = easeOutCubic((clock.value - tl.sigStart) / 700f)
                    alpha = sig
                    translationY = 12.dp.toPx() * (1f - sig)
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Made by Vincent with", style = DefaultNmType.bodySmall, color = Color(0xFF8A8A8A))
            Spacer(Modifier.width(6.dp))
            Icon(
                NmIcons.HeartFilled,
                contentDescription = null,
                tint = AccentRed,
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer {
                        val beat = heartbeat(clock.value - tl.sigStart)
                        scaleX = beat
                        scaleY = beat
                    },
            )
        }
    }
}

/** Letters settle left to right, each flickering through random dot-matrix glyphs first. */
private fun scrambledWordmark(t: Float, tl: Timeline) = buildAnnotatedString {
    APP_NAME.forEachIndexed { k, ch ->
        val settle = tl.wordStart + k * tl.stagger
        when {
            t >= settle -> append(ch)
            t >= settle - tl.scrambleLead -> withStyle(SpanStyle(color = Color(0xFF6B6B6B))) {
                val frame = (t / 45f).toInt()
                append(SCRAMBLE[(k * 31 + frame * 17).mod(SCRAMBLE.length)])
            }
            else -> withStyle(SpanStyle(color = Color.Transparent)) { append(ch) }
        }
    }
}

/** "Lub-dub" scale, repeating every 1.3 s (a resting heartbeat). */
private fun heartbeat(sinceStart: Float): Float {
    if (sinceStart < 0f) return 1f
    val x = sinceStart % 1300f
    fun peak(center: Float, height: Float) = (1f - abs(x - center) / 120f).coerceAtLeast(0f) * height
    return 1f + peak(120f, 0.35f) + peak(380f, 0.22f)
}

// region Drawing

private class Logo(val center: Offset, val unit: Float) {
    fun dot(c: Int, i: Int) = Offset(
        center.x + (columnX[c] - 54f) * unit,
        center.y + (BASELINE - i * STEP - 54f) * unit,
    )
    val radius get() = DOT_RADIUS * unit
}

private inline fun forEachLogoDot(block: (c: Int, i: Int, accent: Boolean) -> Unit) {
    for (c in columnX.indices) for (i in 0 until restHeights[c]) block(c, i, c == ACCENT_COLUMN && i == restHeights[c] - 1)
}

/** Draws the mark; [appear] (0..1) per dot controls its size and opacity. */
private fun DrawScope.drawLogo(logo: Logo, includeAccent: Boolean = true, appear: (c: Int, i: Int) -> Float) {
    forEachLogoDot { c, i, accent ->
        if (accent && !includeAccent) return@forEachLogoDot
        val a = appear(c, i)
        if (a <= 0f) return@forEachLogoDot
        drawCircle(if (accent) AccentRed else Color.White, radius = logo.radius * (0.35f + 0.65f * a.coerceAtMost(1.2f)), center = logo.dot(c, i), alpha = clamp01(a))
    }
}

/** The red dot drops in last with a bounce. */
private fun DrawScope.dropAccent(logo: Logo, q: Float) {
    if (q <= 0f) return
    val target = logo.dot(ACCENT_COLUMN, restHeights[ACCENT_COLUMN] - 1)
    val y = target.y - 70.dp.toPx() * (1f - bounce(q))
    drawCircle(AccentRed, radius = logo.radius, center = Offset(target.x, y), alpha = clamp01(q * 3f))
}

/** Background dot positions, computed once per screen size. */
private class DotGridCache {
    private var forSize = Size.Zero
    var xs = FloatArray(0); private set
    var ys = FloatArray(0); private set
    val buckets = Array(BUCKETS) { ArrayList<Offset>(256) }

    fun ensure(size: Size, step: Float) {
        if (size == forSize) return
        forSize = size
        val cols = (size.width / step).toInt() + 1
        val rows = (size.height / step).toInt() + 1
        val ox = (size.width % step) / 2f
        val oy = (size.height % step) / 2f
        xs = FloatArray(cols * rows) { ox + (it % cols) * step }
        ys = FloatArray(cols * rows) { oy + (it / cols) * step }
    }
}

private const val BUCKETS = 8

/** 1 · Boot-up: a wave lights the dot grid from the centre, revealing the mark. */
private fun DrawScope.boot(logo: Logo, p: Float, outro: Float, grid: DotGridCache) {
    grid.ensure(size, 22.dp.toPx())
    val maxDist = hypot(size.width, size.height) / 2f
    val front = easeOutCubic(p) * maxDist * 1.25f
    val band = 46.dp.toPx()
    // Sort dots into a few brightness levels and draw each level in one call, instead of
    // hundreds of separate circles per frame.
    grid.buckets.forEach { it.clear() }
    for (k in grid.xs.indices) {
        val x = grid.xs[k]
        val y = grid.ys[k]
        val d = hypot(x - logo.center.x, y - logo.center.y)
        val z = (d - front) / band
        val wave = if (z > -3f && z < 3f) exp(-z * z) else 0f
        val lit = if (d < front) 0.07f else 0.025f
        val a = (lit + 0.6f * wave * (1f - outro)).coerceIn(0f, 1f)
        grid.buckets[(a * (BUCKETS - 1) / 0.67f).toInt().coerceIn(0, BUCKETS - 1)].add(Offset(x, y))
    }
    val dot = 2.2.dp.toPx()
    grid.buckets.forEachIndexed { level, points ->
        if (points.isEmpty()) return@forEachIndexed
        val alpha = (0.025f + level * 0.67f / (BUCKETS - 1)).coerceAtMost(1f)
        drawPoints(points, PointMode.Points, Color.White, strokeWidth = dot, cap = StrokeCap.Round, alpha = alpha)
    }
    drawLogo(logo, includeAccent = false) { c, i ->
        val d = hypot(logo.dot(c, i).x - logo.center.x, logo.dot(c, i).y - logo.center.y)
        (front - d) / 24.dp.toPx()
    }
    dropAccent(logo, (p - 0.78f) / 0.22f + outro)
}

/** 2 · Glyph: light segments switch on around the mark one by one, then fade. */
private fun DrawScope.glyph(logo: Logo, p: Float, outro: Float) {
    val n = 16
    val r = 118.dp.toPx() * (logo.unit / (288.dp.toPx() / 108f))
    val gap = 8f
    for (k in 0 until n) {
        val on = clamp01((p * 1.15f - k.toFloat() / n) * n / 2f)
        if (on <= 0f) continue
        drawArc(
            color = if (k == n - 1) AccentRed else Color.White,
            startAngle = -90f + k * 360f / n + gap / 2f,
            sweepAngle = 360f / n - gap,
            useCenter = false,
            topLeft = Offset(logo.center.x - r, logo.center.y - r),
            size = Size(r * 2f, r * 2f),
            alpha = on * (1f - outro),
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
        )
    }
    drawLogo(logo, includeAccent = false) { c, _ -> (p - 0.35f - c * 0.08f) / 0.18f }
    dropAccent(logo, (p - 0.82f) / 0.18f + outro)
}

/** 3 · Pulse: a red dot sends out ripples; each ripple leaves the mark's dots behind. */
private fun DrawScope.pulse(logo: Logo, p: Float, outro: Float) {
    val maxR = 170.dp.toPx()
    var firstRipple = 0f
    for (k in 0 until 3) {
        val q = (p - 0.12f - k * 0.17f) / 0.5f
        if (q <= 0f || q >= 1f) continue
        val r = easeOutCubic(q) * maxR
        if (k == 0) firstRipple = r
        drawCircle(
            if (k == 0) AccentRed else Color.White,
            radius = r,
            center = logo.center,
            alpha = (1f - q).pow(1.5f) * 0.7f * (1f - outro),
            style = Stroke(width = 1.5.dp.toPx()),
        )
    }
    if (p >= 0.62f) firstRipple = maxR
    drawLogo(logo, includeAccent = false) { c, i ->
        val d = hypot(logo.dot(c, i).x - logo.center.x, logo.dot(c, i).y - logo.center.y)
        (firstRipple - d) / 18.dp.toPx()
    }
    // The red dot starts at the centre and travels to its place on the mark.
    val appear = clamp01(p / 0.12f)
    val travel = easeInOut((p - 0.74f) / 0.26f)
    val target = logo.dot(ACCENT_COLUMN, restHeights[ACCENT_COLUMN] - 1)
    val pos = Offset(logo.center.x + (target.x - logo.center.x) * travel, logo.center.y + (target.y - logo.center.y) * travel)
    drawCircle(AccentRed, radius = logo.radius * (1f + 1.2f * (1f - travel)) * easeOutBack(appear), center = pos, alpha = appear)
}

/** 4 · Vinyl: the record spins up and slows, then the equalizer bars pop out of it. */
private fun DrawScope.vinyl(logo: Logo, p: Float, outro: Float) {
    val r = 140.dp.toPx() * (1f - 0.25f * outro)
    val fade = 1f - outro
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFF2A2A2A), Color(0xFF0B0B0B)), center = Offset(logo.center.x - r * 0.3f, logo.center.y - r * 0.3f), radius = r * 1.4f),
        radius = r,
        center = logo.center,
        alpha = fade,
    )
    for (g in 1..9) {
        drawCircle(Color.White, radius = r * (0.38f + g * 0.065f), center = logo.center, alpha = 0.05f * fade, style = Stroke(width = 1f))
    }
    // Spin: fast at first, easing to a stop — shown by a light streak and the label's red dot.
    val angle = ((1f - easeOutCubic(p)) * 900f) * (PI / 180f).toFloat()
    drawArc(
        Color.White,
        startAngle = angle * 180f / PI.toFloat(),
        sweepAngle = 38f,
        useCenter = false,
        topLeft = Offset(logo.center.x - r * 0.8f, logo.center.y - r * 0.8f),
        size = Size(r * 1.6f, r * 1.6f),
        alpha = 0.22f * fade,
        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
    )
    drawCircle(Color(0xFF151515), radius = r * 0.33f, center = logo.center, alpha = fade)
    drawCircle(AccentRed, radius = 4.dp.toPx(), center = Offset(logo.center.x + cos(angle) * r * 0.2f, logo.center.y + sin(angle) * r * 0.2f), alpha = fade * (1f - easeInOut((p - 0.5f) / 0.2f)))
    // Bars pop up column by column from the baseline.
    drawLogo(logo, includeAccent = false) { c, i ->
        val h = easeOutBack((p - 0.52f - c * 0.06f) / 0.24f) * restHeights[c]
        h - i
    }
    dropAccent(logo, (p - 0.84f) / 0.16f + outro)
}

// endregion

/** The splash is always black, so system bar icons must be light whatever the app theme. */
@Composable
private fun LightBars() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
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
