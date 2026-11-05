package com.hkm.carlauncher.apps

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Every launchable app except this launcher, sorted by label; refreshed on install/uninstall. */
class AppsRepository(private val context: Context, private val scope: CoroutineScope) {
    private val pm = context.packageManager
    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val apps: StateFlow<List<InstalledApp>> = _apps.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refresh()
    }

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(context.applicationContext, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        refresh()
    }

    fun refresh() {
        scope.launch(Dispatchers.IO) { _apps.value = load() }
    }

    fun iconFor(app: InstalledApp): Drawable =
        runCatching { pm.getActivityIcon(ComponentName(app.packageName, app.activityName)) }
            .getOrElse { pm.defaultActivityIcon }

    private fun load(): List<InstalledApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { info ->
                val ai = info.activityInfo
                InstalledApp(
                    packageName = ai.packageName,
                    activityName = ai.name,
                    label = info.loadLabel(pm).toString(),
                    isSystem = (ai.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                )
            }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName to it.activityName }
            .sortedBy { it.label.lowercase() }
    }
}
