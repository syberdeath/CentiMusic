package com.centimusic.files

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.centimusic.files.theme.CentiFilesTheme
import com.centimusic.files.ui.AppRoot
import com.centimusic.files.ui.AppViewModel

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by vm.settings.collectAsState()
            CentiFilesTheme(settings.theme) {
                AppRoot(vm, onExit = { finish() })
            }
        }
    }
}
