package com.centimusic.files.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * CentiFiles icon set, drawn like CentiMusic's: a 24-unit grid, one 1.6 stroke weight, round caps.
 * Tinted by [androidx.compose.material3.Icon].
 */
object FIcons {
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

    private fun PathBuilder.rect(l: Float, t: Float, r: Float, b: Float) {
        moveTo(l, t); lineTo(r, t); lineTo(r, b); lineTo(l, b); close()
    }

    private fun PathBuilder.page() {
        moveTo(6f, 3.8f); lineTo(14.2f, 3.8f); lineTo(18.5f, 8.1f); lineTo(18.5f, 20.2f)
        lineTo(6f, 20.2f); close()
    }

    private fun Builder.pageWithFold() {
        stroke { page() }
        stroke { moveTo(14.2f, 3.8f); lineTo(14.2f, 8.1f); lineTo(18.5f, 8.1f) }
    }

    private fun PathBuilder.starPath() {
        moveTo(12f, 3.8f); lineTo(14.4f, 9f); lineTo(20.1f, 9.6f); lineTo(15.8f, 13.4f)
        lineTo(17f, 19f); lineTo(12f, 16.1f); lineTo(7f, 19f); lineTo(8.2f, 13.4f)
        lineTo(3.9f, 9.6f); lineTo(9.6f, 9f); close()
    }

    val Search by lazy {
        icon("Search") {
            stroke { circle(10.6f, 10.6f, 6.1f) }
            stroke { moveTo(15.2f, 15.2f); lineTo(19.8f, 19.8f) }
        }
    }
    val Back by lazy { icon("Back") { stroke { moveTo(19.5f, 12f); lineTo(4.8f, 12f) }; stroke { moveTo(10.6f, 6.2f); lineTo(4.8f, 12f); lineTo(10.6f, 17.8f) } } }
    val Close by lazy { icon("Close") { stroke { moveTo(6f, 6f); lineTo(18f, 18f) }; stroke { moveTo(18f, 6f); lineTo(6f, 18f) } } }
    val Check by lazy { icon("Check") { stroke(1.9f) { moveTo(5f, 12.6f); lineTo(9.6f, 17.2f); lineTo(19f, 7.4f) } } }
    val ChevronRight by lazy { icon("ChevronRight") { stroke { moveTo(9.5f, 5.8f); lineTo(15.7f, 12f); lineTo(9.5f, 18.2f) } } }
    val Plus by lazy { icon("Plus") { stroke { moveTo(12f, 5f); lineTo(12f, 19f) }; stroke { moveTo(5f, 12f); lineTo(19f, 12f) } } }
    val More by lazy {
        icon("More") {
            fill { circle(12f, 5.6f, 1.1f) }
            fill { circle(12f, 12f, 1.1f) }
            fill { circle(12f, 18.4f, 1.1f) }
        }
    }

    val Folder by lazy {
        icon("Folder") {
            stroke {
                moveTo(3.6f, 6.4f); lineTo(3.6f, 18.6f); lineTo(20.4f, 18.6f); lineTo(20.4f, 8.6f)
                lineTo(11.6f, 8.6f); lineTo(9.6f, 5.6f); lineTo(3.6f, 5.6f); close()
            }
        }
    }
    val FolderPlus by lazy {
        icon("FolderPlus") {
            stroke {
                moveTo(3.6f, 6.4f); lineTo(3.6f, 18.6f); lineTo(20.4f, 18.6f); lineTo(20.4f, 8.6f)
                lineTo(11.6f, 8.6f); lineTo(9.6f, 5.6f); lineTo(3.6f, 5.6f); close()
            }
            stroke { moveTo(12f, 11f); lineTo(12f, 16.2f) }
            stroke { moveTo(9.4f, 13.6f); lineTo(14.6f, 13.6f) }
        }
    }
    val Download by lazy {
        icon("Download") {
            stroke { moveTo(12f, 4f); lineTo(12f, 15f) }
            stroke { moveTo(7.4f, 10.6f); lineTo(12f, 15.2f); lineTo(16.6f, 10.6f) }
            stroke { moveTo(4.8f, 19.6f); lineTo(19.2f, 19.6f) }
        }
    }
    val Image by lazy {
        icon("Image") {
            stroke { rect(3.8f, 4.8f, 20.2f, 19.2f) }
            stroke { moveTo(3.8f, 16.4f); lineTo(9f, 11.2f); lineTo(14.2f, 16.4f) }
            stroke { moveTo(12.6f, 14.8f); lineTo(15.4f, 12f); lineTo(20.2f, 16.8f) }
            fill { circle(15.6f, 8.6f, 1.1f) }
        }
    }
    val Video by lazy {
        icon("Video") {
            stroke { rect(3.6f, 6.2f, 15.4f, 17.8f) }
            stroke { moveTo(15.4f, 10.4f); lineTo(20.4f, 7.6f); lineTo(20.4f, 16.4f); lineTo(15.4f, 13.6f) }
        }
    }
    val Audio by lazy {
        icon("Audio") {
            stroke { moveTo(9.4f, 17.2f); lineTo(9.4f, 5.2f); lineTo(18.6f, 3.8f); lineTo(18.6f, 15.6f) }
            stroke { circle(7.2f, 17.2f, 2.2f) }
            stroke { circle(16.4f, 15.6f, 2.2f) }
        }
    }
    val Document by lazy {
        icon("Document") {
            pageWithFold()
            stroke { moveTo(9f, 12.2f); lineTo(15.5f, 12.2f) }
            stroke { moveTo(9f, 15.8f); lineTo(15.5f, 15.8f) }
        }
    }
    val File by lazy { icon("File") { pageWithFold() } }
    val Apk by lazy {
        icon("Apk") {
            stroke { rect(6.6f, 3.4f, 17.4f, 20.6f) }
            stroke { moveTo(10.4f, 17.6f); lineTo(13.6f, 17.6f) }
        }
    }
    val Archive by lazy {
        icon("Archive") {
            pageWithFold()
            stroke { moveTo(11f, 6f); lineTo(12.4f, 6f) }
            stroke { moveTo(11f, 8.6f); lineTo(12.4f, 8.6f) }
            stroke { moveTo(11f, 11.2f); lineTo(12.4f, 11.2f) }
            stroke { rect(10.4f, 13.6f, 13f, 16.6f) }
        }
    }
    val Storage by lazy {
        icon("Storage") {
            stroke { rect(4f, 5f, 20f, 11f) }
            stroke { rect(4f, 13f, 20f, 19f) }
            fill { circle(7.4f, 8f, 0.7f) }
            fill { circle(7.4f, 16f, 0.7f) }
        }
    }
    val Star by lazy { icon("Star") { stroke { starPath() } } }
    val StarFilled by lazy { icon("StarFilled") { fill { starPath() } } }
    val Clock by lazy {
        icon("Clock") {
            stroke { circle(12f, 12f, 8.2f) }
            stroke { moveTo(12f, 7.4f); lineTo(12f, 12f); lineTo(15.2f, 14f) }
        }
    }

