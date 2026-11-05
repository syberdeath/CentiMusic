package com.hkm.carlauncher.apps

/** A launchable activity on the head unit. */
data class InstalledApp(
    val packageName: String,
    val activityName: String,
    val label: String,
    val isSystem: Boolean,
)
