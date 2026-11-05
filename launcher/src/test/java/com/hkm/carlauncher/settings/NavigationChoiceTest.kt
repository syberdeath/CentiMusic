package com.hkm.carlauncher.settings

import com.hkm.carlauncher.apps.AppOpener
import com.hkm.carlauncher.apps.InstalledApp
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationChoiceTest {
    private fun app(pkg: String, label: String) = InstalledApp(pkg, "$pkg.Main", label, true)
    private val maps = app("com.google.android.apps.maps", "Maps")
    private val carmate = app("com.vendor.carmate", "Carmate")

    private class FakeOpener(private val launchable: Set<String>) : AppOpener {
        val opened = mutableListOf<String>()
        override fun launchPackage(packageName: String): Boolean =
            (packageName in launchable).also { if (it) opened += packageName }
    }

    @Test
    fun googleMapsIsDefault() {
        assertEquals(maps.packageName, defaultNavigationPackage(listOf(carmate, maps)))
    }

    @Test
    fun carmateWhenNoGoogleMaps() {
        assertEquals(carmate.packageName, defaultNavigationPackage(listOf(carmate)))
    }

    @Test
    fun noneIsNull() {
        assertNull(defaultNavigationPackage(listOf(app("a.b", "Radio"))))
    }

    @Test
    fun savedChoiceIsOpened() = runTest {
        val opener = FakeOpener(setOf("com.waze", maps.packageName))
        var asked = 0
        openNavigation(saved = "com.waze", apps = listOf(maps), opener = opener) { asked++ }
        assertEquals(listOf("com.waze"), opener.opened)
        assertEquals(0, asked)
    }

    @Test
    fun defaultUsedWhenNothingSaved() = runTest {
        val opener = FakeOpener(setOf(maps.packageName))
        openNavigation(saved = null, apps = listOf(carmate, maps), opener = opener) { error("should not ask") }
        assertEquals(listOf(maps.packageName), opener.opened)
    }

    @Test
    fun savedMissingAsksForChoice() = runTest {
        val opener = FakeOpener(emptySet())
        var asked = 0
        openNavigation(saved = "gone.app", apps = listOf(maps), opener = opener) { asked++ }
        assertEquals(1, asked)
        assertEquals(emptyList<String>(), opener.opened)
    }

    @Test
    fun nothingInstalledAsksForChoice() = runTest {
        var asked = 0
        openNavigation(saved = null, apps = emptyList(), opener = FakeOpener(emptySet())) { asked++ }
        assertEquals(1, asked)
    }
}
