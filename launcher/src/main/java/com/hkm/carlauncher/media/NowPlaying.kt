package com.hkm.carlauncher.media

import android.graphics.Bitmap

/** What the "Now playing" card shows. */
sealed interface NowPlaying {
    /** The user hasn't granted notification access, which Android requires to read other apps' media. */
    data object NoAccess : NowPlaying
    data object Nothing : NowPlaying
    data class Playing(
        val sourceLabel: String,
        val packageName: String,
        val title: String,
        val artist: String,
        val artwork: Bitmap?,
        val isPlaying: Boolean,
        val progress: Float,
    ) : NowPlaying
}