    val Grid by lazy {
        icon("Grid") {
            stroke { rect(4.4f, 4.4f, 10.6f, 10.6f) }
            stroke { rect(13.4f, 4.4f, 19.6f, 10.6f) }
            stroke { rect(4.4f, 13.4f, 10.6f, 19.6f) }
            stroke { rect(13.4f, 13.4f, 19.6f, 19.6f) }
        }
    }
    val ListView by lazy {
        icon("ListView") {
            stroke { moveTo(4.6f, 6.6f); lineTo(19.4f, 6.6f) }
            stroke { moveTo(4.6f, 12f); lineTo(19.4f, 12f) }
            stroke { moveTo(4.6f, 17.4f); lineTo(19.4f, 17.4f) }
        }
    }
    val Sort by lazy {
        icon("Sort") {
            stroke { moveTo(4.6f, 7f); lineTo(19.4f, 7f) }
            stroke { moveTo(7f, 12f); lineTo(17f, 12f) }
            stroke { moveTo(9.6f, 17f); lineTo(14.4f, 17f) }
        }
    }

    val Copy by lazy {
        icon("Copy") {
            stroke { rect(8.4f, 8.4f, 19.6f, 19.6f) }
            stroke { moveTo(15.6f, 8.4f); lineTo(15.6f, 4.4f); lineTo(4.4f, 4.4f); lineTo(4.4f, 15.6f); lineTo(8.4f, 15.6f) }
        }
    }
    val Move by lazy {
        icon("Move") {
            stroke {
                moveTo(3.6f, 6.4f); lineTo(3.6f, 18.6f); lineTo(20.4f, 18.6f); lineTo(20.4f, 8.6f)
                lineTo(11.6f, 8.6f); lineTo(9.6f, 5.6f); lineTo(3.6f, 5.6f); close()
            }
            stroke { moveTo(8.4f, 13.6f); lineTo(15.4f, 13.6f) }
            stroke { moveTo(12.8f, 11f); lineTo(15.4f, 13.6f); lineTo(12.8f, 16.2f) }
        }
    }
    val Delete by lazy {
        icon("Delete") {
            stroke { moveTo(4.6f, 6.8f); lineTo(19.4f, 6.8f) }
            stroke { moveTo(9.4f, 6.8f); lineTo(9.4f, 4.2f); lineTo(14.6f, 4.2f); lineTo(14.6f, 6.8f) }
            stroke { moveTo(6.6f, 6.8f); lineTo(7.6f, 20f); lineTo(16.4f, 20f); lineTo(17.4f, 6.8f) }
            stroke { moveTo(10.4f, 10.6f); lineTo(10.4f, 16.2f) }
            stroke { moveTo(13.6f, 10.6f); lineTo(13.6f, 16.2f) }
        }
    }
    val Rename by lazy {
        icon("Rename") {
            stroke { moveTo(4.6f, 19.4f); lineTo(5.4f, 15.6f); lineTo(15.8f, 5.2f); lineTo(18.8f, 8.2f); lineTo(8.4f, 18.6f); close() }
            stroke { moveTo(13.6f, 7.4f); lineTo(16.6f, 10.4f) }
        }
    }
    val Share by lazy {
        icon("Share") {
            stroke { circle(17.2f, 5.8f, 2.4f) }
            stroke { circle(6.8f, 12f, 2.4f) }
            stroke { circle(17.2f, 18.2f, 2.4f) }
            stroke { moveTo(9f, 10.8f); lineTo(15f, 7.1f) }
            stroke { moveTo(9f, 13.2f); lineTo(15f, 16.9f) }
        }
    }
    val Info by lazy {
        icon("Info") {
            stroke { circle(12f, 12f, 8.2f) }
            stroke { moveTo(12f, 11f); lineTo(12f, 16.4f) }
            fill { circle(12f, 7.9f, 0.6f) }
        }
    }
    val Paste by lazy {
        icon("Paste") {
            stroke { rect(5.6f, 5.6f, 18.4f, 20.2f) }
            stroke { rect(9.2f, 3.6f, 14.8f, 7.2f) }
        }
    }

