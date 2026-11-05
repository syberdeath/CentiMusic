package com.hkm.carlauncher

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.maplibre.android.MapLibre

class LauncherApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        graph = AppGraph(this, CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
    }
}
