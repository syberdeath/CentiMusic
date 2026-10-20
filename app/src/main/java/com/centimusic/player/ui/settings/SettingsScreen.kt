package com.centimusic.player.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.centimusic.player.AppContainer
import com.centimusic.player.BuildConfig
import com.centimusic.player.data.media.ScanState
import com.centimusic.player.data.preferences.ThemeMode
import com.centimusic.player.data.preferences.UserPreferences
import com.centimusic.player.navigation.LocalAppContainer
import com.centimusic.player.navigation.LocalChromePadding
import com.centimusic.player.navigation.LocalLibraryAccess
import com.centimusic.player.navigation.LocalMessenger
import com.centimusic.player.navigation.LocalNavigator
import com.centimusic.player.ui.appViewModel
import com.centimusic.player.ui.components.ChoiceDialog
import com.centimusic.player.ui.components.CircleIcon
import com.centimusic.player.ui.components.DotLoader
import com.centimusic.player.ui.components.NavigationRow
import com.centimusic.player.ui.components.NmDivider
import com.centimusic.player.ui.components.TopBar
import com.centimusic.player.ui.components.bottomContentPadding
import com.centimusic.player.ui.theme.NmIcons
import com.centimusic.player.ui.theme.NmTheme
import com.centimusic.player.ui.theme.Spacing
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val prefs: UserPreferences = UserPreferences(),
    val songs: Int = 0,
    val albums: Int = 0,
    val artists: Int = 0,
    val scanning: Boolean = false,
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val prefs = container.preferences

    val state: StateFlow<SettingsUiState> = combine(prefs.preferences, container.music.library, container.music.scanState) { p, lib, scan ->
        SettingsUiState(p, lib.songs.size, lib.albums.size, lib.artists.size, scan == ScanState.Scanning)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { prefs.setThemeMode(mode) }
    }
    fun setRememberModes(v: Boolean) {
        viewModelScope.launch { prefs.setRememberPlaybackModes(v) }
    }
    fun setResume(v: Boolean) {
        viewModelScope.launch { prefs.setResumeLastQueue(v) }
    }
    fun setPauseOnDisconnect(v: Boolean) {
        viewModelScope.launch { prefs.setPauseOnDisconnect(v) }
    }
    fun setSkipSilence(v: Boolean) {
        viewModelScope.launch { prefs.setSkipSilence(v) }
    }
    fun setVisualizerLock(v: Boolean) {
        viewModelScope.launch { prefs.setVisualizerOnLockScreen(v) }
    }
    fun setIgnoreShort(v: Boolean) {
        viewModelScope.launch { prefs.setIgnoreShortTracks(v) }
    }
    suspend fun rescan(): Int {
        container.music.rescan()
        return container.music.library.value.songs.size
    }
    fun clearSearchHistory() {
        viewModelScope.launch { container.history.clearSearches() }
    }
}

