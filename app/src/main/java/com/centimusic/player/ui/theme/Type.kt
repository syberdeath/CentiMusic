package com.centimusic.player.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.centimusic.player.R

/** Doto (SIL OFL) — a dot-matrix display face, bundled as static round-dot instances. */
val DotFamily = FontFamily(
    Font(R.font.doto_semibold, FontWeight.SemiBold),
    Font(R.font.doto_bold, FontWeight.Bold),
)

val SansFamily: FontFamily = FontFamily.SansSerif

@Immutable
data class NmType(
    /** Hero headline: "MUSIC FOR A CLEARER MIND". */
    val hero: TextStyle,
    /** Screen titles: Library, Search, Settings. */
    val screenTitle: TextStyle,
    /** Now Playing track title. */
    val trackTitle: TextStyle,
    /** Small dot-matrix branding. */
    val brand: TextStyle,
    val sectionTitle: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val bodySmall: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
)

val DefaultNmType = NmType(
    hero = TextStyle(fontFamily = DotFamily, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 46.sp, letterSpacing = 0.02.em),
    screenTitle = TextStyle(fontFamily = DotFamily, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = 0.02.em),
    trackTitle = TextStyle(fontFamily = DotFamily, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = 0.03.em),
    brand = TextStyle(fontFamily = DotFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 21.sp, letterSpacing = 0.06.em),
    sectionTitle = TextStyle(fontFamily = SansFamily, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = 0.sp),
    title = TextStyle(fontFamily = SansFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 21.sp),
    body = TextStyle(fontFamily = SansFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontFamily = SansFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 17.sp),
    label = TextStyle(fontFamily = SansFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.02.em),
    caption = TextStyle(fontFamily = SansFamily, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.02.em),
)

val LocalNmType = staticCompositionLocalOf { DefaultNmType }
