package com.centimusic.player.navigation

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavGraphBuilder
import com.centimusic.player.ui.components.LocalNavScope
import com.centimusic.player.ui.components.LocalSharedScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.centimusic.player.AppContainer
import com.centimusic.player.data.media.AudioPermission
import com.centimusic.player.data.preferences.UserPreferences
import com.centimusic.player.ui.components.BottomNavBar
import com.centimusic.player.ui.components.dotGrid
import com.centimusic.player.ui.components.GlyphToast
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import com.centimusic.player.ui.components.MiniPlayer
import com.centimusic.player.ui.components.LocalSongDeleter
import com.centimusic.player.ui.components.SongActionsHost
import com.centimusic.player.ui.components.SongDeleteHost
import com.centimusic.player.ui.components.SongDeleter
import com.centimusic.player.ui.home.HomeScreen
import com.centimusic.player.ui.library.AlbumsScreen
import com.centimusic.player.ui.library.ArtistScreen
import com.centimusic.player.ui.library.ArtistsScreen
import com.centimusic.player.ui.library.HiddenFoldersScreen
import com.centimusic.player.ui.edit.EditSongScreen
import com.centimusic.player.ui.library.FoldersScreen
import com.centimusic.player.ui.library.LibraryScreen
import com.centimusic.player.ui.library.SongListScreen
import com.centimusic.player.ui.library.SongListSource
import com.centimusic.player.ui.player.NowPlayingScreen
import com.centimusic.player.ui.playlists.AddSongsScreen
import com.centimusic.player.ui.playlists.PlaylistDetailScreen
import com.centimusic.player.ui.playlists.PlaylistsScreen
import com.centimusic.player.ui.queue.QueueScreen
import com.centimusic.player.ui.search.SearchScreen
import com.centimusic.player.ui.settings.SettingsScreen
import com.centimusic.player.ui.splash.AnimatedSplash
import com.centimusic.player.ui.splash.SplashConfig
import com.centimusic.player.ui.splash.SplashStyle
import com.centimusic.player.data.preferences.SplashChoice
import kotlinx.coroutines.flow.first
import com.centimusic.player.ui.visualizer.VisualizerScreen
import com.centimusic.player.ui.theme.NmTheme
import com.centimusic.player.ui.theme.CentiMusicTheme
import com.centimusic.player.ui.theme.Spacing
import kotlinx.coroutines.launch

