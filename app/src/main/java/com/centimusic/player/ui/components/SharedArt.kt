package com.centimusic.player.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import com.centimusic.player.ui.pulse.rememberMotionEnabled

/** The app-wide shared-transition layout, so artwork can fly between screens. */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedScope = compositionLocalOf<SharedTransitionScope?> { null }

/** The enter/exit animation of whatever hosts the current content (a nav destination, the mini-player). */
val LocalNavScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Artwork with the same [key] on two screens morphs from one to the other during navigation. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedArt(key: String): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val anim = LocalNavScope.current ?: return this
    if (!rememberMotionEnabled()) return this
    return with(shared) {
        this@sharedArt.sharedElement(
            rememberSharedContentState(key),
            anim,
            boundsTransform = { _, _ -> androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 380f) },
        )
    }
}
