package com.linkvault.app.ui.components

import com.linkvault.app.ui.components.NeuButton as Button
import com.linkvault.app.ui.components.NeuTonalButton as FilledTonalButton
import com.linkvault.app.ui.components.NeuOutlinedButton as OutlinedButton
import com.linkvault.app.ui.components.NeuIconButton as IconButton
import com.linkvault.app.ui.components.NeuSwitch as Switch
import com.linkvault.app.ui.components.NeuCheckbox as Checkbox
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.linkvault.app.data.DownloadQuality
import com.linkvault.app.download.CookieStore
import com.linkvault.app.download.DownloadEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.linkvault.app.ui.theme.Radius

/** Pick video or audio and a quality. Used before a download starts and for the default in Settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadOptionsSheet(
    title: String, subtitle: String?, initial: DownloadQuality, confirmLabel: String,
    onConfirm: (DownloadQuality) -> Unit, onDismiss: () -> Unit, showNotice: Boolean = true,
    /** When given, the sheet looks up the file size of every quality and shows it on each row. */
    url: String? = null
) {
    var selected by remember { mutableStateOf(initial) }
    val context = LocalContext.current
    var sizes by remember { mutableStateOf<Map<DownloadQuality, Long?>?>(null) }
    if (url != null) {
        val probeId = remember(url) { "size-" + url.hashCode() }
        LaunchedEffect(url) {
            sizes = withContext(Dispatchers.IO) {
                try {
                    DownloadEngine.ensureInitialised(context)
                    DownloadEngine.estimateSizes(url, probeId, CookieStore.file(context))
                } catch (e: Exception) { null } ?: emptyMap()
            }
        }
        DisposableEffect(url) { onDispose { if (sizes == null) DownloadEngine.cancel(probeId) } }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, shape = Radius.card) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            if (subtitle != null) {
                Text(
                    subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 2,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp)
                )
            }
            DownloadQuality.entries.forEach { q ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { selected = q }.padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selected == q, onClick = null)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(q.label, style = MaterialTheme.typography.bodyLarge)
                        Text(q.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (url != null) {
                        val bytes = sizes?.get(q)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            when {
                                sizes == null -> "Checking…"
                                bytes != null && bytes > 0 -> "~" + sizeText(bytes)
                                else -> "Size unknown"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (bytes != null && bytes > 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (bytes != null && bytes > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
            Button(
                onClick = { onConfirm(selected) }, shape = Radius.pill,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp).height(52.dp)
            ) { Text(confirmLabel, style = MaterialTheme.typography.titleSmall) }
            if (showNotice) {
                Text(
                    "Only download content you have the right to save. Some sites forbid downloads in their terms.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp).padding(top = 12.dp)
                )
            }
        }
    }
}

private fun sizeText(b: Long): String = when {
    b >= 1L shl 30 -> "%.1f GB".format(b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> "%.0f MB".format(b / (1L shl 20).toDouble())
    b >= 1L shl 10 -> "%.0f KB".format(b / (1L shl 10).toDouble())
    else -> "$b B"
}
