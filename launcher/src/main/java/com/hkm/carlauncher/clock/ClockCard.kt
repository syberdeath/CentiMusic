package com.hkm.carlauncher.clock

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hkm.carlauncher.ui.theme.LauncherColors
import com.hkm.carlauncher.ui.theme.LauncherCard

@Composable
fun ClockCard(text: ClockText, modifier: Modifier = Modifier) {
    LauncherCard(modifier) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 18.dp)) {
            Text(
                text.time,
                color = LauncherColors.Text,
                fontSize = 78.sp,
                lineHeight = 80.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = (-2).sp,
            )
            Text(text.date, color = LauncherColors.Muted, fontSize = 17.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
