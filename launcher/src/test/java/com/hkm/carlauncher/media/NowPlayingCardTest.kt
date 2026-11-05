package com.hkm.carlauncher.media

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h632dp-land")
class NowPlayingCardTest {
    @get:Rule val rule = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun show(state: NowPlaying) = rule.setContent {
        NowPlayingCard(
            state = state,
            onPlayPause = { calls += "playPause" },
            onNext = { calls += "next" },
            onPrevious = { calls += "previous" },
            onOpenSource = { calls += "open" },
            onGrantAccess = { calls += "grant" },
            onOpenMusic = { calls += "music" },
        )
    }

    @Test
    fun playingShowsSourceAndTitleWithoutArtwork() {
        show(NowPlaying.Playing("Bluetooth", "com.vendor.bt", "Desert Highway", "", null, isPlaying = true, progress = 0.3f))
        rule.onNodeWithText("NOW PLAYING · BLUETOOTH").assertExists()
        rule.onNodeWithText("Desert Highway").performClick()
        rule.onNodeWithContentDescription("Pause").performClick()
        rule.onNodeWithContentDescription("Next").performClick()
        rule.onNodeWithContentDescription("Previous").performClick()
        assertEquals(listOf("open", "playPause", "next", "previous"), calls)
    }

    @Test
    fun pausedShowsPlayButton() {
        show(NowPlaying.Playing("Radio", "com.vendor.radio", "91.1 FM", "Radio", null, isPlaying = false, progress = 0f))
        rule.onNodeWithContentDescription("Play").assertExists()
    }

    @Test
    fun noAccessAsksForPermission() {
        show(NowPlaying.NoAccess)
        rule.onNodeWithText("Allow access to show what's playing").assertExists()
        rule.onNodeWithText("Allow").performClick()
        assertEquals(listOf("grant"), calls)
    }

    @Test
    fun nothingPlayingOffersMusic() {
        show(NowPlaying.Nothing)
        rule.onNodeWithText("Nothing playing").assertExists()
        rule.onNodeWithText("Open Music").performClick()
        assertEquals(listOf("music"), calls)
    }
}
