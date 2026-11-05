package com.hkm.carlauncher.apps

import org.junit.Assert.assertEquals
import org.junit.Test

class DockResolverTest {
    private val self = "com.hkm.carlauncher"
    private val resolver = DockResolver(self)

    private fun app(pkg: String, label: String, system: Boolean = true) =
        InstalledApp(packageName = pkg, activityName = "$pkg.Main", label = label, isSystem = system)

    @Test
    fun matchesExactLabel() {
        val bt = app("com.vendor.bt", " bluetooth ")
        assertEquals(DockTarget.App(bt), resolver.resolve(DockEntry.BLUETOOTH, listOf(app("a.b", "Radio"), bt)))
    }

    @Test
    fun bluetoothFallsBackToBt() {
        val bt = app("com.vendor.btmusic", "BT Music")
        assertEquals(DockTarget.App(bt), resolver.resolve(DockEntry.BLUETOOTH, listOf(bt)))
    }

    @Test
    fun googlePlayByPackage() {
        val play = app("com.android.vending", "Play Store")
        assertEquals(DockTarget.App(play), resolver.resolve(DockEntry.GOOGLE_PLAY, listOf(play)))
    }

    @Test
    fun settingsPrefersVendorApp() {
        val vendor = app("com.vendor.carsettings", "Settings")
        val android = app("com.android.settings", "Settings")
        assertEquals(DockTarget.App(vendor), resolver.resolve(DockEntry.SETTINGS, listOf(android, vendor)))
        assertEquals(DockTarget.App(android), resolver.resolve(DockEntry.SETTINGS, listOf(android)))
        assertEquals(DockTarget.SystemSettings, resolver.resolve(DockEntry.SETTINGS, emptyList()))
    }

    @Test
    fun prefersSystemAppOnLabelTie() {
        val user = app("com.other.music", "Music", system = false)
        val vendor = app("com.vendor.music", "Music", system = true)
        assertEquals(DockTarget.App(vendor), resolver.resolve(DockEntry.MUSIC, listOf(user, vendor)))
        assertEquals(DockTarget.App(vendor), resolver.resolve(DockEntry.MUSIC, listOf(vendor, user)))
    }

    @Test
    fun neverMatchesSelf() {
        assertEquals(DockTarget.AllApps, resolver.resolve(DockEntry.MUSIC, listOf(app(self, "Music"))))
    }

    @Test
    fun pinnedPackageWins() {
        val pinned = DockResolver(self, mapOf(DockEntry.MUSIC to "com.vendor.music"))
        val player = app("com.vendor.music", "Player")
        val other = app("com.other.music", "Music")
        assertEquals(DockTarget.App(player), pinned.resolve(DockEntry.MUSIC, listOf(other, player)))
    }

    @Test
    fun carmateMatchesCaseInsensitive() {
        val carmate = app("com.vendor.carmate", "CarMate")
        assertEquals(DockTarget.App(carmate), resolver.resolve(DockEntry.CARMATE, listOf(carmate)))
    }

    @Test
    fun missingFallsBackToAllApps() {
        assertEquals(DockTarget.AllApps, resolver.resolve(DockEntry.VIDEO, listOf(app("a.b", "Music"))))
    }
}
