package com.hkm.carlauncher

import android.app.Application

class LauncherApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph()
    }
}

/** Holds the launcher's single instances; filled in as the units are added. */
class AppGraph
