package com.hkm.carlauncher.apps

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
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
class AllAppsScreenTest {
    @get:Rule val rule = createComposeRule()

    private val carmate = InstalledApp("com.vendor.carmate", "Main", "Carmate", true)
    private val music = InstalledApp("com.vendor.music", "Main", "Music", true)

    private val icon = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).asImageBitmap()

    @Test
    fun showsLabelsAndOpens() {
        var opened: InstalledApp? = null
        var closed = 0
        rule.setContent {
            AllAppsScreen(
                apps = listOf(carmate, music),
                icon = { icon },
                onOpen = { opened = it },
                onClose = { closed++ },
            )
        }
        rule.onNodeWithText("Carmate").assertExists()
        rule.onNodeWithText("Music").performClick()
        assertEquals(music, opened)
        rule.onNodeWithContentDescription("Close").performClick()
        assertEquals(1, closed)
    }
}
