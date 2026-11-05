package com.hkm.carlauncher.settings

import com.hkm.carlauncher.apps.AppOpener
import com.hkm.carlauncher.apps.InstalledApp

const val GOOGLE_MAPS = "com.google.android.apps.maps"

/**
 * Google Maps if installed, else nothing (the user picks in settings). The head unit's "Carmate"
 * is not assumed: on these units it is the phone-mirroring app.
 */
fun defaultNavigationPackage(apps: List<InstalledApp>): String? =
    apps.firstOrNull { it.packageName == GOOGLE_MAPS }?.packageName

/**
 * Opens the user's navigation app: the saved choice, otherwise the default. If neither can be
 * launched, [onNeedsChoice] lets the user pick one.
 */
fun openNavigation(saved: String?, apps: List<InstalledApp>, opener: AppOpener, onNeedsChoice: () -> Unit) {
    val pkg = saved ?: defaultNavigationPackage(apps)
    if (pkg == null || !opener.launchPackage(pkg)) onNeedsChoice()
}
