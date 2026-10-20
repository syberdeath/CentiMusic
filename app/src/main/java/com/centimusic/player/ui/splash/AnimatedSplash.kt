package com.centimusic.player.ui.splash

import android.app.Activity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import com.centimusic.player.ui.theme.AccentRed
import com.centimusic.player.ui.theme.DefaultNmType
import com.centimusic.player.ui.theme.NmIcons
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.floor

private val SplashBlack = Color(0xFF080808)
private const val APP_NAME = "CentiMusic"

/**
 * Same geometry as the launcher mark (res/drawable/ic_launcher_foreground.xml) on its 108-unit grid:
 * five dot columns on a common baseline, the tallest one capped with a red dot.
 */
private val restHeights = floatArrayOf(2f, 4f, 6f, 3f, 5f)
private val columnX = floatArrayOf(36f, 45f, 54f, 63f, 72f)
private const val BASELINE = 71f
private const val STEP = 7f
private const val DOT_RADIUS = 2.6f
private const val ACCENT_COLUMN = 2

/** Equalizer poses the bars bounce through before settling back into the logo. */
private val poses = listOf(
    restHeights,
    floatArrayOf(5f, 2f, 4f, 6f, 3f),
    floatArrayOf(3f, 6f, 2f, 4f, 6f),
    floatArrayOf(6f, 3f, 5f, 2f, 4f),
    restHeights,
)

/**
 * Launch animation: picks up exactly where the system splash (the static mark) leaves off,
 * bounces the dot equalizer, types the wordmark and signs it. Tap anywhere to skip.
 */
@Composable
fun AnimatedSplash(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val finish by rememberUpdatedState(onFinished)
    val bounce = remember { Animatable(0f) }      // 0..poses.lastIndex
    val lift = remember { Animatable(0f) }        // mark moves up and shrinks
    val typing = remember { Animatable(0f) }      // characters of the wordmark revealed
    val signature = remember { Animatable(0f) }   // "Made by Vincent" fade/slide
    val exit = remember { Animatable(1f) }        // whole-screen fade out

    LaunchedEffect(Unit) {
        coroutineScope {
            launch { bounce.animateTo(poses.lastIndex.toFloat(), tween(1100, easing = LinearEasing)) }
            launch {
                delay(850)
                lift.animateTo(1f, tween(550, easing = FastOutSlowInEasing))
            }
            launch {
                delay(1100)
                typing.animateTo(APP_NAME.length.toFloat(), tween(APP_NAME.length * 55, easing = LinearEasing))
            }
            launch {
                delay(1750)
                signature.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
            }
        }
        delay(900)
        exit.animateTo(0f, tween(320))
        finish()
    }

    // The window behind is black during the splash, so status/navigation bar icons must be light.
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val lightStatus = controller?.isAppearanceLightStatusBars
        val lightNav = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            lightStatus?.let { controller.isAppearanceLightStatusBars = it }
            lightNav?.let { controller.isAppearanceLightNavigationBars = it }
        }
    }

    val pulse = rememberInfiniteTransition(label = "heart").animateFloat(
        initialValue = 1f,
        targetValue = 1.22f,
        animationSpec = infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "heartScale",
    )
    val cursorBlink = rememberInfiniteTransition(label = "cursor").animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(380, easing = LinearEasing), RepeatMode.Reverse),
        label = "cursorAlpha",
    )

    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer { alpha = exit.value }
            .background(SplashBlack)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { finish() }
            .semantics { contentDescription = "CentiMusic, made by Vincent with love" },
        contentAlignment = Alignment.Center,
    ) {
        // 288dp matches the size the system splash draws the 108-unit adaptive icon foreground at.
        Canvas(
            Modifier
                .size(288.dp)
                .graphicsLayer {
                    val s = 1f - 0.38f * lift.value
                    scaleX = s
                    scaleY = s
                    translationY = -96.dp.toPx() * lift.value
                },
        ) {
            val unit = size.width / 108f
            val heights = heightsAt(bounce.value)
            for (c in columnX.indices) {
                val h = heights[c]
                val count = 6
                for (i in 0 until count) {
                    val visible = (h - i).coerceIn(0f, 1f)
                    if (visible <= 0f) continue
                    val isTop = c == ACCENT_COLUMN && i == floor(h - 0.001f).toInt()
                    drawCircle(
                        color = if (isTop) AccentRed else Color.White,
                        radius = DOT_RADIUS * unit * (0.4f + 0.6f * visible),
                        center = Offset(columnX[c] * unit, (BASELINE - i * STEP) * unit),
                        alpha = visible,
                    )
                }
            }
        }

        Column(
            Modifier.offset(y = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val shown = typing.value.toInt()
            val typingDone = shown >= APP_NAME.length
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Full string always laid out (hidden part transparent) so the wordmark doesn't drift while typing.
                Text(
                    buildAnnotatedString {
                        append(APP_NAME.take(shown))
                        withStyle(SpanStyle(color = Color.Transparent)) { append(APP_NAME.drop(shown)) }
                    },
                    style = DefaultNmType.screenTitle.copy(fontSize = 38.sp, letterSpacing = 1.sp),
                    color = Color.White,
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier
                        .size(8.dp)
                        .graphicsLayer {
                            alpha = when {
                                typing.value <= 0f -> 0f
                                typingDone -> 1f - signature.value
                                else -> cursorBlink.value
                            }
                        }
                        .background(AccentRed, androidx.compose.foundation.shape.CircleShape),
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.graphicsLayer {
                    alpha = signature.value
                    translationY = 12.dp.toPx() * (1f - signature.value)
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Made by Vincent with", style = DefaultNmType.bodySmall, color = Color(0xFFA0A0A0))
                Spacer(Modifier.width(6.dp))
                Icon(
                    NmIcons.HeartFilled,
                    contentDescription = null,
                    tint = AccentRed,
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer {
                            scaleX = pulse.value
                            scaleY = pulse.value
                        },
                )
            }
        }
    }
}

/** Column heights for a point on the bounce timeline, eased between consecutive poses. */
private fun heightsAt(t: Float): FloatArray {
    val i = floor(t).toInt().coerceIn(0, poses.lastIndex)
    if (i >= poses.lastIndex) return poses.last()
    val f = t - i
    val eased = f * f * (3 - 2 * f) // smoothstep
    val from = poses[i]
    val to = poses[i + 1]
    return FloatArray(from.size) { c -> from[c] + (to[c] - from[c]) * eased }
}
