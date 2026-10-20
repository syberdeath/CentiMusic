package com.centimusic.player

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.centimusic.player.navigation.CentiMusicRoot
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val container get() = (application as CentiMusicApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        // Swaps the launch theme for Theme.CentiMusic; AnimatedSplash takes over from the system splash.
        installSplashScreen().setOnExitAnimationListener { it.remove() }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleViewIntent(intent)
        setContent { CentiMusicRoot(container) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleViewIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        container.music.refreshPermission()
    }

    /** "Open with CentiMusic" from a file manager. */
    private fun handleViewIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (intent.action != Intent.ACTION_VIEW) return
        lifecycleScope.launch {
            container.music.songForExternalUri(uri)?.let { container.player.play(listOf(it)) }
        }
    }
}
