package com.hkm.carlauncher.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin

/**
 * The launcher's own icons, drawn on a 24×24 grid in one colour (the same shapes as the approved
 * mockup), so the dock looks uniform whatever icons the vendor apps ship with.
 */
object LauncherIcons {
    @Composable fun Close(modifier: Modifier, color: Color) = Icon(modifier) {
        line(6f, 6f, 18f, 18f, color); line(18f, 6f, 6f, 18f, color)
    }

    @Composable fun Play(modifier: Modifier, color: Color) = Icon(modifier) {
        fill(color) { moveTo(7f, 4f); lineTo(20f, 12f); lineTo(7f, 20f); close() }
    }

    @Composable fun Pause(modifier: Modifier, color: Color) = Icon(modifier) {
        fill(color) { rect(6f, 5f, 4f, 14f); rect(14f, 5f, 4f, 14f) }
    }

    @Composable fun Next(modifier: Modifier, color: Color) = Icon(modifier) {
        fill(color) { moveTo(4f, 5f); lineTo(15f, 12f); lineTo(4f, 19f); close(); rect(16f, 5f, 2.5f, 14f) }
    }

    @Composable fun Previous(modifier: Modifier, color: Color) = Icon(modifier) {
        fill(color) { moveTo(20f, 5f); lineTo(9f, 12f); lineTo(20f, 19f); close(); rect(5.5f, 5f, 2.5f, 14f) }
    }

    @Composable fun Note(modifier: Modifier, color: Color) = Icon(modifier) {
        stroke(color) { moveTo(9f, 18f); lineTo(9f, 5f); lineTo(21f, 3f); lineTo(21f, 16f) }
        circle(6f, 18f, 3f, color); circle(18f, 16f, 3f, color)
    }

    @Composable fun Bluetooth(modifier: Modifier, color: Color) = Icon(modifier) {
        stroke(color) { moveTo(7f, 7f); lineTo(17f, 17f); lineTo(12f, 22f); lineTo(12f, 2f); lineTo(17f, 7f); lineTo(7f, 17f) }
    }

    @Composable fun Pin(modifier: Modifier, color: Color) = Icon(modifier) {
        stroke(color) {
            moveTo(12f, 22f)
            cubicTo(12f, 22f, 19f, 15f, 19f, 10f)
            cubicTo(19f, 1f, 5f, 1f, 5f, 10f)
            cubicTo(5f, 15f, 12f, 22f, 12f, 22f)
        }
        circle(12f, 10f, 2.5f, color)
    }

    @Composable fun Video(modifier: Modifier, color: Color) = Icon(modifier) {
        stroke(color) { rect(2f, 5f, 20f, 14f) }
        stroke(color) { moveTo(10f, 9f); lineTo(15f, 12f); lineTo(10f, 15f); close() }
    }

    @Composable fun Store(modifier: Modifier, color: Color) = Icon(modifier) {
        stroke(color) { moveTo(4f, 3f); lineTo(20f, 12f); lineTo(4f, 21f); close() }
    }

    @Composable fun Gear(modifier: Modifier, color: Color) = Icon(modifier) {
        circle(12f, 12f, 3f, color)
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            line(12f + 6.5f * cos(a).toFloat(), 12f + 6.5f * sin(a).toFloat(), 12f + 9.5f * cos(a).toFloat(), 12f + 9.5f * sin(a).toFloat(), color)
        }
    }

    @Composable fun Grid(modifier: Modifier, color: Color) = Icon(modifier) {
        for (x in listOf(6f, 12f, 18f)) for (y in listOf(6f, 12f, 18f)) dot(x, y, 2f, color)
    }

    @Composable fun Plus(modifier: Modifier, color: Color) = Icon(modifier) {
        line(12f, 5f, 12f, 19f, color); line(5f, 12f, 19f, 12f, color)
    }

    @Composable fun Minus(modifier: Modifier, color: Color) = Icon(modifier) {
        line(5f, 12f, 19f, 12f, color)
    }

    @Composable fun Recenter(modifier: Modifier, color: Color) = Icon(modifier) {
        circle(12f, 12f, 7f, color); dot(12f, 12f, 3f, color)
    }

    @Composable
    private fun Icon(modifier: Modifier, draw: IconScope.() -> Unit) {
        Canvas(modifier) { IconScope(this).draw() }
    }
}

/** Draws in 24-unit icon coordinates scaled to the canvas. */
class IconScope(private val scope: DrawScope) {
    private val k = scope.size.minDimension / 24f
    private val strokeStyle = Stroke(width = 2f * k, cap = StrokeCap.Round, join = StrokeJoin.Round)

    fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Color) =
        scope.drawLine(color, Offset(x1 * k, y1 * k), Offset(x2 * k, y2 * k), 2f * k, StrokeCap.Round)

    fun circle(cx: Float, cy: Float, r: Float, color: Color) =
        scope.drawCircle(color, r * k, Offset(cx * k, cy * k), style = strokeStyle)

    fun dot(cx: Float, cy: Float, r: Float, color: Color) =
        scope.drawCircle(color, r * k, Offset(cx * k, cy * k))

    fun stroke(color: Color, build: PathBuilder.() -> Unit) =
        scope.drawPath(PathBuilder(k).apply(build).path, color, style = strokeStyle)

    fun fill(color: Color, build: PathBuilder.() -> Unit) =
        scope.drawPath(PathBuilder(k).apply(build).path, color)
}

class PathBuilder(private val k: Float) {
    val path = Path()
    fun moveTo(x: Float, y: Float) = path.moveTo(x * k, y * k)
    fun lineTo(x: Float, y: Float) = path.lineTo(x * k, y * k)
    fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) =
        path.cubicTo(x1 * k, y1 * k, x2 * k, y2 * k, x3 * k, y3 * k)
    fun close() = path.close()
    fun rect(x: Float, y: Float, w: Float, h: Float) =
        path.addRect(androidx.compose.ui.geometry.Rect(Offset(x * k, y * k), Size(w * k, h * k)))
}