@Composable
fun CentiMusicRoot(container: AppContainer) {
    val prefs by container.preferences.preferences.collectAsStateWithLifecycle(initialValue = UserPreferences())
    CentiMusicTheme(prefs.themeMode) {
        val navController = rememberNavController()
        val navigator = remember(navController) { Navigator(navController) }
        val snackbar = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val songMenu = remember { SongMenuController() }
        val songDeleter = remember { SongDeleter() }
        val messenger: (String) -> Unit = remember {
            { text ->
                scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar(text)
                }
            }
        }
        // Shown once per launch; survives rotation so it doesn't replay.
        var showSplash by rememberSaveable { mutableStateOf(true) }
        var splashConfig by remember { mutableStateOf<SplashConfig?>(null) }
        var forceFullSplash by remember { mutableStateOf(false) }
        // Decide which launch animation to play: full on the first launch of the day, quick after.
        LaunchedEffect(showSplash) {
            if (!showSplash) {
                splashConfig = null
                return@LaunchedEffect
            }
            val p = container.preferences.preferences.first()
            val today = java.text.SimpleDateFormat("yyyy-DDD", java.util.Locale.US).format(java.util.Date())
            val full = forceFullSplash || p.alwaysFullSplash || p.lastFullSplashDay != today
            val style = when (p.splashChoice) {
                SplashChoice.SURPRISE -> SplashStyle.entries.random()
                SplashChoice.BOOT -> SplashStyle.BOOT
                SplashChoice.GLYPH -> SplashStyle.GLYPH
                SplashChoice.PULSE -> SplashStyle.PULSE
                SplashChoice.VINYL -> SplashStyle.VINYL
            }
            splashConfig = SplashConfig(style, full, p.greetingName)
            forceFullSplash = false
            if (full) container.preferences.setLastFullSplashDay(today)
        }
        val replaySplash: () -> Unit = remember {
            {
                forceFullSplash = true
                showSplash = true
            }
        }
        val access = rememberLibraryAccess(container, messenger, askForPermission = !showSplash)

        LaunchedEffect(Unit) {
            container.player.messages.collect { messenger(it) }
        }

        CompositionLocalProvider(
            LocalAppContainer provides container,
            LocalNavigator provides navigator,
            LocalMessenger provides messenger,
            LocalSongMenu provides songMenu,
            LocalSongDeleter provides songDeleter,
            LocalLibraryAccess provides access,
            LocalReplaySplash provides replaySplash,
        ) {
            Box(Modifier.fillMaxSize()) {
                AppScaffold(navController, navigator, container, snackbar)
                SongActionsHost(songMenu)
                SongDeleteHost(songDeleter)
                if (showSplash) {
                    val config = splashConfig
                    if (config == null) {
                        // A frame or two while preferences load: stay black like the system splash.
                        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF080808)))
                    } else {
                        AnimatedSplash(config, onFinished = { showSplash = false })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun AppScaffold(
    navController: NavHostController,
    navigator: Navigator,
    container: AppContainer,
    snackbar: SnackbarHostState,
) {
    val colors = NmTheme.colors
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val playback by container.player.state.collectAsStateWithLifecycle()
    val showChrome = route !in Routes.immersive
    // Detail screens keep highlighting the tab they were opened from.
    var lastTab by rememberSaveable { mutableStateOf(Routes.HOME) }
    LaunchedEffect(route) { if (route != null && route in Routes.tabs) lastTab = route }
    val selectedTab = if (route != null && route in Routes.tabs) route else lastTab
    var chromeHeightPx by remember { mutableIntStateOf(0) }
    val chromeHeight = with(LocalDensity.current) { chromeHeightPx.toDp() }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .dotGrid(colors.textPrimary, alpha = if (colors.isDark) 0.07f else 0.09f),
    ) {
      SharedTransitionLayout(Modifier.fillMaxSize()) {
       CompositionLocalProvider(LocalSharedScope provides this) {
        Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalChromePadding provides if (showChrome) chromeHeight else 0.dp) {
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                modifier = Modifier.fillMaxSize(),
                enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 12 } },
                exitTransition = { fadeOut(tween(160)) },
                popEnterTransition = { fadeIn(tween(220)) },
                popExitTransition = { fadeOut(tween(160)) + slideOutHorizontally(tween(260)) { it / 12 } },
            ) {
                dest(Routes.HOME, enterTransition = { fadeIn(tween(200)) }) { HomeScreen() }
                dest(Routes.SEARCH, enterTransition = { fadeIn(tween(200)) }) { SearchScreen() }
                dest(Routes.LIBRARY, enterTransition = { fadeIn(tween(200)) }) { LibraryScreen() }
                dest(Routes.SONGS) { SongListScreen(SongListSource.AllSongs) }
                dest(Routes.LIKED) { SongListScreen(SongListSource.Liked) }
                dest(Routes.RECENT) { SongListScreen(SongListSource.RecentlyAdded) }
                dest(Routes.HISTORY) { SongListScreen(SongListSource.RecentlyPlayed) }
                dest(Routes.ALBUMS) { AlbumsScreen() }
                dest(Routes.ALBUM, arguments = listOf(navArgument("key") { type = NavType.StringType })) {
                    SongListScreen(SongListSource.Album(it.stringArg("key")))
                }
                dest(Routes.ARTISTS) { ArtistsScreen() }
                dest(Routes.ARTIST, arguments = listOf(navArgument("name") { type = NavType.StringType })) {
                    ArtistScreen(it.stringArg("name"))
                }
                dest(Routes.ARTIST_SONGS, arguments = listOf(navArgument("name") { type = NavType.StringType })) {
                    SongListScreen(SongListSource.Artist(it.stringArg("name")))
                }
                dest(Routes.EDIT_SONG, arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                    EditSongScreen(it.stringArg("id"))
                }
                dest(Routes.HIDDEN_FOLDERS) { HiddenFoldersScreen() }
                dest(Routes.FOLDERS) { FoldersScreen() }
                dest(Routes.FOLDER, arguments = listOf(navArgument("path") { type = NavType.StringType })) {
                    SongListScreen(SongListSource.Folder(it.stringArg("path").let { p -> if (p == "/") "" else p }))
                }
                dest(Routes.PLAYLISTS) { PlaylistsScreen() }
                dest(Routes.PLAYLIST, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                    PlaylistDetailScreen(it.arguments?.getLong("id") ?: 0L)
                }
                dest(Routes.PLAYLIST_ADD, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                    AddSongsScreen(it.arguments?.getLong("id") ?: 0L)
                }
                dest(Routes.SETTINGS) { SettingsScreen() }
                dest(
                    Routes.PLAYER,
                    enterTransition = { slideInVertically(tween(320)) { it } + fadeIn(tween(200)) },
                    exitTransition = { fadeOut(tween(160)) },
                    popEnterTransition = { fadeIn(tween(200)) },
                    popExitTransition = { slideOutVertically(tween(300)) { it } + fadeOut(tween(260)) },
                ) { NowPlayingScreen() }
                dest(
                    Routes.QUEUE,
                    enterTransition = { slideInVertically(tween(300)) { it / 3 } + fadeIn(tween(200)) },
                    popExitTransition = { slideOutVertically(tween(260)) { it / 3 } + fadeOut(tween(200)) },
                ) { QueueScreen() }
                dest(
                    Routes.VISUALIZER,
                    enterTransition = { fadeIn(tween(350)) },
                    popExitTransition = { fadeOut(tween(250)) },
                ) { VisualizerScreen() }
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            SnackbarHost(snackbar, Modifier.padding(horizontal = Spacing.l)) { data ->
                GlyphToast(data.visuals.message)
            }
            AnimatedVisibility(
                visible = showChrome,
                enter = fadeIn(tween(200)) + slideInVertically { it },
                exit = fadeOut(tween(120)) + slideOutVertically { it },
            ) {
              CompositionLocalProvider(LocalNavScope provides this) {
                Column(
                    Modifier
                        .onSizeChanged { chromeHeightPx = it.height }
                        // Content fades out behind the floating player and dock.
                        .background(Brush.verticalGradient(listOf(colors.background.copy(alpha = 0f), colors.background.copy(alpha = 0.92f), colors.background))),
                ) {
                    Spacer(Modifier.height(Spacing.s))
                    if (playback.current != null) {
                        MiniPlayer(playback, container.player, onOpen = navigator::player)
                        Spacer(Modifier.height(Spacing.s))
                    }
                    BottomNavBar(selectedTab, onSelect = navigator::tab)
                }
              }
            }
        }
        }
       }
      }
    }
}

