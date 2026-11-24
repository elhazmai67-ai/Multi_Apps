package com.linkvault.app.bubble

import com.linkvault.app.ui.components.NeuButton as Button
import com.linkvault.app.ui.components.NeuTonalButton as FilledTonalButton
import com.linkvault.app.ui.components.NeuOutlinedButton as OutlinedButton
import com.linkvault.app.ui.components.NeuIconButton as IconButton
import com.linkvault.app.ui.components.NeuSwitch as Switch
import com.linkvault.app.ui.components.NeuCheckbox as Checkbox
import com.linkvault.app.ui.components.neuRaised
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.linkvault.app.MainViewModel
import com.linkvault.app.SaveOutcome
import com.linkvault.app.data.DownloadQuality
import com.linkvault.app.data.LinkEntity
import com.linkvault.app.data.ThemeMode
import com.linkvault.app.download.DownloadCenter
import com.linkvault.app.ui.components.CategoryChip
import com.linkvault.app.ui.screens.rememberNotificationPermissionRequester
import com.linkvault.app.ui.theme.LinkVaultTheme
import com.linkvault.app.ui.theme.Radius
import com.linkvault.app.util.MetadataFetcher
import com.linkvault.app.util.UrlResult
import com.linkvault.app.util.UrlUtils
import kotlinx.coroutines.launch

/**
 * The small panel the floating button opens. Because this is a real, focused activity, Android lets
 * it read the clipboard (background apps can't), so the copied link is already filled in.
 */
class QuickAddActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private var clipUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by vm.settings.collectAsState()
            LinkVaultTheme(settings?.theme ?: ThemeMode.DARK) {
                QuickAddPanel(
                    vm = vm, clipUrl = clipUrl, readClipboard = { readClipboard().also { clipUrl = it } },
                    fetchTitles = settings?.fetchTitles ?: true, quality = settings?.downloadQuality ?: DownloadQuality.BEST,
                    onClose = { finish() }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        BubbleService.setBubbleVisible(false)
    }

    override fun onStop() {
        super.onStop()
        BubbleService.setBubbleVisible(true)
        if (!isFinishing) finish()   // switching away closes the panel; it never lingers in the background
    }

    /** Clipboard access is only granted once this window has focus. */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) clipUrl = readClipboard()
    }

    private fun readClipboard(): String? = try {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
        UrlUtils.extractUrl(text)
    } catch (_: Exception) { null }
}

