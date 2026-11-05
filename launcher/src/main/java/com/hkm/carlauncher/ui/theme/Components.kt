package com.hkm.carlauncher.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

val CardShape = RoundedCornerShape(20.dp)

/** Dark rounded card with a thin outline, as used by every panel on the home screen. */
@Composable
fun LauncherCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .clip(CardShape)
            .background(LauncherColors.Card)
            .border(1.dp, LauncherColors.Outline, CardShape),
    ) { content() }
}
