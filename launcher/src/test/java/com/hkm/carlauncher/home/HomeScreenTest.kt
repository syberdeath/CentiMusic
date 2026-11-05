package com.hkm.carlauncher.home

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hkm.carlauncher.apps.DockEntry
import com.hkm.carlauncher.apps.DockTarget
import com.hkm.carlauncher.apps.InstalledApp
import com.hkm.carlauncher.clock.ClockText
import com.hkm.carlauncher.media.NowPlaying
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h632dp-land")
class HomeScreenTest {
    @get:Rule val rule = createComposeRule()

    private val calls = mutableListOf<String>()
    private val music = InstalledApp("com.vendor.music", "Main", "Music", true)

    private fun show() = rule.setContent {
        HomeScreen(
            clock = ClockText("14:13", "Thursday, 5 November 2026"),
            nowPlaying = NowPlaying.Nothing,
            dock = DockEntry.entries.map { e ->
                e to if (e == DockEntry.MUSIC) DockTarget.App(music) else DockTarget.AllApps
            },
            callbacks = HomeCallbacks(
                onLogoLongPress = { calls += "settings" },
                onDock = { calls += "dock:$it" },
                onAllApps = { calls += "allApps" },
                onPlayPause = {}, onNext = {}, onPrevious = {}, onOpenSource = {},
                onGrantMediaAccess = {}, onOpenMusic = {},
            ),
            mapContent = { m -> Box(m) },
        )
    }

    @Test
    fun rendersAllRegions() {
        show()
        listOf("HKM", "CAR LAUNCHER", "14:13", "Thursday, 5 November 2026", "Nothing playing",
            "Bluetooth", "Carmate", "Music", "Video", "Google Play", "Settings", "All apps")
            .forEach { rule.onNodeWithText(it).assertExists() }
    }

    @Test
    fun dockClickPassesTarget() {
        show()
        rule.onNodeWithText("Video").performClick()
        rule.onNodeWithText("Music").performClick()
        rule.onNodeWithText("All apps").performClick()
        assertEquals(listOf("dock:${DockTarget.AllApps}", "dock:${DockTarget.App(music)}", "allApps"), calls)
    }

    @Test
    fun logoLongPressOpensSettings() {
        show()
        rule.onNodeWithContentDescription("HKM logo").performClick()
        assertEquals(emptyList<String>(), calls)
        rule.onNodeWithContentDescription("HKM logo").performTouchInput { longClick() }
        assertEquals(listOf("settings"), calls)
    }
}
