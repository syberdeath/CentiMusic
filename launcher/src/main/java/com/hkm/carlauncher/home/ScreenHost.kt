package com.hkm.carlauncher.home

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

enum class Screen { HOME, ALL_APPS, SETTINGS }

/**
 * The home screen is always composed; All apps and Settings are drawn on top of it. This keeps the
 * map (a native GL view) alive with its position, heading and zoom while the other screens are open.
 */
@Composable
fun ScreenHost(
    screen: Screen,
    home: @Composable () -> Unit,
    allApps: @Composable () -> Unit,
    settings: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        home()
        if (screen != Screen.HOME) {
            // Swallows touches that the overlay itself doesn't use, so they never reach the map below.
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
                if (screen == Screen.ALL_APPS) allApps() else settings()
            }
        }
    }
}