/** A nav destination whose content knows its enter/exit animation, for shared artwork. */
private fun NavGraphBuilder.dest(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    enterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = null,
    exitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?)? = null,
    popEnterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = enterTransition,
    popExitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?)? = exitTransition,
    content: @Composable (NavBackStackEntry) -> Unit,
) = composable(
    route = route,
    arguments = arguments,
    enterTransition = enterTransition,
    exitTransition = exitTransition,
    popEnterTransition = popEnterTransition,
    popExitTransition = popExitTransition,
) { entry ->
    CompositionLocalProvider(LocalNavScope provides this) { content(entry) }
}

private fun NavBackStackEntry.stringArg(name: String): String = arguments?.getString(name).orEmpty()

/** Requests audio access (and notification permission on 13+) and launches the system file picker. */
@Composable
private fun rememberLibraryAccess(container: AppContainer, message: (String) -> Unit, askForPermission: Boolean): LibraryAccess {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val granted by container.music.permissionGranted.collectAsStateWithLifecycle()
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }
    var askedOnLaunch by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val audioGranted = result[AudioPermission.permission] == true
        permanentlyDenied = !audioGranted && activity?.shouldShowRequestPermissionRationale(AudioPermission.permission) == false
        container.music.refreshPermission()
    }
    val pickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val added = container.music.importDocuments(uris)
                message(if (added == 0) "No new audio files added" else "Added $added ${if (added == 1) "file" else "files"}")
            }
        }
    }

    fun permissions(): Array<String> = buildList {
        add(AudioPermission.permission)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    LaunchedEffect(Unit) { container.music.pruneImported() }
    // Wait for the launch animation before showing the system permission dialog.
    LaunchedEffect(askForPermission) {
        if (askForPermission && !askedOnLaunch && !AudioPermission.isGranted(context)) {
            askedOnLaunch = true
            permissionLauncher.launch(permissions())
        }
    }

    return LibraryAccess(
        granted = granted,
        permanentlyDenied = permanentlyDenied,
        request = {
            if (permanentlyDenied) {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } else {
                permissionLauncher.launch(permissions())
            }
        },
        importFiles = { pickerLauncher.launch(arrayOf("audio/*")) },
    )
}
