package com.hkm.carlauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.hkm.carlauncher.ui.theme.LauncherColors
import com.hkm.carlauncher.ui.theme.LauncherTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No edge-to-edge: the head unit's own status and button bars stay where they are.
        setContent {
            LauncherTheme {
                Box(Modifier.fillMaxSize().background(LauncherColors.Background))
            }
        }
    }
}
