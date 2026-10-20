package com.centimusic.player

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.centimusic.player.navigation.NothingMusicRoot
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val container get() = (application as NothingMusicApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_NothingMusic) // Replace the launch (splash) window background.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleViewIntent(intent)
        setContent { NothingMusicRoot(container) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleViewIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        container.music.refreshPermission()
    }

    /** "Open with NOTHING MUSIC" from a file manager. */
    private fun handleViewIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (intent.action != Intent.ACTION_VIEW) return
        lifecycleScope.launch {
            container.music.songForExternalUri(uri)?.let { container.player.play(listOf(it)) }
        }
    }
}
