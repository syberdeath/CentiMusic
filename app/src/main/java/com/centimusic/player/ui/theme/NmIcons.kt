package com.centimusic.player.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The app's icon set: drawn on a 24-unit grid with a single 1.6 stroke weight and round caps,
 * so every glyph shares the same visual weight. Tinted by [androidx.compose.material3.Icon].
 */
object NmIcons {
    private const val STROKE = 1.6f

    private class Builder(name: String) {
        val builder = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        fun stroke(width: Float = STROKE, block: PathBuilder.() -> Unit) {
            builder.path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block,
            )
        }
        /** Filled shapes get a matching stroke so their corners are rounded like the outline icons. */
        fun fill(block: PathBuilder.() -> Unit) {
            builder.path(
                fill = SolidColor(Color.Black),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.2f,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block,
            )
        }
    }

    private fun icon(name: String, block: Builder.() -> Unit): ImageVector = Builder(name).apply(block).builder.build()

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcTo(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = cx + r, y1 = cy)
        arcTo(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = cx - r, y1 = cy)
        close()
    }

    private fun PathBuilder.heartPath() {
        moveTo(12f, 19.6f)
        curveTo(12f, 19.6f, 3.8f, 14.8f, 3.8f, 9.3f)
        curveTo(3.8f, 6.7f, 5.8f, 4.8f, 8.2f, 4.8f)
        curveTo(9.8f, 4.8f, 11.2f, 5.7f, 12f, 7.1f)
        curveTo(12.8f, 5.7f, 14.2f, 4.8f, 15.8f, 4.8f)
        curveTo(18.2f, 4.8f, 20.2f, 6.7f, 20.2f, 9.3f)
        curveTo(20.2f, 14.8f, 12f, 19.6f, 12f, 19.6f)
        close()
    }

    val Home by lazy {
        icon("Home") {
            stroke {
                moveTo(4.5f, 10.2f); lineTo(12f, 4.2f); lineTo(19.5f, 10.2f)
                lineTo(19.5f, 19.5f); lineTo(14.2f, 19.5f); lineTo(14.2f, 14.2f)
                lineTo(9.8f, 14.2f); lineTo(9.8f, 19.5f); lineTo(4.5f, 19.5f); close()
            }
        }
    }

    val Search by lazy {
        icon("Search") {
            stroke { circle(10.6f, 10.6f, 6.1f) }
            stroke { moveTo(15.2f, 15.2f); lineTo(19.8f, 19.8f) }
        }
    }

    val Library by lazy {
        icon("Library") {
            stroke { moveTo(5f, 4.5f); lineTo(5f, 19.5f) }
            stroke { moveTo(9.5f, 4.5f); lineTo(9.5f, 19.5f) }
            stroke { moveTo(13.8f, 5f); lineTo(18.8f, 19.3f) }
        }
    }

    val Play by lazy {
        icon("Play") { fill { moveTo(8f, 5.3f); lineTo(18.6f, 12f); lineTo(8f, 18.7f); close() } }
    }

    val Pause by lazy {
        icon("Pause") {
            fill { moveTo(7.2f, 5.2f); lineTo(9.8f, 5.2f); lineTo(9.8f, 18.8f); lineTo(7.2f, 18.8f); close() }
            fill { moveTo(14.2f, 5.2f); lineTo(16.8f, 5.2f); lineTo(16.8f, 18.8f); lineTo(14.2f, 18.8f); close() }
        }
    }

    val Next by lazy {
        icon("Next") {
            fill { moveTo(5.5f, 5.8f); lineTo(15f, 12f); lineTo(5.5f, 18.2f); close() }
            stroke(2.2f) { moveTo(18.2f, 5.6f); lineTo(18.2f, 18.4f) }
        }
    }

    val Previous by lazy {
        icon("Previous") {
            fill { moveTo(18.5f, 5.8f); lineTo(9f, 12f); lineTo(18.5f, 18.2f); close() }
            stroke(2.2f) { moveTo(5.8f, 5.6f); lineTo(5.8f, 18.4f) }
        }
    }

    val Shuffle by lazy {
        icon("Shuffle") {
            stroke { moveTo(3.8f, 7f); lineTo(6.6f, 7f); curveTo(11.6f, 7f, 11.8f, 17f, 16.8f, 17f); lineTo(20f, 17f) }
            stroke { moveTo(3.8f, 17f); lineTo(6.6f, 17f); curveTo(8.4f, 17f, 9.6f, 15.8f, 10.6f, 14.3f) }
            stroke { moveTo(13.2f, 9.7f); curveTo(14.1f, 8.2f, 15.2f, 7f, 16.8f, 7f); lineTo(20f, 7f) }
            stroke { moveTo(17.6f, 4.6f); lineTo(20f, 7f); lineTo(17.6f, 9.4f) }
            stroke { moveTo(17.6f, 14.6f); lineTo(20f, 17f); lineTo(17.6f, 19.4f) }
        }
    }

    private fun Builder.repeatArrows() {
        stroke { moveTo(4.5f, 11.5f); lineTo(4.5f, 9.8f); curveTo(4.5f, 8.2f, 5.7f, 7f, 7.3f, 7f); lineTo(19f, 7f) }
        stroke { moveTo(16.6f, 4.6f); lineTo(19f, 7f); lineTo(16.6f, 9.4f) }
        stroke { moveTo(19.5f, 12.5f); lineTo(19.5f, 14.2f); curveTo(19.5f, 15.8f, 18.3f, 17f, 16.7f, 17f); lineTo(5f, 17f) }
        stroke { moveTo(7.4f, 19.4f); lineTo(5f, 17f); lineTo(7.4f, 14.6f) }
    }

    val Repeat by lazy { icon("Repeat") { repeatArrows() } }

    val RepeatOne by lazy {
        icon("RepeatOne") {
            repeatArrows()
            stroke(1.4f) { moveTo(11.1f, 10.8f); lineTo(12.3f, 10f); lineTo(12.3f, 14.2f) }
        }
    }

    val Heart by lazy { icon("Heart") { stroke { heartPath() } } }
    val HeartFilled by lazy { icon("HeartFilled") { fill { heartPath() } } }

    val Queue by lazy {
        icon("Queue") {
            stroke(2.2f) { moveTo(4.5f, 7f); lineTo(4.6f, 7f) }
            stroke(2.2f) { moveTo(4.5f, 12f); lineTo(4.6f, 12f) }
            stroke(2.2f) { moveTo(4.5f, 17f); lineTo(4.6f, 17f) }
            stroke { moveTo(8.5f, 7f); lineTo(19.5f, 7f) }
            stroke { moveTo(8.5f, 12f); lineTo(19.5f, 12f) }
            stroke { moveTo(8.5f, 17f); lineTo(15.5f, 17f) }
        }
    }

    val MoreVertical by lazy {
        icon("MoreVertical") {
            fill { circle(12f, 5.6f, 0.9f) }
            fill { circle(12f, 12f, 0.9f) }
            fill { circle(12f, 18.4f, 0.9f) }
        }
    }

    val MoreHorizontal by lazy {
        icon("MoreHorizontal") {
            fill { circle(5.6f, 12f, 0.9f) }
            fill { circle(12f, 12f, 0.9f) }
            fill { circle(18.4f, 12f, 0.9f) }
        }
    }

    val ChevronDown by lazy { icon("ChevronDown") { stroke { moveTo(6f, 9f); lineTo(12f, 15f); lineTo(18f, 9f) } } }
    val ChevronRight by lazy { icon("ChevronRight") { stroke { moveTo(9.5f, 6f); lineTo(15.5f, 12f); lineTo(9.5f, 18f) } } }
    val Back by lazy { icon("Back") { stroke { moveTo(14.5f, 5.5f); lineTo(8f, 12f); lineTo(14.5f, 18.5f) } } }

    val Close by lazy {
        icon("Close") {
            stroke { moveTo(6.5f, 6.5f); lineTo(17.5f, 17.5f) }
            stroke { moveTo(17.5f, 6.5f); lineTo(6.5f, 17.5f) }
        }
    }

    val Plus by lazy {
        icon("Plus") {
            stroke { moveTo(12f, 5f); lineTo(12f, 19f) }
            stroke { moveTo(5f, 12f); lineTo(19f, 12f) }
        }
    }

    val Check by lazy { icon("Check") { stroke { moveTo(5f, 12.5f); lineTo(9.8f, 17.2f); lineTo(19f, 7.5f) } } }

    val Settings by lazy {
        icon("Settings") {
            stroke { moveTo(4f, 7.5f); lineTo(7f, 7.5f) }
            stroke { moveTo(11f, 7.5f); lineTo(20f, 7.5f) }
            stroke { circle(9f, 7.5f, 2f) }
            stroke { moveTo(4f, 16.5f); lineTo(13f, 16.5f) }
            stroke { moveTo(17f, 16.5f); lineTo(20f, 16.5f) }
            stroke { circle(15f, 16.5f, 2f) }
        }
    }

    val Note by lazy {
        icon("Note") {
            stroke { moveTo(9.5f, 17.5f); lineTo(9.5f, 6.2f); lineTo(18.5f, 4.5f); lineTo(18.5f, 15.5f) }
            stroke { circle(7.3f, 17.5f, 2.2f) }
            stroke { circle(16.3f, 15.5f, 2.2f) }
        }
    }

    val Disc by lazy {
        icon("Disc") {
            stroke { circle(12f, 12f, 8f) }
            stroke { circle(12f, 12f, 2.2f) }
        }
    }

    val Person by lazy {
        icon("Person") {
            stroke { circle(12f, 8.3f, 3.6f) }
            stroke { moveTo(5f, 19.6f); curveTo(5.4f, 16f, 8.3f, 14.2f, 12f, 14.2f); curveTo(15.7f, 14.2f, 18.6f, 16f, 19f, 19.6f) }
        }
    }

    val Folder by lazy {
        icon("Folder") {
            stroke {
                moveTo(3.8f, 7f); curveTo(3.8f, 6.1f, 4.5f, 5.4f, 5.4f, 5.4f); lineTo(9.4f, 5.4f); lineTo(11.4f, 7.8f)
                lineTo(18.6f, 7.8f); curveTo(19.5f, 7.8f, 20.2f, 8.5f, 20.2f, 9.4f); lineTo(20.2f, 17f)
                curveTo(20.2f, 17.9f, 19.5f, 18.6f, 18.6f, 18.6f); lineTo(5.4f, 18.6f); curveTo(4.5f, 18.6f, 3.8f, 17.9f, 3.8f, 17f); close()
            }
        }
    }

    val Clock by lazy {
        icon("Clock") {
            stroke { circle(12f, 12f, 8f) }
            stroke { moveTo(12f, 7.8f); lineTo(12f, 12f); lineTo(14.8f, 13.8f) }
        }
    }

    val Download by lazy {
        icon("Download") {
            stroke { moveTo(12f, 4.5f); lineTo(12f, 15f) }
            stroke { moveTo(7.6f, 10.6f); lineTo(12f, 15f); lineTo(16.4f, 10.6f) }
            stroke { moveTo(5f, 19.5f); lineTo(19f, 19.5f) }
        }
    }

    val Import by lazy {
        icon("Import") {
            stroke {
                moveTo(13.5f, 4f); lineTo(7f, 4f); curveTo(6.2f, 4f, 5.5f, 4.7f, 5.5f, 5.5f); lineTo(5.5f, 18.5f)
                curveTo(5.5f, 19.3f, 6.2f, 20f, 7f, 20f); lineTo(17f, 20f); curveTo(17.8f, 20f, 18.5f, 19.3f, 18.5f, 18.5f)
                lineTo(18.5f, 9f); close()
            }
            stroke { moveTo(13.5f, 4f); lineTo(13.5f, 9f); lineTo(18.5f, 9f) }
            stroke { moveTo(12f, 11.5f); lineTo(12f, 17f) }
            stroke { moveTo(9.3f, 14.3f); lineTo(12f, 17f); lineTo(14.7f, 14.3f) }
        }
    }

    val DragHandle by lazy {
        icon("DragHandle") {
            stroke { moveTo(5f, 8.5f); lineTo(19f, 8.5f) }
            stroke { moveTo(5f, 12f); lineTo(19f, 12f) }
            stroke { moveTo(5f, 15.5f); lineTo(19f, 15.5f) }
        }
    }

    val Phone by lazy {
        icon("Phone") {
            stroke {
                moveTo(8.5f, 3.5f); lineTo(15.5f, 3.5f); curveTo(16.4f, 3.5f, 17f, 4.1f, 17f, 5f); lineTo(17f, 19f)
                curveTo(17f, 19.9f, 16.4f, 20.5f, 15.5f, 20.5f); lineTo(8.5f, 20.5f); curveTo(7.6f, 20.5f, 7f, 19.9f, 7f, 19f)
                lineTo(7f, 5f); curveTo(7f, 4.1f, 7.6f, 3.5f, 8.5f, 3.5f); close()
            }
            stroke { moveTo(11f, 17.5f); lineTo(13f, 17.5f) }
        }
    }

    val Headphones by lazy {
        icon("Headphones") {
            stroke { moveTo(4.5f, 15f); lineTo(4.5f, 12f); curveTo(4.5f, 7.9f, 7.9f, 4.5f, 12f, 4.5f); curveTo(16.1f, 4.5f, 19.5f, 7.9f, 19.5f, 12f); lineTo(19.5f, 15f) }
            stroke { moveTo(4.5f, 14f); lineTo(7.5f, 14f); lineTo(7.5f, 19.5f); lineTo(6f, 19.5f); curveTo(5.2f, 19.5f, 4.5f, 18.8f, 4.5f, 18f); close() }
            stroke { moveTo(19.5f, 14f); lineTo(16.5f, 14f); lineTo(16.5f, 19.5f); lineTo(18f, 19.5f); curveTo(18.8f, 19.5f, 19.5f, 18.8f, 19.5f, 18f); close() }
        }
    }

    val Bluetooth by lazy {
        icon("Bluetooth") {
            stroke { moveTo(7f, 7.8f); lineTo(16.5f, 16.2f); lineTo(12f, 20.2f); lineTo(12f, 3.8f); lineTo(16.5f, 7.8f); lineTo(7f, 16.2f) }
        }
    }

    val Sort by lazy {
        icon("Sort") {
            stroke { moveTo(7.5f, 5f); lineTo(7.5f, 19f) }
            stroke { moveTo(4.5f, 16f); lineTo(7.5f, 19f); lineTo(10.5f, 16f) }
            stroke { moveTo(13.5f, 7f); lineTo(20f, 7f) }
            stroke { moveTo(13.5f, 12f); lineTo(18.5f, 12f) }
            stroke { moveTo(13.5f, 17f); lineTo(17f, 17f) }
        }
    }

    val Refresh by lazy {
        icon("Refresh") {
            stroke {
                moveTo(19.2f, 12f)
                arcTo(7.2f, 7.2f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 17.1f, y1 = 6.9f)
            }
            stroke { moveTo(17.6f, 3.6f); lineTo(17.6f, 7.4f); lineTo(13.8f, 7.4f) }
        }
    }

    val Trash by lazy {
        icon("Trash") {
            stroke { moveTo(4.8f, 7f); lineTo(19.2f, 7f) }
            stroke { moveTo(9.8f, 4.5f); lineTo(14.2f, 4.5f) }
            stroke { moveTo(6.8f, 7f); lineTo(7.7f, 19.5f); lineTo(16.3f, 19.5f); lineTo(17.2f, 7f) }
        }
    }

    val Edit by lazy {
        icon("Edit") {
            stroke { moveTo(5f, 19f); lineTo(5.6f, 15.4f); lineTo(15.6f, 5.4f); lineTo(18.6f, 8.4f); lineTo(8.6f, 18.4f); close() }
            stroke { moveTo(13.4f, 7.6f); lineTo(16.4f, 10.6f) }
        }
    }

    val PlaylistAdd by lazy {
        icon("PlaylistAdd") {
            stroke { moveTo(4f, 7f); lineTo(15f, 7f) }
            stroke { moveTo(4f, 12f); lineTo(15f, 12f) }
            stroke { moveTo(4f, 17f); lineTo(11f, 17f) }
            stroke { moveTo(17.5f, 13.5f); lineTo(17.5f, 20.5f) }
            stroke { moveTo(14f, 17f); lineTo(21f, 17f) }
        }
    }

    val PlayNext by lazy {
        icon("PlayNext") {
            stroke { moveTo(4f, 7f); lineTo(15f, 7f) }
            stroke { moveTo(4f, 12f); lineTo(12f, 12f) }
            stroke { moveTo(4f, 17f); lineTo(10f, 17f) }
            fill { moveTo(14.5f, 13.6f); lineTo(19.8f, 16.8f); lineTo(14.5f, 20f); close() }
        }
    }

    val Info by lazy {
        icon("Info") {
            stroke { circle(12f, 12f, 8f) }
            stroke { moveTo(12f, 11f); lineTo(12f, 16.2f) }
            stroke(2f) { moveTo(12f, 8f); lineTo(12f, 8.05f) }
        }
    }

    val Appearance by lazy {
        icon("Appearance") {
            stroke { circle(12f, 12f, 8f) }
            fill { moveTo(12f, 4f); arcTo(8f, 8f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 12f, y1 = 20f); close() }
        }
    }

    val Waveform by lazy {
        icon("Waveform") {
            stroke { moveTo(4.5f, 10.5f); lineTo(4.5f, 13.5f) }
            stroke { moveTo(8.2f, 7.5f); lineTo(8.2f, 16.5f) }
            stroke { moveTo(12f, 4.5f); lineTo(12f, 19.5f) }
            stroke { moveTo(15.8f, 8.5f); lineTo(15.8f, 15.5f) }
            stroke { moveTo(19.5f, 10.5f); lineTo(19.5f, 13.5f) }
        }
    }

    val Shield by lazy {
        icon("Shield") {
            stroke {
                moveTo(12f, 3.8f); lineTo(19f, 6.4f); lineTo(19f, 11.6f)
                curveTo(19f, 16f, 16f, 19f, 12f, 20.2f)
                curveTo(8f, 19f, 5f, 16f, 5f, 11.6f); lineTo(5f, 6.4f); close()
            }
        }
    }

    val Minus by lazy { icon("Minus") { stroke { moveTo(6f, 12f); lineTo(18f, 12f) } } }
}
