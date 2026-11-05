package com.hkm.carlauncher

import androidx.compose.ui.graphics.Color
import com.hkm.carlauncher.ui.theme.LauncherColors
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeTest {
    @Test
    fun accentIsLogoBlue() {
        assertEquals(Color(0xFF4F86F7), LauncherColors.Accent)
    }

    @Test
    fun backgroundIsNearBlack() {
        assertEquals(Color(0xFF07090C), LauncherColors.Background)
    }
}
