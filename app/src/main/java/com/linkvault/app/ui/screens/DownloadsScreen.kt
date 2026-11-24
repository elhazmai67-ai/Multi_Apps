package com.linkvault.app.ui.screens

import com.linkvault.app.ui.components.NeuButton as Button
import com.linkvault.app.ui.components.NeuTonalButton as FilledTonalButton
import com.linkvault.app.ui.components.NeuOutlinedButton as OutlinedButton
import com.linkvault.app.ui.components.NeuIconButton as IconButton
import com.linkvault.app.ui.components.NeuSwitch as Switch
import com.linkvault.app.ui.components.NeuCheckbox as Checkbox
import com.linkvault.app.ui.components.neuRaised
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.linkvault.app.MainViewModel
import com.linkvault.app.data.DownloadEntity
import com.linkvault.app.data.DownloadQuality
import com.linkvault.app.data.DownloadStatus
import com.linkvault.app.download.DownloadCenter
import com.linkvault.app.download.MediaSaver
import com.linkvault.app.ui.components.*
import com.linkvault.app.ui.theme.Radius
import com.linkvault.app.util.ParsedUrl
import com.linkvault.app.util.UrlResult
import com.linkvault.app.util.UrlUtils

/** Returns a function that asks for the notification permission once (Android 13+), so progress is visible. */
@Composable
fun rememberNotificationPermissionRequester(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return remember {
        {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
fun DownloadsScreen(vm: MainViewModel, onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val downloads by vm.downloads.collectAsState()
    val progress by DownloadCenter.progress.collectAsState()
    val settings by vm.settings.collectAsState()
    val askNotifications = rememberNotificationPermissionRequester()

    var showAdd by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<ParsedUrl?>(null) }
    var confirmRemove by remember { mutableStateOf<DownloadEntity?>(null) }
    val finishedCount = downloads.count { it.status in DownloadStatus.finished }
    val activeCount = downloads.size - finishedCount

    Column(Modifier.fillMaxSize()) {
        ScreenTitle(
            "Downloads", onBack = onBack,
            subtitle = when {
                downloads.isEmpty() -> "Videos, audio, images, PDFs and files"
                activeCount > 0 -> "$activeCount in progress"
                else -> "${downloads.size} saved"
            },
            trailing = {
                IconButton(onClick = { showAdd = true }) { Icon(Icons.Outlined.Add, "Download a link") }
                if (finishedCount > 0) IconButton(onClick = { vm.clearFinishedDownloads() }) { Icon(Icons.Outlined.ClearAll, "Clear finished") }
            }
        )

        if (downloads.isEmpty()) {
            EmptyState(
                "Nothing downloaded yet",
                "Open a saved link and tap Download, or paste any link here: video, audio, image, PDF or other file. Saved under LinkVault in Movies, Music, Pictures or Download.",
                actionLabel = "Download a link", onAction = { showAdd = true }
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(downloads, key = { it.id }) { d ->
                    DownloadRow(
                        d, progress[d.id],
                        onCancel = { vm.cancelDownload(d.id) },
                        onRetry = { askNotifications(); vm.retryDownload(d.id) },
                        onOpen = { if (!MediaSaver.open(context, d.fileUri, DownloadQuality.fromKey(d.quality).audio)) vm.toast("Couldn't open this file. It may have been moved or deleted.") },
                        onShare = { MediaSaver.share(context, d.fileUri, DownloadQuality.fromKey(d.quality).audio) },
                        onRemove = { if (d.status == DownloadStatus.DONE) confirmRemove = d else vm.removeDownload(d.id, deleteFile = false) }
                    )
                }
            }
        }
    }

    if (showAdd) TextInputDialog(
        title = "Download a link", label = "Paste a link", confirmLabel = "Next",
        validate = { t -> (UrlUtils.parse(t) as? UrlResult.Error)?.message },
        onConfirm = { t -> (UrlUtils.parse(t) as? UrlResult.Ok)?.let { pending = it.parsed }; showAdd = false },
        onDismiss = { showAdd = false }
    )

    pending?.let { p ->
        DownloadOptionsSheet(
            title = "Download", subtitle = p.url, url = p.url, initial = settings?.downloadQuality ?: DownloadQuality.BEST, confirmLabel = "Start download",
            onConfirm = { q -> askNotifications(); vm.download(null, p.url, p.suggestedTitle, q); pending = null },
            onDismiss = { pending = null }
        )
    }

    confirmRemove?.let { d ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null }, shape = Radius.card, title = { Text("Remove this download?") },
            text = { Text("You can keep the file on your device and only remove it from this list, or delete the file too.") },
            confirmButton = {
                TextButton(
                    onClick = { vm.removeDownload(d.id, deleteFile = true); confirmRemove = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete file") }
            },
            dismissButton = { TextButton(onClick = { vm.removeDownload(d.id, deleteFile = false); confirmRemove = null }) { Text("Remove from list") } }
        )
    }
}

@Composable
private fun DownloadRow(
    d: DownloadEntity, progress: DownloadCenter.Progress?,
    onCancel: () -> Unit, onRetry: () -> Unit, onOpen: () -> Unit, onShare: () -> Unit, onRemove: () -> Unit
) {
    val quality = DownloadQuality.fromKey(d.quality)
    Surface(Modifier.fillMaxWidth().neuRaised(Radius.card, 5.dp), shape = Radius.card, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when { quality.audio -> Icons.Outlined.MusicNote; quality == DownloadQuality.FILE -> Icons.Outlined.InsertDriveFile; else -> Icons.Outlined.Movie }, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(d.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        statusText(d, progress, quality), style = MaterialTheme.typography.bodySmall, maxLines = 3,
                        color = if (d.status == DownloadStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (d.status == DownloadStatus.RUNNING) {
                Spacer(Modifier.height(10.dp))
                val pct = progress?.percent ?: -1f
                if (pct < 0f) LinearProgressIndicator(Modifier.fillMaxWidth().padding(end = 8.dp))
                else LinearProgressIndicator(progress = { (pct / 100f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(end = 8.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                when (d.status) {
                    DownloadStatus.QUEUED, DownloadStatus.RUNNING ->
                        IconButton(onClick = onCancel) { Icon(Icons.Outlined.Close, "Cancel") }
                    DownloadStatus.DONE -> {
                        IconButton(onClick = onOpen) { Icon(Icons.Outlined.PlayArrow, "Open") }
                        IconButton(onClick = onShare) { Icon(Icons.Outlined.Share, "Share") }
                        IconButton(onClick = onRemove) { Icon(Icons.Outlined.DeleteOutline, "Remove") }
                    }
                    else -> {
                        IconButton(onClick = onRetry) { Icon(Icons.Outlined.Refresh, "Retry") }
                        IconButton(onClick = onRemove) { Icon(Icons.Outlined.DeleteOutline, "Remove") }
                    }
                }
            }
        }
    }
}

private fun statusText(d: DownloadEntity, p: DownloadCenter.Progress?, q: DownloadQuality): String = when (d.status) {
    DownloadStatus.QUEUED -> "Waiting in queue · ${q.label}"
    DownloadStatus.RUNNING ->
        if (p == null || p.percent < 0f) "Starting… · ${q.label}"
        else "${p.percent.toInt()}%" + (if (p.etaSeconds > 0) " · ${formatEta(p.etaSeconds)} left" else "") + " · ${q.label}"
    DownloadStatus.DONE -> "Saved · ${formatBytes(d.sizeBytes)} · in the LinkVault folder"
    DownloadStatus.FAILED -> d.error ?: "Download failed."
    else -> "Canceled"
}

private fun formatEta(seconds: Long): String =
    if (seconds >= 3600) "${seconds / 3600}h ${(seconds % 3600) / 60}m" else if (seconds >= 60) "${seconds / 60}m ${seconds % 60}s" else "${seconds}s"

private fun formatBytes(b: Long): String = when {
    b >= 1L shl 30 -> "%.1f GB".format(b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> "%.1f MB".format(b / (1L shl 20).toDouble())
    b >= 1L shl 10 -> "%.0f KB".format(b / (1L shl 10).toDouble())
    else -> "$b B"
}
