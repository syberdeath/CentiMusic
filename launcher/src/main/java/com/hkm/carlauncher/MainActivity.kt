package com.hkm.carlauncher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.hkm.carlauncher.apps.AllAppsScreen
import com.hkm.carlauncher.apps.DockEntry
import com.hkm.carlauncher.apps.InstalledApp
import com.hkm.carlauncher.home.HomeCallbacks
import com.hkm.carlauncher.home.HomeScreen
import com.hkm.carlauncher.map.MapPanel
import com.hkm.carlauncher.map.MapRelease
import com.hkm.carlauncher.settings.SettingsScreen
import com.hkm.carlauncher.settings.openNavigation
import com.hkm.carlauncher.ui.theme.LauncherColors
import com.hkm.carlauncher.ui.theme.LauncherTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private enum class Screen { HOME, ALL_APPS, SETTINGS }

class MainActivity : ComponentActivity() {
    private val graph get() = (application as LauncherApp).graph
    private var screen by mutableStateOf(Screen.HOME)
    private var hasLocation by mutableStateOf(false)
    private var hasMediaAccess by mutableStateOf(false)

    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshPermissions()
        graph.location.start()
    }

    private val importMap = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) lifecycleScope.launch {
            val input = contentResolver.openInputStream(uri) ?: return@launch
            graph.mapStore.import(input, expectedSha256 = if (graph.online.value) MapRelease.fetchSha256() else null)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No edge-to-edge: the head unit's own status and button bars stay where they are.
        if (savedInstanceState == null) lifecycleScope.launch { graph.settings.incrementLaunchCount() }
        refreshPermissions()
        if (savedInstanceState == null) requestLocation()
        setContent { LauncherTheme { Root() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Pressing Home while the launcher is showing returns to the main screen.
        if (intent.hasCategory(Intent.CATEGORY_HOME)) screen = Screen.HOME
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
        graph.clock.refresh()
        graph.nowPlaying.checkAccess()
        graph.location.start()
    }

    private fun refreshPermissions() {
        hasLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        hasMediaAccess = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
    }

    @Composable
    private fun Root() {
        val apps by graph.apps.apps.collectAsStateWithLifecycle()
        val clock by graph.clock.text.collectAsStateWithLifecycle()
        val nowPlaying by graph.nowPlaying.state.collectAsStateWithLifecycle()
        val location by graph.location.state.collectAsStateWithLifecycle()
        val mapSource by graph.mapSource.collectAsStateWithLifecycle()
        val mapStatus by graph.mapStore.status.collectAsStateWithLifecycle()
        val online by graph.online.collectAsStateWithLifecycle()
        val navPackage by graph.settings.navigationPackage.collectAsStateWithLifecycle(null)
        val launchCount by graph.settings.launchCount.collectAsStateWithLifecycle(0)
        val icons = remember { mutableMapOf<String, ImageBitmap>() }
        val iconFor: (InstalledApp) -> ImageBitmap = { app ->
            icons.getOrPut(app.packageName + "/" + app.activityName) {
                graph.apps.iconFor(app).toBitmap(128, 128).asImageBitmap()
            }
        }

        when (screen) {
            Screen.HOME -> HomeScreen(
                clock = clock,
                nowPlaying = nowPlaying,
                dock = DockEntry.entries.map { it to graph.dockResolver.resolve(it, apps) },
                callbacks = HomeCallbacks(
                    onLogoLongPress = { screen = Screen.SETTINGS },
                    onDock = { target -> graph.launcher.launch(target) { screen = Screen.ALL_APPS } },
                    onAllApps = { screen = Screen.ALL_APPS },
                    onPlayPause = graph.nowPlaying::playPause,
                    onNext = graph.nowPlaying::next,
                    onPrevious = graph.nowPlaying::previous,
                    onOpenSource = { graph.nowPlaying.openSource(graph.launcher) },
                    onGrantMediaAccess = ::openNotificationAccess,
                    onOpenMusic = {
                        graph.launcher.launch(graph.dockResolver.resolve(DockEntry.MUSIC, apps)) { screen = Screen.ALL_APPS }
                    },
                ),
                mapContent = { modifier ->
                    MapPanel(
                        source = mapSource,
                        location = location,
                        showHint = launchCount <= 3,
                        onTap = { openNavigation(navPackage, apps, graph.launcher) { screen = Screen.SETTINGS } },
                        onAllowLocation = ::requestLocation,
                        onGetMap = { screen = Screen.SETTINGS },
                        modifier = modifier,
                    )
                },
            )
            Screen.ALL_APPS -> AllAppsScreen(
                apps = apps,
                icon = iconFor,
                onOpen = { app -> if (graph.launcher.launch(app)) screen = Screen.HOME },
                onClose = { screen = Screen.HOME },
            )
            Screen.SETTINGS -> SettingsScreen(
                apps = apps,
                navigationPackage = navPackage,
                mapStatus = mapStatus,
                hasLocation = hasLocation,
                hasMediaAccess = hasMediaAccess,
                versionName = BuildConfig.VERSION_NAME,
                onPickNavigation = { pkg -> lifecycleScope.launch { graph.settings.setNavigationPackage(pkg) } },
                onDownloadMap = { graph.scope.launch { graph.mapStore.download() } },
                onImportMap = { importMap.launch(arrayOf("*/*")) },
                onDeleteMap = graph.mapStore::delete,
                onGrantLocation = ::requestLocation,
                onGrantMediaAccess = ::openNotificationAccess,
                onSetHome = { startSafely(Intent(Settings.ACTION_HOME_SETTINGS)) },
                onClose = { screen = Screen.HOME },
            )
        }

        MapDownloadOffer(online)
    }

    /** Offers the UAE offline map once, the first time the launcher has internet and no map file. */
    @Composable
    private fun MapDownloadOffer(online: Boolean) {
        var show by remember { mutableStateOf(false) }
        var sizeMb by remember { mutableStateOf<Long?>(null) }
        LaunchedEffect(online) {
            if (!online || graph.mapStore.installedFile.value != null) return@LaunchedEffect
            if (graph.settings.mapOfferShown.first()) return@LaunchedEffect
            sizeMb = MapRelease.fetchSizeBytes()?.let { it / 1_000_000 }
            show = true
        }
        if (!show) return
        val dismiss = {
            show = false
            lifecycleScope.launch { graph.settings.setMapOfferShown() }
            Unit
        }
        AlertDialog(
            onDismissRequest = dismiss,
            containerColor = LauncherColors.Card,
            title = { Text("Offline map", color = LauncherColors.Text) },
            text = {
                val size = sizeMb?.let { " (about $it MB)" }.orEmpty()
                Text("Download the UAE offline map$size? The map then works without internet.", color = LauncherColors.Muted)
            },
            confirmButton = {
                TextButton(onClick = {
                    dismiss()
                    graph.scope.launch { graph.mapStore.download() }
                }) { Text("Download", color = LauncherColors.Accent) }
            },
            dismissButton = { TextButton(onClick = dismiss) { Text("Not now", color = LauncherColors.Muted) } },
        )
    }

    /** Asks again; if Android no longer shows the prompt (denied twice), opens the app's settings page. */
    private fun requestLocation() {
        if (hasLocation) return
        if (askedLocation && !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
            startSafely(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        } else {
            askedLocation = true
            locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private var askedLocation = false

    private fun openNotificationAccess() {
        startSafely(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    private fun startSafely(intent: Intent) {
        if (!graph.launcher.start(intent)) graph.launcher.start(Intent(Settings.ACTION_SETTINGS))
    }
}
