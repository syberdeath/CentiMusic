package com.hkm.carlauncher.settings

import com.hkm.carlauncher.apps.AppOpener
import com.hkm.carlauncher.apps.InstalledApp

const val GOOGLE_MAPS = "com.google.android.apps.maps"

/** Google Maps if installed, else the head unit's Carmate, else nothing. */
fun defaultNavigationPackage(apps: List<InstalledApp>): String? =
    apps.firstOrNull { it.packageName == GOOGLE_MAPS }?.packageName
        ?: apps.firstOrNull { it.label.trim().equals("carmate", ignoreCase = true) }?.packageName

/**
 * Opens the user's navigation app: the saved choice, otherwise the default. If neither can be
 * launched, [onNeedsChoice] lets the user pick one.
 */
fun openNavigation(saved: String?, apps: List<InstalledApp>, opener: AppOpener, onNeedsChoice: () -> Unit) {
    val pkg = saved ?: defaultNavigationPackage(apps)
    if (pkg == null || !opener.launchPackage(pkg)) onNeedsChoice()
}
