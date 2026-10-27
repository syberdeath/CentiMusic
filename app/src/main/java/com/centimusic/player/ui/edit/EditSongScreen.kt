package com.centimusic.player.ui.edit

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.centimusic.player.navigation.LocalAppContainer
import com.centimusic.player.navigation.LocalMessenger
import com.centimusic.player.navigation.LocalNavigator
import com.centimusic.player.ui.components.Artwork
import com.centimusic.player.ui.components.ConfirmDialog
import com.centimusic.player.ui.components.PillButton
import com.centimusic.player.ui.components.TopBar
import com.centimusic.player.ui.theme.NmIcons
import com.centimusic.player.ui.theme.NmTheme
import com.centimusic.player.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Edit a song's title, artist, album and cover. Edits are stored in CentiMusic and shown
 * everywhere in the app (including the lock screen); the music file itself is not modified.
 */
@Composable
fun EditSongScreen(songId: String) {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val message = LocalMessenger.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = NmTheme.colors
    val library by container.music.library.collectAsStateWithLifecycle()
    val song = library.byId[songId]?.takeIf { it.id == songId }
    val original = remember(songId) { container.music.original(songId) }

    var title by rememberSaveable { mutableStateOf<String?>(null) }
    var artist by rememberSaveable { mutableStateOf<String?>(null) }
    var album by rememberSaveable { mutableStateOf<String?>(null) }
    var newCover by rememberSaveable { mutableStateOf<String?>(null) }
    var removeCover by rememberSaveable { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    // Prefill once with the current (possibly already edited) values.
    LaunchedEffect(song?.id) {
        if (song != null && title == null) {
            title = song.title
            artist = song.artist
            album = song.album
        }
    }

    val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            newCover = uri.toString()
            removeCover = false
        }
    }
    val pickedPreview by produceState<android.graphics.Bitmap?>(null, newCover) {
        value = newCover?.let { uri ->
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(Uri.parse(uri))?.use {
                        android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 })
                    }
                }.getOrNull()
            }
        }
    }

    if (song == null) {
        Column(Modifier.fillMaxSize()) {
            TopBar(onBack = navigator::back, title = "Edit info")
            Text("This song is no longer in your library.", color = colors.textSecondary, modifier = Modifier.padding(Spacing.gutter))
        }
        return
    }
    val hasCustomCover = (song.artworkPath != null && !removeCover) || newCover != null

    Column(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        TopBar(onBack = navigator::back, title = "Edit info")
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter),
        ) {
            // Cover
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(180.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        pickCover.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
            ) {
                val preview = pickedPreview
                when {
                    preview != null -> Image(preview.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    removeCover -> Artwork(original ?: song.copy(artworkPath = null), Modifier.fillMaxSize(), corner = 16.dp, sizeHint = 256.dp)
                    else -> Artwork(song, Modifier.fillMaxSize(), corner = 16.dp, sizeHint = 256.dp)
                }
            }
            Spacer(Modifier.height(Spacing.m))
            Row(
                Modifier.align(Alignment.CenterHorizontally),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                PillButton(
                    "Change cover",
                    { pickCover.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    icon = NmIcons.Edit,
                )
                if (hasCustomCover) {
                    PillButton("Use original", {
                        newCover = null
                        removeCover = true
                    })
                }
            }

            Spacer(Modifier.height(Spacing.xl))
            Field("Title", title.orEmpty(), original?.title) { title = it }
            Field("Artist", artist.orEmpty(), original?.artist) { artist = it }
            Field("Album", album.orEmpty(), original?.album) { album = it }

            Spacer(Modifier.height(Spacing.m))
            Text(
                "Changes are saved in CentiMusic and shown everywhere in the app, including the lock screen. " +
                    "Your music file isn't modified.",
                style = NmTheme.type.bodySmall,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(Spacing.xl))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.background)
                .navigationBarsPadding()
                .padding(horizontal = Spacing.gutter, vertical = Spacing.m),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PillButton("Reset to original", { confirmReset = true })
            Spacer(Modifier.weight(1f))
            PillButton(
                "Save",
                {
                    scope.launch {
                        container.music.saveEdit(
                            id = songId,
                            title = title.orEmpty(),
                            artist = artist.orEmpty(),
                            album = album.orEmpty(),
                            newCover = newCover?.let(Uri::parse),
                            removeCover = removeCover,
                        )
                        message("Saved")
                        navigator.back()
                    }
                },
                icon = NmIcons.Check,
                filled = true,
            )
        }
    }

    if (confirmReset) {
        ConfirmDialog(
            title = "Reset to original?",
            message = "Title, artist, album and cover go back to what's in the music file.",
            confirmLabel = "Reset",
            onConfirm = {
                confirmReset = false
                scope.launch {
                    container.music.resetEdit(songId)
                    message("Reset to original")
                    navigator.back()
                }
            },
            onDismiss = { confirmReset = false },
        )
    }
}

@Composable
private fun Field(label: String, value: String, original: String?, onChange: (String) -> Unit) {
    val colors = NmTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        supportingText = if (original != null && original != value.trim()) {
            { Text("Original: $original", color = colors.textTertiary) }
        } else {
            null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.textPrimary,
            unfocusedBorderColor = colors.divider,
            cursorColor = colors.accent,
            focusedLabelColor = colors.textPrimary,
            unfocusedLabelColor = colors.textSecondary,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Spacing.s),
    )
}
