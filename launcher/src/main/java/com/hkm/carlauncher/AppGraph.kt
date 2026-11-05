package com.hkm.carlauncher

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.hkm.carlauncher.apps.AppLauncher
import com.hkm.carlauncher.apps.AppsRepository
import com.hkm.carlauncher.apps.DockResolver
import com.hkm.carlauncher.clock.ClockState
import com.hkm.carlauncher.location.LocationRepository
import com.hkm.carlauncher.map.DownloadManagerMapDownloader
import com.hkm.carlauncher.map.MapRelease
import com.hkm.carlauncher.map.MapSource
import com.hkm.carlauncher.map.OfflineMapStore
import com.hkm.carlauncher.map.selectMapSource
import com.hkm.carlauncher.media.NowPlayingRepository
import com.hkm.carlauncher.settings.LauncherSettings
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** One instance of each unit for the whole app (manual dependency injection). */
class AppGraph(context: Context, val scope: CoroutineScope) {
    private val app = context.applicationContext

    val apps = AppsRepository(app, scope)
    val launcher = AppLauncher(app)
    /** Vendor package names can be pinned here once known, e.g. DockEntry.MUSIC to "com.vendor.music". */
    val dockResolver = DockResolver(selfPackage = app.packageName)
    val clock = ClockState(app)
    val nowPlaying = NowPlayingRepository(app, scope)
    val location = LocationRepository(app)
    val settings = LauncherSettings(app)
    val mapStore = OfflineMapStore(
        dir = app.getExternalFilesDir("maps") ?: File(app.filesDir, "maps"),
        sha256Fetcher = { MapRelease.fetchSha256() },
        downloader = DownloadManagerMapDownloader(app),
    )

    private val _online = MutableStateFlow(false)
    val online: StateFlow<Boolean> = _online

    val mapSource: StateFlow<MapSource> =
        combine(mapStore.installedFile, online) { file, isOnline -> selectMapSource(file, isOnline) }
            .stateIn(scope, SharingStarted.Eagerly, selectMapSource(mapStore.installedFile.value, false))

    init {
        val cm = app.getSystemService(ConnectivityManager::class.java)
        _online.value = cm.getNetworkCapabilities(cm.activeNetwork)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                _online.value = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
            override fun onLost(network: Network) { _online.value = false }
        })
    }
}
