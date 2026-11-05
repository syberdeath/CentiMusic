package com.hkm.carlauncher.apps

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

/** Starts apps from the launcher. Every method fails quietly (returns false) when the app is gone. */
class AppLauncher(private val context: Context) : AppOpener {
    fun launch(app: InstalledApp): Boolean =
        start(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(ComponentName(app.packageName, app.activityName)),
        ) || launchPackage(app.packageName)

    fun launch(target: DockTarget, onAllApps: () -> Unit) {
        val started = when (target) {
            is DockTarget.App -> launch(target.app)
            DockTarget.SystemSettings -> start(Intent(Settings.ACTION_SETTINGS))
            DockTarget.AllApps -> false
        }
        if (!started) onAllApps()
    }

    override fun launchPackage(packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        return start(intent)
    }

    fun start(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}
