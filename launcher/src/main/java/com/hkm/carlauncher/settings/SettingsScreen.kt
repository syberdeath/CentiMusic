package com.hkm.carlauncher.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hkm.carlauncher.apps.InstalledApp
import com.hkm.carlauncher.map.OfflineMapStatus
import com.hkm.carlauncher.ui.theme.LauncherCard
import com.hkm.carlauncher.ui.theme.LauncherColors
import com.hkm.carlauncher.ui.theme.LauncherIcons
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsScreen(
    apps: List<InstalledApp>,
    navigationPackage: String?,
    mapStatus: OfflineMapStatus,
    hasLocation: Boolean,
    hasMediaAccess: Boolean,
    versionName: String,
    gpsStatus: String = "",
    onPickNavigation: (String) -> Unit,
    onDownloadMap: () -> Unit,
    onImportMap: () -> Unit,
    onDeleteMap: () -> Unit,
    onGrantLocation: () -> Unit,
    onGrantMediaAccess: () -> Unit,
    onSetHome: () -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize().background(LauncherColors.Background).padding(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Launcher settings", color = LauncherColors.Text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(58.dp).clip(RoundedCornerShape(16.dp)).background(LauncherColors.Raised)
                    .clickable(onClick = onClose).semantics { contentDescription = "Close" },
                contentAlignment = Alignment.Center,
            ) { LauncherIcons.Close(Modifier.size(26.dp), LauncherColors.Text) }
        }
        Row(Modifier.fillMaxSize().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Section("Offline map") {
                    Text(mapStatusText(mapStatus), color = statusColor(mapStatus), fontSize = 17.sp)
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (mapStatus !is OfflineMapStatus.Downloading) {
                            Button(if (mapStatus is OfflineMapStatus.Installed) "Update" else "Download", onDownloadMap)
                            Button("Load from USB", onImportMap, primary = false)
                        }
                        if (mapStatus is OfflineMapStatus.Installed) Button("Delete", onDeleteMap, primary = false)
                    }
                }
                Section("Permissions") {
                    PermissionRow("Location", hasLocation, "Allow location", onGrantLocation)
                    PermissionRow("Now playing (notification access)", hasMediaAccess, "Allow media access", onGrantMediaAccess)
                }
                if (gpsStatus.isNotEmpty()) Section("GPS status") {
                    Text(gpsStatus, color = LauncherColors.Muted, fontSize = 15.sp)
                }
                Section("Home app") {
                    Text("Make HKM Launcher open when you press Home.", color = LauncherColors.Muted, fontSize = 15.sp)
                    Button("Set as home app", onSetHome, Modifier.padding(top = 12.dp))
                }
                Text("Version $versionName · Map data © OpenStreetMap contributors", color = LauncherColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(8.dp))
            }
            LauncherCard(Modifier.weight(1f).fillMaxSize()) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
                    Text("Navigation app", color = LauncherColors.Text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Text("Opens when you tap the map.", color = LauncherColors.Muted, fontSize = 15.sp, modifier = Modifier.padding(bottom = 8.dp))
                    apps.forEach { app ->
                        val selected = app.packageName == navigationPackage
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 58.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (selected) LauncherColors.Raised else Color.Transparent)
                                .clickable { onPickNavigation(app.packageName) }.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(app.label, color = LauncherColors.Text, fontSize = 17.sp, modifier = Modifier.weight(1f))
                            if (selected) Text("Selected", color = LauncherColors.Accent, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

internal fun mapStatusText(status: OfflineMapStatus): String = when (status) {
    OfflineMapStatus.NotInstalled -> "Not installed — the map uses online data while connected."
    is OfflineMapStatus.Downloading -> "Downloading ${status.percent}%"
    is OfflineMapStatus.Installed ->
        "Installed · ${status.sizeBytes / 1_000_000} MB · " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(status.dateMillis))
    is OfflineMapStatus.Failed -> status.message
}

private fun statusColor(status: OfflineMapStatus) = when (status) {
    is OfflineMapStatus.Installed -> LauncherColors.Ok
    is OfflineMapStatus.Failed -> Color(0xFFFF8A80)
    else -> LauncherColors.Text
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    LauncherCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text(title, color = LauncherColors.Text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
            content()
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 58.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = LauncherColors.Text, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (granted) Text("Allowed", color = LauncherColors.Ok, fontSize = 15.sp) else Button(action, onClick)
    }
}

@Composable
private fun Button(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = true) {
    Box(
        modifier.heightIn(min = 52.dp).clip(RoundedCornerShape(14.dp))
            .background(if (primary) LauncherColors.Accent else LauncherColors.Raised)
            .clickable(onClick = onClick).padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
}

