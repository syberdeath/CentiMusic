package com.hkm.carlauncher.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hkm.carlauncher.R
import com.hkm.carlauncher.ui.theme.LauncherCard
import com.hkm.carlauncher.ui.theme.LauncherColors

/** HKM branding. A long press (not a tap, so it can't happen by accident) opens launcher settings. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogoCard(onLongPress: () -> Unit, modifier: Modifier = Modifier) {
    LauncherCard(modifier) {
        Row(
            Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = onLongPress)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(painterResource(R.drawable.hkm_logo), contentDescription = "HKM logo", modifier = Modifier.size(60.dp))
            Column(Modifier.padding(start = 12.dp)) {
                Text("HKM", color = LauncherColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("CAR LAUNCHER", color = LauncherColors.Muted, fontSize = 12.sp, letterSpacing = 1.sp)
            }
        }
    }
}
