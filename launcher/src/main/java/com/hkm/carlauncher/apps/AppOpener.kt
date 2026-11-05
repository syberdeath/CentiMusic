package com.hkm.carlauncher.apps

/** Starts another app by package; false when it is no longer installed or can't be launched. */
fun interface AppOpener {
    fun launchPackage(packageName: String): Boolean
}
