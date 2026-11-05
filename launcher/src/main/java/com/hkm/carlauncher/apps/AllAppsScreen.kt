package com.hkm.carlauncher.apps

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hkm.carlauncher.ui.theme.LauncherColors
import com.hkm.carlauncher.ui.theme.LauncherIcons

@Composable
fun AllAppsScreen(
    apps: List<InstalledApp>,
    icon: (InstalledApp) -> ImageBitmap,
    onOpen: (InstalledApp) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize().background(LauncherColors.Background).padding(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("All apps", color = LauncherColors.Text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(58.dp).clip(RoundedCornerShape(16.dp)).background(LauncherColors.Raised)
                    .clickable(onClick = onClose).semantics { contentDescription = "Close" },
                contentAlignment = Alignment.Center,
            ) { LauncherIcons.Close(Modifier.size(26.dp), LauncherColors.Text) }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(140.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(apps, key = { it.packageName + "/" + it.activityName }) { app ->
                Column(
                    Modifier.clip(RoundedCornerShape(16.dp)).clickable { onOpen(app) }.padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(icon(app), contentDescription = null, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        app.label,
                        color = LauncherColors.Text,
                        fontSize = 15.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