    /** Clean tab: a dot-matrix sparkle. */
    val Clean by lazy {
        icon("Clean") {
            stroke {
                moveTo(10f, 3.8f); curveTo(10.6f, 8.2f, 11.8f, 9.4f, 16.2f, 10f)
                curveTo(11.8f, 10.6f, 10.6f, 11.8f, 10f, 16.2f)
                curveTo(9.4f, 11.8f, 8.2f, 10.6f, 3.8f, 10f)
                curveTo(8.2f, 9.4f, 9.4f, 8.2f, 10f, 3.8f); close()
            }
            stroke {
                moveTo(17.2f, 13.6f); curveTo(17.5f, 15.6f, 18.1f, 16.2f, 20.1f, 16.5f)
                curveTo(18.1f, 16.8f, 17.5f, 17.4f, 17.2f, 19.4f)
                curveTo(16.9f, 17.4f, 16.3f, 16.8f, 14.3f, 16.5f)
                curveTo(16.3f, 16.2f, 16.9f, 15.6f, 17.2f, 13.6f); close()
            }
        }
    }
    val Browse by lazy {
        icon("Browse") {
            stroke { rect(4.4f, 4.4f, 10.6f, 10.6f) }
            stroke { circle(16.5f, 7.5f, 3.1f) }
            stroke { rect(4.4f, 13.4f, 10.6f, 19.6f) }
            stroke { rect(13.4f, 13.4f, 19.6f, 19.6f) }
        }
    }
    val Settings by lazy {
        icon("Settings") {
            stroke { moveTo(4.5f, 7f); lineTo(19.5f, 7f) }
            stroke { moveTo(4.5f, 17f); lineTo(19.5f, 17f) }
            fill { circle(9f, 7f, 2.1f) }
            fill { circle(15f, 17f, 2.1f) }
        }
    }
    val Eye by lazy {
        icon("Eye") {
            stroke { moveTo(2.8f, 12f); curveTo(5.2f, 7.4f, 8.4f, 5.6f, 12f, 5.6f); curveTo(15.6f, 5.6f, 18.8f, 7.4f, 21.2f, 12f); curveTo(18.8f, 16.6f, 15.6f, 18.4f, 12f, 18.4f); curveTo(8.4f, 18.4f, 5.2f, 16.6f, 2.8f, 12f); close() }
            stroke { circle(12f, 12f, 2.8f) }
        }
    }
    val Duplicate by lazy {
        icon("Duplicate") {
            stroke { rect(4.2f, 4.2f, 14.2f, 14.2f) }
            stroke { rect(9.8f, 9.8f, 19.8f, 19.8f) }
        }
    }
    val Large by lazy {
        icon("Large") {
            stroke { moveTo(4.6f, 9.6f); lineTo(4.6f, 4.6f); lineTo(9.6f, 4.6f) }
            stroke { moveTo(14.4f, 4.6f); lineTo(19.4f, 4.6f); lineTo(19.4f, 9.6f) }
            stroke { moveTo(19.4f, 14.4f); lineTo(19.4f, 19.4f); lineTo(14.4f, 19.4f) }
            stroke { moveTo(9.6f, 19.4f); lineTo(4.6f, 19.4f); lineTo(4.6f, 14.4f) }
        }
    }
    val Screenshot by lazy {
        icon("Screenshot") {
            stroke { rect(6.6f, 3.4f, 17.4f, 20.6f) }
            stroke { moveTo(9.2f, 9f); lineTo(9.2f, 6.4f); lineTo(11.8f, 6.4f) }
            stroke { moveTo(14.8f, 17.6f); lineTo(14.8f, 15f); }
            stroke { moveTo(12.2f, 17.6f); lineTo(14.8f, 17.6f) }
        }
    }
}
