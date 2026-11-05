package com.hkm.carlauncher.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hkm.carlauncher.apps.InstalledApp
import com.hkm.carlauncher.map.OfflineMapStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h632dp-land")
class SettingsScreenTest {
    @get:Rule val rule = createComposeRule()

    private val carmate = InstalledApp("com.vendor.carmate", "Main", "Carmate", true)
    private val calls = mutableListOf<String>()

    private fun show(status: OfflineMapStatus) = rule.setContent {
        SettingsScreen(
            apps = listOf(carmate),
            navigationPackage = null,
            mapStatus = status,
            hasLocation = true,
            hasMediaAccess = false,
            versionName = "1.0.7",
            onPickNavigation = { calls += "pick:$it" },
            onDownloadMap = { calls += "download" },
            onImportMap = { calls += "import" },
            onDeleteMap = { calls += "delete" },
            onGrantLocation = { calls += "location" },
            onGrantMediaAccess = { calls += "media" },
            onSetHome = { calls += "home" },
            onClose = { calls += "close" },
        )
    }

    @Test
    fun downloadingShowsPercent() {
        show(OfflineMapStatus.Downloading(42))
        rule.onNodeWithText("Downloading 42%").assertExists()
    }

    @Test
    fun installedShowsSizeAndDelete() {
        show(OfflineMapStatus.Installed(80_000_000, 0))
        rule.onNodeWithText("Installed · 80 MB", substring = true).assertExists()
        rule.onNodeWithText("Delete").performScrollTo().performClick()
        assertEquals(listOf("delete"), calls)
    }

    @Test
    fun notInstalledOffersDownloadAndUsb() {
        show(OfflineMapStatus.NotInstalled)
        rule.onNodeWithText("Download").performScrollTo().performClick()
        rule.onNodeWithText("Load from USB").performScrollTo().performClick()
        assertEquals(listOf("download", "import"), calls)
    }

    @Test
    fun pickingNavigationApp() {
        show(OfflineMapStatus.NotInstalled)
        rule.onNodeWithText("Carmate").performScrollTo().performClick()
        assertEquals(listOf("pick:com.vendor.carmate"), calls)
    }

    @Test
    fun missingMediaAccessHasButton() {
        show(OfflineMapStatus.NotInstalled)
        rule.onNodeWithText("Allow media access").performScrollTo().performClick()
        assertEquals(listOf("media"), calls)
    }
}
