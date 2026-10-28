package com.centimusic.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.centimusic.player.navigation.Routes
import com.centimusic.player.ui.theme.NmIcons
import com.centimusic.player.ui.theme.NmTheme

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Home", NmIcons.Home),
    Tab(Routes.SEARCH, "Search", NmIcons.Search),
    Tab(Routes.LIBRARY, "Library", NmIcons.Library),
)

/**
 * Floating Nothing-style dock: a rounded capsule of icons, the active tab marked by a red dot.
 */
@Composable
fun BottomNavBar(selectedTab: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = NmTheme.colors
    val haptics = rememberHaptics()
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(top = 6.dp, bottom = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .width(232.dp)
                .height(60.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(colors.surface),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                val selected = tab.route == selectedTab
                Column(
                    Modifier
                        .weight(1f)
                        .height(60.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = false, radius = 30.dp),
                            role = Role.Tab,
                        ) {
                            if (!selected) haptics.tick()
                            onSelect(tab.route)
                        }
                        .semantics { contentDescription = tab.label; this.selected = selected },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        tab.icon,
                        contentDescription = null,
                        tint = if (selected) colors.textPrimary else colors.textTertiary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(if (selected) colors.accent else Color.Transparent),
                    )
                }
            }
        }
    }
}
