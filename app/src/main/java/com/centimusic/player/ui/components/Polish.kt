package com.centimusic.player.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Light, Nothing-style haptic ticks. Respects the system "touch feedback" setting. */
@Stable
class Haptics(private val view: View) {
    /** Buttons: play/pause, skip. */
    fun tick() {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    /** Toggles and confirmations: like, select. */
    fun confirm() {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK,
        )
    }

    /** A swipe gesture crossing its threshold. */
    fun gesture() {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}

/**
 * Single-line text that scrolls only when it doesn't fit, with softly faded edges while it does,
 * and a short gap between repeats.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MarqueeText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    edge: Dp = 18.dp,
) {
    BoxWithConstraints(modifier) {
        val measurer = rememberTextMeasurer()
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val overflows = remember(text, style, widthPx) {
            measurer.measure(text, style, maxLines = 1, softWrap = false).size.width > widthPx
        }
        if (!overflows) {
            Text(text, style = style, color = color, maxLines = 1, overflow = TextOverflow.Clip)
        } else {
            Text(
                text,
                style = style,
                color = color,
                maxLines = 1,
                modifier = Modifier
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        val e = edge.toPx()
                        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = 0f, endX = e), blendMode = BlendMode.DstIn)
                        drawRect(
                            Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width - e, endX = size.width),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                    .basicMarquee(
                        iterations = Int.MAX_VALUE,
                        initialDelayMillis = 1_500,
                        repeatDelayMillis = 2_000,
                        spacing = MarqueeSpacing(48.dp),
                    ),
            )
        }
    }
}
