package com.linkvault.app.ui.tools

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.linkvault.app.download.MediaSaver
import com.linkvault.app.tools.PickedFile
import com.linkvault.app.tools.ToolRunner
import com.linkvault.app.tools.ToolState
import com.linkvault.app.tools.queryPicked
import com.linkvault.app.ui.components.*
import com.linkvault.app.ui.components.NeuButton as Button
import com.linkvault.app.ui.components.NeuTonalButton as FilledTonalButton
import com.linkvault.app.ui.components.NeuOutlinedButton as OutlinedButton
import com.linkvault.app.ui.theme.Neon
import com.linkvault.app.ui.theme.Radius

/** Page frame shared by every tool: title with back button and a scrolling body. */
@Composable
fun ToolPage(title: String, subtitle: String?, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenTitle(title, subtitle = subtitle, onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 14.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp), content = content
        )
    }
}

fun formatSize(bytes: Long): String = when {
    bytes < 0 -> ""
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1048576.0)
}

fun formatTime(sec: Double): String {
    val t = sec.toInt().coerceAtLeast(0)
    val m = t / 60
    val s = t % 60
    return String.format(java.util.Locale.US, "%d:%02d", m, s)
}

/** Remembers the files the person picked and offers a button to pick more. */
class PickState(val files: SnapshotStateList<PickedFile>, val open: () -> Unit)

@Composable
fun rememberPicker(mimeTypes: Array<String>, multiple: Boolean): PickState {
    val context = LocalContext.current
    val files = remember { mutableStateListOf<PickedFile>() }
    val single = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) { files.clear(); files.add(queryPicked(context, uri)) }
    }
    val multi = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) { files.clear(); uris.forEach { files.add(queryPicked(context, it)) } }
    }
    return remember(mimeTypes.joinToString(), multiple) {
        PickState(files) { if (multiple) multi.launch(mimeTypes) else single.launch(mimeTypes) }
    }
}

/** "Choose file" card showing what is selected. */
@Composable
fun FilePickerCard(label: String, pick: PickState, enabled: Boolean = true) {
    NeuCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (pick.files.isEmpty()) {
                Text("No file chosen yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                pick.files.take(8).forEach { f ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(f.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.width(8.dp))
                        Text(formatSize(f.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (pick.files.size > 8) Text("+ ${pick.files.size - 8} more", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalButton(onClick = pick.open, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.FolderOpen, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(label)
            }
        }
    }
}

/** Title above a group of options. */
@Composable
fun OptionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
}

/** Even grid of chips, [columns] per row. */
@Composable
fun ChipRows(items: List<Pair<String, Boolean>>, columns: Int = 3, onClick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.withIndex().chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (i, item) -> UniformChip(item.first, item.second, { onClick(i) }, Modifier.weight(1f)) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Progress, errors and finished files for one tool. Place it under the main button. */
@Composable
fun ToolResultPanel(toolId: String) {
    val context = LocalContext.current
    val state by remember(toolId) { ToolRunner.state(toolId) }.collectAsState()
    ResultBody(context, toolId, state)
}

@Composable
private fun ResultBody(context: Context, toolId: String, s: ToolState) {
    if (s.running) {
        NeuCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(s.message.ifBlank { "Working…" }, style = MaterialTheme.typography.bodyMedium)
                if (s.progress in 0f..1f) LinearProgressIndicator(progress = { s.progress }, Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
                else LinearProgressIndicator(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
                Text("You can leave this screen. It keeps working.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { ToolRunner.cancel(toolId) }) { Text("Cancel") }
            }
        }
        return
    }
    if (s.error != null) {
        NeuCard(Modifier.fillMaxWidth()) {
            Text(s.error, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    if (s.results.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Done. Saved in the LinkVault folder.", style = MaterialTheme.typography.titleSmall, color = Neon.green)
            s.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            val before = s.results.firstOrNull()?.originalSize ?: -1L
            if (before > 0) {
                val after = s.results.sumOf { it.size }
                val saved = ((before - after) * 100.0 / before).toInt()
                Text("${formatSize(before)} → ${formatSize(after)}" + if (saved > 0) "  ($saved% smaller)" else "", style = MaterialTheme.typography.bodyMedium)
            }
            s.results.take(30).forEach { r ->
                NeuCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(r.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        Text(formatSize(r.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { MediaSaver.open(context, r.uri, r.audio) }) { Text("Open") }
                            FilledTonalButton(onClick = { MediaSaver.share(context, r.uri, r.audio) }) { Text("Share") }
                        }
                    }
                }
            }
            if (s.results.size > 30) Text("+ ${s.results.size - 30} more files in the LinkVault folder", style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** The big action button; shows a Cancel-less disabled state while running. */
@Composable
fun RunButton(label: String, enabled: Boolean, toolId: String, onClick: () -> Unit) {
    val state by remember(toolId) { ToolRunner.state(toolId) }.collectAsState()
    Button(onClick = onClick, enabled = enabled && !state.running, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
    }
}

@Suppress("unused") private val keepRadius = Radius.card