@Composable
fun SettingsScreen() {
    val vm = appViewModel { SettingsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val access = LocalLibraryAccess.current
    val message = LocalMessenger.current
    val scope = LocalAppContainer.current.scope
    val colors = NmTheme.colors
    val prefs = state.prefs
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = bottomContentPadding(LocalChromePadding.current)) {
        item {
            TopBar(onBack = navigator::back)
            Text(
                "Settings",
                style = NmTheme.type.screenTitle,
                color = colors.textPrimary,
                modifier = Modifier.padding(horizontal = Spacing.gutter),
            )
            Spacer(Modifier.height(Spacing.xl))
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleIcon(NmIcons.Waveform, size = 56.dp)
                Spacer(Modifier.width(Spacing.l))
                Column(Modifier.weight(1f)) {
                    Text("Your library", style = NmTheme.type.title, color = colors.textPrimary)
                    Text(
                        "${state.songs} songs · ${state.albums} albums · ${state.artists} artists",
                        style = NmTheme.type.bodySmall,
                        color = colors.textSecondary,
                    )
                }
                if (state.scanning) DotLoader()
            }
            Spacer(Modifier.height(Spacing.m))
            NmDivider(Modifier.padding(horizontal = Spacing.gutter))
        }

        item { GroupLabel("Appearance") }
        item {
            NavigationRow(NmIcons.Appearance, "Theme", { dialog = SettingsDialog.Theme }, value = prefs.themeMode.label, iconSize = 40.dp)
        }

        item { GroupLabel("Playback") }
        item {
            ToggleRow(NmIcons.Shuffle, "Remember shuffle & repeat", "Keep your shuffle and repeat choice between sessions", prefs.rememberPlaybackModes, vm::setRememberModes)
        }
        item {
            ToggleRow(NmIcons.Clock, "Resume where you left off", "Restore the last queue and position when you return", prefs.resumeLastQueue, vm::setResume)
        }
        item {
            ToggleRow(NmIcons.Headphones, "Pause when audio disconnects", "Pause if headphones are unplugged or Bluetooth drops", prefs.pauseOnDisconnect, vm::setPauseOnDisconnect)
        }
        item {
            ToggleRow(NmIcons.Waveform, "Skip silence", "Trim silent passages during playback", prefs.skipSilence, vm::setSkipSilence)
        }
        item {
            ToggleRow(
                NmIcons.Shield,
                "Visualizer on lock screen",
                "If the visualizer is open when your phone locks, show it over the lock screen. Unlock to leave it.",
                prefs.visualizerOnLockScreen,
                vm::setVisualizerLock,
            )
        }

        item { GroupLabel("Library") }
        item {
            ToggleRow(NmIcons.Note, "Ignore short audio", "Hide files shorter than 30 seconds, like ringtones and voice notes", prefs.ignoreShortTracks, vm::setIgnoreShort)
        }
        item {
            NavigationRow(NmIcons.Refresh, "Rescan library", {
                scope.launch {
                    val count = vm.rescan()
                    message("Library updated · $count songs")
                }
            }, subtitle = "Look for new or removed music", iconSize = 40.dp, showChevron = false)
        }
        item {
            NavigationRow(NmIcons.Import, "Import audio files", access.importFiles, subtitle = "Pick files from any folder or provider", iconSize = 40.dp, showChevron = false)
        }
        if (!access.granted) {
            item {
                NavigationRow(NmIcons.Shield, "Allow music access", access.request, subtitle = "Required to scan music on this device", iconSize = 40.dp)
            }
        }
        item {
            NavigationRow(NmIcons.Search, "Clear search history", {
                vm.clearSearchHistory()
                message("Search history cleared")
            }, iconSize = 40.dp, showChevron = false)
        }

        item { GroupLabel("About") }
        item { NavigationRow(NmIcons.Shield, "Privacy", { dialog = SettingsDialog.Privacy }, iconSize = 40.dp) }
        item { NavigationRow(NmIcons.Info, "About", { dialog = SettingsDialog.About }, iconSize = 40.dp) }
        item { NavigationRow(NmIcons.Info, "Version", {}, value = BuildConfig.VERSION_NAME, iconSize = 40.dp, showChevron = false) }

        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.xxxl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("CentiMusic", style = NmTheme.type.brand, color = colors.textSecondary)
                Spacer(Modifier.height(Spacing.xs))
                Text("Local music. A clearer mind.", style = NmTheme.type.caption, color = colors.textTertiary)
                Spacer(Modifier.height(Spacing.m))
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(colors.accent),
                )
            }
        }
    }

    when (dialog) {
        SettingsDialog.Theme -> ChoiceDialog(
            title = "Theme",
            options = ThemeMode.entries,
            selected = prefs.themeMode,
            label = { it.label },
            onSelect = {
                vm.setTheme(it)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        SettingsDialog.Privacy -> InfoDialog(
            "Privacy",
            "CentiMusic works entirely offline. It reads audio files on this device to build your library, " +
                "and stores playlists, likes, history and settings only on this device. The app has no internet " +
                "permission, no accounts, no analytics and no ads.",
        ) { dialog = null }
        SettingsDialog.About -> InfoDialog(
            "About",
            "CentiMusic\nVersion ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})\n\n" +
                "A minimalist offline music player, inspired by Nothing's design language, built with Jetpack Compose " +
                "and Media3 ExoPlayer.\n\nDot-matrix type: Doto, © The Doto Project Authors (SIL Open Font License 1.1).\n\n" +
                "CentiMusic is an independent project. It is not affiliated with, endorsed by or a product of Nothing Technology Ltd.",
        ) { dialog = null }
        null -> Unit
    }
}

private enum class SettingsDialog { Theme, Privacy, About }

@Composable
private fun GroupLabel(text: String) {
    Text(
        text.uppercase(),
        style = NmTheme.type.caption,
        color = NmTheme.colors.textTertiary,
        modifier = Modifier.padding(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.xxl, bottom = Spacing.xs),
    )
}

@Composable
private fun ToggleRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = NmTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) { onChange(!checked) }
            .semantics { stateDescription = if (checked) "On" else "Off" }
            .padding(horizontal = Spacing.gutter, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIcon(icon, size = 40.dp)
        Spacer(Modifier.width(Spacing.l))
        Column(Modifier.weight(1f)) {
            Text(title, style = NmTheme.type.title, color = colors.textPrimary)
            Text(subtitle, style = NmTheme.type.caption, color = colors.textSecondary)
        }
        Spacer(Modifier.width(Spacing.m))
        DotSwitch(checked)
    }
}

/** Minimal pill switch: hollow when off, red with a white dot when on. */
@Composable
private fun DotSwitch(checked: Boolean) {
    val colors = NmTheme.colors
    val track by animateColorAsState(if (checked) colors.accent else colors.surfaceHigh, label = "track")
    val thumbColor by animateColorAsState(if (checked) colors.onAccent else colors.textSecondary, label = "thumb")
    val offset by animateDpAsState(if (checked) 18.dp else 0.dp, label = "offset")
    Box(
        Modifier
            .size(width = 42.dp, height = 24.dp)
            .clip(RoundedCornerShape(50))
            .background(track)
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .offset(x = offset)
                .size(16.dp)
                .clip(CircleShape)
                .background(thumbColor),
        )
    }
}

@Composable
private fun InfoDialog(title: String, text: String, onDismiss: () -> Unit) {
    val colors = NmTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        shape = RoundedCornerShape(20),
        title = { Text(title, style = NmTheme.type.sectionTitle, color = colors.textPrimary) },
        text = { Text(text, style = NmTheme.type.body, color = colors.textSecondary) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = colors.accent) } },
    )
}
