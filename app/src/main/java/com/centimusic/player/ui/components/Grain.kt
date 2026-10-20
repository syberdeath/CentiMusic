package com.centimusic.player.ui.components

import android.graphics.Bitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.random.Random

/** A small tile of monochrome noise, repeated to give flat shapes a printed, grainy feel. */
object Grain {
    val tile: ImageBitmap by lazy {
        val size = 128
        val random = Random(42)
        val pixels = IntArray(size * size) {
            val v = random.nextInt(256)
            val a = random.nextInt(110)
            (a shl 24) or (v shl 16) or (v shl 8) or v
        }
        Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    fun brush(): ShaderBrush = ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated))
}

fun Modifier.grain(alpha: Float = 0.35f): Modifier = drawWithCache {
    val brush = Grain.brush()
    onDrawWithContent {
        drawContent()
        drawRect(brush, alpha = alpha)
    }
}
