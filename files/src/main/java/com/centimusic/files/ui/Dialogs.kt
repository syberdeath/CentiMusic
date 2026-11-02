package com.centimusic.files.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.centimusic.files.data.FileItem
import com.centimusic.files.data.SortField
import com.centimusic.files.data.SortOrder
import com.centimusic.files.data.formatSize
import com.centimusic.files.data.isValidName
import com.centimusic.files.theme.FIcons
import com.centimusic.files.theme.NmTheme
import com.centimusic.files.theme.Spacing
import java.io.File
import java.text.DateFormat
import java.util.Date

// ---- Intents ------------------------------------------------------------------------------------

private fun Context.uriFor(path: String): Uri = FileProvider.getUriForFile(this, "$packageName.files", File(path))

/** Opens a file in the app the user picks for its type. Returns false when nothing can open it. */
fun Context.openFile(item: FileItem): Boolean = try {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uriFor(item.path), item.mimeType)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: IllegalArgumentException) {
    false
}

fun Context.shareFiles(items: List<FileItem>) {
    val files = items.filter { !it.isDirectory }
    if (files.isEmpty()) return
    val uris = ArrayList(files.map { uriFor(it.path) })
    val types = files.map { it.mimeType }.distinct()
    val type = types.singleOrNull() ?: types.map { it.substringBefore('/') }.distinct().singleOrNull()?.let { "$it/*" } ?: "*/*"
    val send = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    }.setType(type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

// ---- Dialogs ------------------------------------------------------------------------------------

@Composable
fun NameDialog(title: String, initial: String, confirm: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    val c = NmTheme.colors
    val selectEnd = initial.lastIndexOf('.').takeIf { it > 0 } ?: initial.length
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, selectEnd))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val valid = isValidName(value.text.trim())
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text(title.uppercase(), style = NmTheme.type.sectionTitle, color = c.textPrimary) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                textStyle = NmTheme.type.title.copy(color = c.textPrimary),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.text.trim()) }, enabled = valid) {
                Text(confirm.uppercase(), style = NmTheme.type.label, color = if (valid) c.accent else c.textTertiary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL", style = NmTheme.type.label, color = c.textSecondary) }
        },
    )
}

@Composable
fun ConfirmDialog(title: String, body: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val c = NmTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text(title.uppercase(), style = NmTheme.type.sectionTitle, color = c.textPrimary) },
        text = { Text(body, style = NmTheme.type.body, color = c.textSecondary) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirm.uppercase(), style = NmTheme.type.label, color = c.accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL", style = NmTheme.type.label, color = c.textSecondary) }
        },
    )
}

@Composable
fun InfoDialog(item: FileItem, onDismiss: () -> Unit) {
    val c = NmTheme.colors
    val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(item.modified))
    val rows = buildList {
        add("Name" to item.name)
        add("Location" to item.path.substringBeforeLast('/'))
        if (item.isDirectory) add("Contains" to "${item.childCount.coerceAtLeast(0)} items")
        else {
            add("Size" to "${formatSize(item.size)}  (${"%,d".format(item.size)} bytes)")
            add("Type" to item.mimeType.takeIf { it != "*/*" }.orEmpty().ifEmpty { item.extension.uppercase().ifEmpty { "File" } })
        }
        add("Modified" to date)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text("FILE INFO", style = NmTheme.type.sectionTitle, color = c.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                rows.forEach { (k, v) ->
                    Column {
                        Text(k.uppercase(), style = NmTheme.type.dotCaption, color = c.textTertiary)
                        Text(v, style = NmTheme.type.body, color = c.textPrimary)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("DONE", style = NmTheme.type.label, color = c.accent) }
        },
    )
}

// ---- Sheets -------------------------------------------------------------------------------------

data class SheetAction(val label: String, val icon: ImageVector, val destructive: Boolean = false, val run: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionSheet(title: String, subtitle: String?, actions: List<SheetAction>, onDismiss: () -> Unit) {
    val c = NmTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface,
        dragHandle = { SheetHandle() },
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = Spacing.l)) {
            Text(
                title, style = NmTheme.type.title, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = Spacing.gutter),
            )
            if (subtitle != null) {
                Text(subtitle, style = NmTheme.type.bodySmall, color = c.textSecondary, modifier = Modifier.padding(horizontal = Spacing.gutter))
            }
            Spacer(Modifier.height(Spacing.m))
            HorizontalDivider(color = c.divider)
            actions.forEach { a ->
                Row(
                    Modifier.fillMaxWidth().clickable { onDismiss(); a.run() }.padding(horizontal = Spacing.gutter).height(52.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(a.icon, null, tint = if (a.destructive) c.accent else c.textPrimary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(Spacing.l))
                    Text(a.label, style = NmTheme.type.title, color = if (a.destructive) c.accent else c.textPrimary)
                }
            }
        }
    }
}

@Composable
private fun SheetHandle() {
    Box(Modifier.padding(vertical = Spacing.m)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(5) { Box(Modifier.size(4.dp).clip(CircleShape).background(NmTheme.colors.textTertiary)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortSheet(current: SortOrder, onChange: (SortOrder) -> Unit, onDismiss: () -> Unit) {
    val c = NmTheme.colors
    val options = listOf(
        Triple(SortField.NAME, true, "Name, A to Z"),
        Triple(SortField.NAME, false, "Name, Z to A"),
        Triple(SortField.DATE, false, "Date, newest first"),
        Triple(SortField.DATE, true, "Date, oldest first"),
        Triple(SortField.SIZE, false, "Size, largest first"),
        Triple(SortField.SIZE, true, "Size, smallest first"),
        Triple(SortField.TYPE, true, "Type"),
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface,
        dragHandle = { SheetHandle() },
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = Spacing.l)) {
            Text("SORT BY", style = NmTheme.type.sectionTitle, color = c.textPrimary, modifier = Modifier.padding(horizontal = Spacing.gutter))
            Spacer(Modifier.height(Spacing.s))
            options.forEach { (field, asc, label) ->
                val selected = current.field == field && (current.ascending == asc || field == SortField.TYPE)
                Row(
                    Modifier.fillMaxWidth().clickable { onChange(SortOrder(field, asc)); onDismiss() }
                        .padding(horizontal = Spacing.gutter).height(50.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, style = NmTheme.type.title, color = c.textPrimary, modifier = Modifier.weight(1f))
                    if (selected) Box(Modifier.size(8.dp).clip(CircleShape).background(c.accent))
                }
            }
        }
    }
}