@Composable
private fun QuickAddPanel(
    vm: MainViewModel, clipUrl: String?, readClipboard: () -> String?,
    fetchTitles: Boolean, quality: DownloadQuality, onClose: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val categories by vm.categories.collectAsState()
    val askNotifications = rememberNotificationPermissionRequester()

    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var duplicate by remember { mutableStateOf<LinkEntity?>(null) }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var alsoSave by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var pickQuality by remember { mutableStateOf<String?>(null) }   // the link to download, while the quality sheet is open

    // Fill the field from the clipboard once, if the user hasn't typed anything.
    LaunchedEffect(clipUrl) { if (text.isBlank() && clipUrl != null) text = clipUrl }

    fun done(message: String) { Toast.makeText(context, message, Toast.LENGTH_SHORT).show(); onClose() }

    /** Looks up the page title and (for video links) the cover if allowed; failures just mean blank/none, never an error. */
    suspend fun metaFor(parsed: com.linkvault.app.util.ParsedUrl): com.linkvault.app.util.LinkMeta {
        if (!fetchTitles || parsed.platform == com.linkvault.app.util.Platform.WHATSAPP) return com.linkvault.app.util.LinkMeta(null, null)
        val wantImage = com.linkvault.app.util.MediaLinks.isVideo(parsed.platform, parsed.domain, parsed.url)
        return try {
            MetadataFetcher.fetchMeta(parsed.url, parsed.platform, wantImage)
        } catch (_: Exception) { com.linkvault.app.util.LinkMeta(null, null) }
    }

    fun save(force: Boolean) {
        val parsed = (UrlUtils.parse(text) as? UrlResult.Ok)?.parsed
        if (parsed == null) { error = (UrlUtils.parse(text) as? UrlResult.Error)?.message ?: "Enter a link."; return }
        scope.launch {
            busy = true; error = null; duplicate = null
            val meta = metaFor(parsed)
            when (val o = vm.saveLink(null, parsed.url, meta.title.orEmpty(), categoryId, emptyList(), "", false, false, force)) {
                is SaveOutcome.Saved -> {
                    meta.thumbnail?.let { com.linkvault.app.util.Thumbnails.save(context, parsed.normalized, it) }
                    done("Saved to LinkVault")
                }
                is SaveOutcome.Duplicate -> duplicate = o.existing
                is SaveOutcome.Invalid -> error = o.message
            }
            busy = false
        }
    }

    fun download(q: DownloadQuality) {
        val parsed = (UrlUtils.parse(text) as? UrlResult.Ok)?.parsed
        if (parsed == null) { error = (UrlUtils.parse(text) as? UrlResult.Error)?.message ?: "Enter a link."; return }
        scope.launch {
            busy = true; error = null; duplicate = null
            val meta = metaFor(parsed)
            val title = meta.title.orEmpty()
            var linkId: Long? = null
            if (alsoSave) {
                when (val o = vm.saveLink(null, parsed.url, title, categoryId, emptyList(), "", false, false, false)) {
                    is SaveOutcome.Saved -> {
                        linkId = o.id
                        meta.thumbnail?.let { com.linkvault.app.util.Thumbnails.save(context, parsed.normalized, it) }
                    }
                    is SaveOutcome.Duplicate -> linkId = o.existing.id   // already saved: just download it
                    is SaveOutcome.Invalid -> { error = o.message; busy = false; return@launch }
                }
            }
            askNotifications()
            try {
                DownloadCenter.enqueue(context.applicationContext, linkId, parsed.url, title.ifBlank { parsed.suggestedTitle }, q)
                done("Download started")
            } catch (e: Exception) {
                error = "Couldn't start the download."
            }
            busy = false
        }
    }

    val noRipple = remember { MutableInteractionSource() }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))
            .clickable(interactionSource = noRipple, indication = null) { onClose() }
            .safeDrawingPadding().imePadding().padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            Modifier.fillMaxWidth().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
            shape = Radius.card, color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Save or download a link", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Close") }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text, onValueChange = { text = it; error = null; duplicate = null },
                    label = { Text("Link") }, singleLine = true, isError = error != null, enabled = !busy,
                    supportingText = error?.let { { Text(it) } },
                    trailingIcon = {
                        IconButton(onClick = {
                            val u = readClipboard()
                            if (u != null) { text = u; error = null; duplicate = null } else error = "No link found on the clipboard."
                        }) { Icon(Icons.Outlined.ContentPaste, "Paste from clipboard") }
                    },
                    modifier = Modifier.fillMaxWidth(), shape = Radius.small
                )
                duplicate?.let { d ->
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Already saved as “${d.title}”.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { save(force = true) }, enabled = !busy) { Text("Save anyway") }
                    }
                }
                if (categories.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Category", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CategoryChip("None", categoryId == null, { categoryId = null })
                        categories.forEach { c -> CategoryChip(c.name, categoryId == c.id, { categoryId = c.id }) }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().clickable { alsoSave = !alsoSave }.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = alsoSave, onCheckedChange = { alsoSave = it })
                    Text("Also save the link when downloading", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { save(force = false) }, enabled = !busy && text.isNotBlank(), shape = Radius.pill, modifier = Modifier.weight(1f).height(48.dp)) {
                        Text(if (busy) "Working…" else "Save")
                    }
                    FilledTonalButton(onClick = {
                        val r = UrlUtils.parse(text)
                        if (r is UrlResult.Ok) pickQuality = r.parsed.url else error = (r as? UrlResult.Error)?.message ?: "Enter a link."
                    }, enabled = !busy && text.isNotBlank(), shape = Radius.pill, modifier = Modifier.weight(1f).height(48.dp)) {
                        Text("Download")
                    }
                }
                Text(
                    "You choose the quality when you tap Download. Default: ${quality.label} (Settings → Downloads).",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }
    }
    pickQuality?.let { u ->
        com.linkvault.app.ui.components.DownloadOptionsSheet(
            title = "Download quality", subtitle = u, url = u, initial = quality, confirmLabel = "Start download", showNotice = false,
            onConfirm = { q -> pickQuality = null; download(q) },
            onDismiss = { pickQuality = null }
        )
    }
}
