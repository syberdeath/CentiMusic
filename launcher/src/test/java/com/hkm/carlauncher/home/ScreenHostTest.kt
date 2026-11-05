package com.hkm.carlauncher.home

import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreenHostTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun homeStaysComposedUnderOtherScreens() {
        // The map lives in the home screen; tearing it down on every visit rebuilds the GL map.
        val screen = mutableStateOf(Screen.HOME)
        var homeDisposals = 0
        rule.setContent {
            ScreenHost(
                screen = screen.value,
                home = {
                    DisposableEffect(Unit) { onDispose { homeDisposals++ } }
                    Text("home")
                },
                allApps = { Text("all apps") },
                settings = { Text("settings") },
            )
        }
        rule.runOnIdle { screen.value = Screen.ALL_APPS }
        rule.onNodeWithText("all apps").assertExists()
        rule.runOnIdle { screen.value = Screen.SETTINGS }
        rule.onNodeWithText("settings").assertExists()
        rule.runOnIdle { screen.value = Screen.HOME }
        rule.onNodeWithText("home").assertExists()
        rule.onNodeWithText("settings").assertDoesNotExist()
        assertEquals(0, homeDisposals)
    }
}
