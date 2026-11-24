package com.linkvault.app.ui.screens

import com.linkvault.app.ui.components.NeuButton as Button
import com.linkvault.app.ui.components.NeuTonalButton as FilledTonalButton
import com.linkvault.app.ui.components.NeuOutlinedButton as OutlinedButton
import com.linkvault.app.ui.components.NeuIconButton as IconButton
import com.linkvault.app.ui.components.NeuSwitch as Switch
import com.linkvault.app.ui.components.NeuCheckbox as Checkbox
import com.linkvault.app.ui.components.neuRaised
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import com.linkvault.app.ui.theme.Neon
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.linkvault.app.MainViewModel
import com.linkvault.app.data.LinkEntity
import com.linkvault.app.ui.components.*
import com.linkvault.app.ui.theme.Radius
import com.linkvault.app.util.Format
import com.linkvault.app.util.LinkOpener
import com.linkvault.app.util.MediaLinks
import com.linkvault.app.util.MetadataFetcher
import com.linkvault.app.util.Thumbnails
import com.linkvault.app.util.Platform

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(vm: MainViewModel, id: Long, onBack: () -> Unit, onEdit: (Long) -> Unit, onOpenDownloads: () -> Unit) {
    val context = LocalContext.current
    val link by remember(id) { vm.repo.observeLink(id) }.collectAsState(initial = null)
    val categories by vm.categories.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }
    var deleted by remember { mutableStateOf(false) }
    var showDownload by remember { mutableStateOf(false) }
    val askNotifications = rememberNotificationPermissionRequester()

    val l = link
    // If the link disappears (deleted elsewhere) leave the screen instead of showing a blank page.
    LaunchedEffect(l, deleted) { if (deleted) onBack() }

    // Links saved before thumbnails existed (or restored from a backup) get their cover once,
    // the first time they are opened here.
    val fetchEnabled = vm.settings.collectAsState().value?.fetchTitles == true
    LaunchedEffect(l?.normalizedUrl, fetchEnabled) {
        val link = l ?: return@LaunchedEffect
        val platform = Platform.fromKey(link.platform)
        if (fetchEnabled && MediaLinks.isVideo(platform, link.domain, link.url) && !Thumbnails.exists(context, link.normalizedUrl)) {
            val meta = MetadataFetcher.fetchMeta(link.url, platform, wantImage = true)
            meta.thumbnail?.let { Thumbnails.save(context, link.normalizedUrl, it) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Link details", onBack = onBack)
        if (l == null) { return@Column }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 24.dp)) {
            val isVideo = remember(l.url, l.platform) { MediaLinks.isVideo(Platform.fromKey(l.platform), l.domain, l.url) }
            val cover = rememberThumbnail(l.normalizedUrl, isVideo)
            if (cover != null) {
                CoverImage(cover)
                Spacer(Modifier.height(16.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlatformIcon(Platform.fromKey(l.platform), l.domain, size = 52.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(l.title, style = MaterialTheme.typography.titleLarge, maxLines = 3)
                    Text(
                        if (Platform.fromKey(l.platform) == Platform.WEB) l.domain else Platform.fromKey(l.platform).label + " · " + l.domain,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Button(onClick = { vm.open(l, context) }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = Radius.pill) {
                Icon(Icons.Outlined.OpenInNew, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Open Link", style = MaterialTheme.typography.titleSmall)
            }

            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = { showDownload = true }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = Radius.pill) {
                Icon(Icons.Outlined.Download, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Download", style = MaterialTheme.typography.titleSmall)
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ActionTile(Icons.Outlined.Edit, "Edit") { onEdit(l.id) }
                ActionTile(Icons.Outlined.ContentCopy, "Copy") {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Link", l.url)); vm.toast("Link copied")
                }
                ActionTile(Icons.Outlined.Share, "Share") { LinkOpener.share(context, l.url, l.title) }
                ActionTile(if (l.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder, "Favorite", active = l.isFavorite) { vm.toggleFavorite(l) }
                ActionTile(Icons.Outlined.PushPin, "Pin", active = l.isPinned) { vm.togglePinned(l) }
            }

            Spacer(Modifier.height(16.dp))
            Surface(Modifier.fillMaxWidth().neuRaised(Radius.card, 5.dp), shape = Radius.card, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                    Field("URL") { SelectionContainer { Text(l.url, style = MaterialTheme.typography.bodyMedium) } }
                    Field("Category") { Text(categories.firstOrNull { it.id == l.categoryId }?.name ?: "Uncategorized", style = MaterialTheme.typography.bodyMedium) }
                    Field("Tags") {
                        if (l.tagList.isEmpty()) Text("None", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { l.tagList.forEach { TagPill(it) } }
                    }
                    Field("Notes") {
                        if (l.notes.isBlank()) Text("No notes", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else SelectionContainer { Text(l.notes, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("${l.openCount}", "Times opened", Icons.Outlined.OpenInNew, Neon.lime, Modifier.weight(1f).height(112.dp))
                StatCard(l.lastOpenedAt?.let { Format.relative(it) } ?: "Never", "Last opened", Icons.Outlined.History, Neon.green, Modifier.weight(1f).height(112.dp))
            }
            Text(
                "Added ${Format.full(l.createdAt)}", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp, start = 4.dp)
            )

            Spacer(Modifier.height(20.dp))
            OutlinedButton(
                onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = Radius.pill,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Icon(Icons.Outlined.DeleteOutline, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Delete") }
        }
    }

    if (showDownload && l != null) DownloadOptionsSheet(
        title = "Download", subtitle = l.title, url = l.url, initial = vm.settings.collectAsState().value?.downloadQuality ?: com.linkvault.app.data.DownloadQuality.BEST,
        confirmLabel = "Start download",
        onConfirm = { q ->
            showDownload = false
            askNotifications()
            vm.download(l, l.url, l.title, q)
            vm.messages.tryEmit(com.linkvault.app.UiMessage("Download queued", "View") { onOpenDownloads() })
        },
        onDismiss = { showDownload = false }
    )

    if (confirmDelete && l != null) ConfirmDialog(
        title = "Delete this link?", text = "It will be removed from LinkVault. You can undo right after.",
        confirmLabel = "Delete", destructive = true,
        onConfirm = { confirmDelete = false; deleted = true; vm.delete(listOf(l.id)) },
        onDismiss = { confirmDelete = false }
    )
}

@Composable
private fun Field(label: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun ActionTile(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier.clip(Radius.small).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(52.dp).neuRaised(CircleShape, 4.dp, if (active) Neon.lime else MaterialTheme.colorScheme.surface).clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, label, Modifier.size(22.dp), tint = if (active) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
