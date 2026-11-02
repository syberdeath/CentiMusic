package com.centimusic.files.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.centimusic.files.data.FileRepository
import com.centimusic.files.theme.FIcons
import com.centimusic.files.theme.NmTheme
import com.centimusic.files.theme.Spacing
import kotlinx.coroutines.delay

@Composable
fun AppRoot(vm: AppViewModel, onExit: () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(FileRepository.hasAllFilesAccess(context)) }

    // Re-check on every resume: "All files access" is granted in system settings, outside the app.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val now = FileRepository.hasAllFilesAccess(context)
                if (now && !granted) vm.reloadAll()
                granted = now
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    if (!granted) {
        PermissionScreen(onGranted = { granted = true; vm.reloadAll() })
        return
    }

    val stack by vm.stack.collectAsState()
    val selection by vm.selection.collectAsState()
    val operation by vm.operation.collectAsState()
    val top = stack.last()
    val prevDepth = remember { mutableIntStateOf(stack.size) }
    val forward = stack.size >= prevDepth.intValue
    SideEffect { prevDepth.intValue = stack.size }

    BackHandler(enabled = true) { if (!vm.back()) onExit() }

    var toast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        vm.messages.collect { msg ->
            toast = msg
            delay(2200)
            toast = null
        }
    }

    Box(Modifier.fillMaxSize().background(NmTheme.colors.background)) {
        AnimatedContent(
            targetState = top,
            transitionSpec = {
                if (targetState is Screen.Root && initialState is Screen.Root) {
                    fadeIn() togetherWith fadeOut()
                } else if (forward) {
                    (slideInHorizontally { it / 6 } + fadeIn()) togetherWith fadeOut()
                } else {
                    fadeIn() togetherWith (slideOutHorizontally { it / 6 } + fadeOut())
                }
            },
            label = "screens",
        ) { screen ->
            when (screen) {
                is Screen.Root -> when (screen.tab) {
                    Tab.BROWSE -> BrowseScreen(vm)
                    Tab.CLEAN -> CleanScreen(vm)
                }
                is Screen.Folder -> FolderScreen(vm, screen.path)
                is Screen.CategoryList -> CategoryScreen(vm, screen.category)
                Screen.Favourites -> FavouritesScreen(vm)
                Screen.Search -> SearchScreen(vm)
                Screen.Settings -> SettingsScreen(vm)
                is Screen.CleanDetail -> CleanDetailScreen(vm, screen.kind)
            }
        }

        val rootTab = (top as? Screen.Root)?.tab
        AnimatedVisibility(
            visible = rootTab != null && selection.isEmpty(),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            BottomNav(rootTab ?: Tab.BROWSE, vm::selectTab)
        }

        operation?.let { op ->
            Box(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(Spacing.l)) { OperationCard(op) }
        }

        GlyphToast(
            toast,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp),
        )
    }
}

@Composable
private fun BottomNav(selected: Tab, onSelect: (Tab) -> Unit) {
    val c = NmTheme.colors
    Box(Modifier.fillMaxWidth().background(c.background).navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = Spacing.s),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            NavItem(FIcons.Browse, "Browse", selected == Tab.BROWSE) { onSelect(Tab.BROWSE) }
            NavItem(FIcons.Clean, "Clean", selected == Tab.CLEAN) { onSelect(Tab.CLEAN) }
        }
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val c = NmTheme.colors
    Column(
        Modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(horizontal = Spacing.xxl, vertical = Spacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = if (active) c.textPrimary else c.textTertiary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(label.uppercase(), style = NmTheme.type.dotCaption, color = if (active) c.textPrimary else c.textTertiary)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.size(4.dp).clip(CircleShape).background(if (active) c.accent else c.background))
    }
}

@Composable
private fun OperationCard(op: Operation) {
    val c = NmTheme.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.inverse).padding(horizontal = Spacing.xl, vertical = Spacing.l),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(op.label.uppercase(), style = NmTheme.type.label, color = c.onInverse, modifier = Modifier.weight(1f))
            op.fraction?.let { Text("${(it * 100).toInt()}%", style = NmTheme.type.label, color = c.accent) }
        }
        Spacer(Modifier.height(Spacing.m))
        val dots = 24
        val filled = ((op.fraction ?: 0f) * dots).toInt()
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(dots) { i ->
                Box(
                    Modifier.size(5.dp).clip(CircleShape)
                        .background(if (i < filled) c.accent else c.onInverse.copy(alpha = 0.25f)),
                )
            }
        }
    }
}

@Composable
private fun PermissionScreen(onGranted: () -> Unit) {
    val c = NmTheme.colors
    val context = LocalContext.current
    val legacyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) onGranted()
    }
    Column(
        Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding().padding(Spacing.gutter),
    ) {
        Spacer(Modifier.weight(1f))
        Row {
            Text("CENTI", style = NmTheme.type.brand, color = c.textPrimary)
            Text("FILES", style = NmTheme.type.brand, color = c.accent)
        }
        Spacer(Modifier.height(Spacing.xxl))
        Text("YOUR FILES,\nCLEARLY.", style = NmTheme.type.hero, color = c.textPrimary)
        Spacer(Modifier.height(Spacing.l))
        Text(
            "To browse, copy, move and clean up files across your phone, CentiFiles needs access to all files. " +
                "Everything stays on your device. The app has no network access.",
            style = NmTheme.type.body, color = c.textSecondary,
        )
        Spacer(Modifier.height(Spacing.xxxl))
        DotGauge(0.5f, dots = 20)
        Spacer(Modifier.weight(1f))
        PillButton(
            "Allow access",
            primary = true,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val perApp = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
                    runCatching { context.startActivity(perApp) }.onFailure {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                    }
                } else {
                    legacyLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
                }
            },
        )
        Spacer(Modifier.height(Spacing.l))
    }
}

