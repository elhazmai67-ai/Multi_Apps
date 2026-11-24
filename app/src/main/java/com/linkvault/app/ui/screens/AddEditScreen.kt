package com.linkvault.app.ui.screens

import com.linkvault.app.ui.components.NeuButton as Button
import com.linkvault.app.ui.components.NeuTonalButton as FilledTonalButton
import com.linkvault.app.ui.components.NeuOutlinedButton as OutlinedButton
import com.linkvault.app.ui.components.NeuIconButton as IconButton
import com.linkvault.app.ui.components.NeuSwitch as Switch
import com.linkvault.app.ui.components.NeuCheckbox as Checkbox
import com.linkvault.app.ui.components.neuRaised
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.linkvault.app.MainViewModel
import com.linkvault.app.SaveOutcome
import com.linkvault.app.data.LinkEntity
import com.linkvault.app.data.Tags
import com.linkvault.app.ui.components.*
import com.linkvault.app.ui.theme.Radius
import com.linkvault.app.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEditScreen(
    vm: MainViewModel,
    editId: Long?,
    initialUrl: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onOpenExisting: (LinkEntity) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val categories by vm.categories.collectAsState()
    val tagCounts by vm.tagCounts.collectAsState()
    val settings by vm.settings.collectAsState()

    var url by rememberSaveable { mutableStateOf(initialUrl.orEmpty()) }
    var title by rememberSaveable { mutableStateOf("") }
    var titleEdited by rememberSaveable { mutableStateOf(false) }
    var notes by rememberSaveable { mutableStateOf("") }
    var tagInput by rememberSaveable { mutableStateOf("") }
    var tags by rememberSaveable { mutableStateOf(listOf<String>()) }
    var categoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var favorite by rememberSaveable { mutableStateOf(false) }
    var pinned by rememberSaveable { mutableStateOf(false) }
    var loaded by rememberSaveable { mutableStateOf(editId == null) }
    var categoryInit by rememberSaveable { mutableStateOf(editId != null) }
    var showUrlError by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var duplicate by remember { mutableStateOf<LinkEntity?>(null) }
    var fetching by remember { mutableStateOf(false) }
    var cover by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    // Edit mode: load the existing link once.
    LaunchedEffect(editId) {
        if (editId != null && !loaded) {
            val l = vm.repo.getLink(editId)
            if (l == null) { vm.toast("That link no longer exists."); onBack(); return@LaunchedEffect }
            url = l.url; title = l.title; titleEdited = true; notes = l.notes; tags = l.tagList
            categoryId = l.categoryId; favorite = l.isFavorite; pinned = l.isPinned; loaded = true
        }
    }
    // New link: default category = the one being browsed, else "Other".
    LaunchedEffect(categories) {
        if (!categoryInit && categories.isNotEmpty()) {
            categoryId = vm.homeFilters.categoryId?.takeIf { it > 0 } ?: categories.firstOrNull { it.name.equals("Other", true) }?.id
            categoryInit = true
        }
    }
    // New link opened empty: pre-fill from clipboard once, if the user allows it.
    LaunchedEffect(settings?.clipboardSuggestions) {
        if (editId == null && url.isEmpty() && settings?.clipboardSuggestions == true) {
            readClipboardForAdd(context)?.let { url = it }
        }
    }

    val parsed = (UrlUtils.parse(url) as? UrlResult.Ok)?.parsed
    // Auto title + cover: instant suggestion first, then (optionally) the real title and, for
    // video links, the cover image.
    LaunchedEffect(parsed?.normalized, settings?.fetchTitles) {
        if (parsed == null) { cover = null; return@LaunchedEffect }
        val isVideo = MediaLinks.isVideo(parsed.platform, parsed.domain, parsed.url)
        cover = if (isVideo) withContext(Dispatchers.IO) { Thumbnails.load(context, parsed.normalized) } else null
        if (!titleEdited) title = parsed.suggestedTitle
        val wantTitle = !titleEdited
        val wantImage = isVideo && cover == null
        if (settings?.fetchTitles == true && parsed.platform != Platform.WHATSAPP && (wantTitle || wantImage)) {
            delay(600)
            fetching = true
            val meta = MetadataFetcher.fetchMeta(parsed.url, parsed.platform, wantImage)
            fetching = false
            if (meta.title != null && !titleEdited) title = meta.title
            if (meta.thumbnail != null) cover = meta.thumbnail
        }
    }

    fun addTag(raw: String) {
        raw.split(',').map { Tags.clean(it) }.filter { it.isNotEmpty() }.forEach { t ->
            if (tags.none { it.equals(t, true) }) tags = tags + t
        }
        tagInput = ""
    }

    fun save(force: Boolean, overrideId: Long? = null) {
        if (saving) return
        addTag(tagInput)
        showUrlError = true
        if (parsed == null) return
        saving = true
        scope.launch {
            when (val r = vm.saveLink(overrideId ?: editId, url, title, categoryId, tags, notes, favorite, pinned, force)) {
                is SaveOutcome.Saved -> {
                    cover?.let { Thumbnails.save(context, parsed.normalized, it) }
                    vm.toast(if (editId != null || overrideId != null) "Link updated" else "Link saved")
                    onSaved()
                }
                is SaveOutcome.Duplicate -> duplicate = r.existing
                is SaveOutcome.Invalid -> vm.toast(r.message)
            }
            saving = false
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenTitle(if (editId == null) "Add link" else "Edit link", onBack = onBack)

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 16.dp)) {
            OutlinedTextField(
                value = url, onValueChange = { url = it.trim(); showUrlError = false },
                label = { Text("URL") }, placeholder = { Text("Paste or type a link") },
                singleLine = true, shape = Radius.small, modifier = Modifier.fillMaxWidth(),
                isError = showUrlError && parsed == null,
                supportingText = {
                    if (showUrlError && parsed == null) Text((UrlUtils.parse(url) as? UrlResult.Error)?.message ?: "That doesn't look like a valid link.")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                trailingIcon = {
                    if (url.isEmpty()) {
                        IconButton(onClick = {
                            val clip = readClipboardForAdd(context)
                            if (clip != null) url = clip else vm.toast("No link found on the clipboard.")
                        }) { Icon(Icons.Outlined.ContentPaste, "Paste from clipboard") }
                    } else {
                        IconButton(onClick = { url = "" }) { Icon(Icons.Outlined.Close, "Clear URL") }
                    }
                }
            )

            AnimatedVisibility(parsed != null) {
                parsed?.let { p ->
                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        PlatformIcon(p.platform, p.domain, size = 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (p.platform == Platform.WEB) "Website" else p.platform.label, style = MaterialTheme.typography.titleSmall)
                            Text(p.domain, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (fetching) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                }
            }

            cover?.let { bmp ->
                CoverImage(bmp.asImageBitmap(), Modifier.padding(top = 12.dp))
            }

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = title, onValueChange = { title = it; titleEdited = true },
                label = { Text("Title") }, singleLine = true, shape = Radius.small, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )

            Text("Category", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { CategoryChip("None", categoryId == null, { categoryId = null }) }
                items(categories, key = { it.id }) { c -> CategoryChip(c.name, categoryId == c.id, { categoryId = c.id }) }
            }

            Text("Tags", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            OutlinedTextField(
                value = tagInput,
                onValueChange = { v -> if (v.endsWith(",")) addTag(v) else tagInput = v },
                placeholder = { Text("Add a tag and press Done") }, singleLine = true, shape = Radius.small,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { addTag(tagInput) })
            )
            if (tags.isNotEmpty()) {
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tags.forEach { t ->
                        InputChip(selected = false, onClick = { tags = tags - t }, label = { Text("#$t") }, shape = Radius.small,
                            trailingIcon = { Icon(Icons.Outlined.Close, "Remove tag $t", Modifier.size(16.dp)) })
                    }
                }
            }
            val suggestions = tagCounts.map { it.name }.filter { s -> tags.none { it.equals(s, true) } }.take(8)
            if (suggestions.isNotEmpty()) {
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    suggestions.forEach { s -> SuggestionChip(onClick = { addTag(s) }, label = { Text("#$s") }, shape = Radius.small) }
                }
            }

            OutlinedTextField(
                value = notes, onValueChange = { notes = it }, label = { Text("Notes") },
                minLines = 3, maxLines = 6, shape = Radius.small,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            )

            SwitchRow("Favorite", "Show in the Favorites tab", favorite) { favorite = it }
            SwitchRow("Pin to home", "Keep at the top of Home and in the widget", pinned) { pinned = it }
        }

        Surface(tonalElevation = 0.dp, color = MaterialTheme.colorScheme.background, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Button(
                onClick = { save(force = false) }, enabled = !saving && loaded,
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp), shape = Radius.pill
            ) { Text(if (editId == null) "Save Link" else "Save Changes", style = MaterialTheme.typography.titleSmall) }
        }
    }

    duplicate?.let { existing ->
        AlertDialog(
            onDismissRequest = { duplicate = null }, shape = Radius.card,
            title = { Text("This link already exists.") },
            text = { Text("\"${existing.title}\" was saved on ${Format.relative(existing.createdAt)}.") },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { duplicate = null; onOpenExisting(existing) }) { Text("Open Existing") }
                    TextButton(onClick = { duplicate = null; save(force = true, overrideId = existing.id) }) { Text("Update Existing") }
                    TextButton(onClick = { duplicate = null; save(force = true) }) { Text("Save Anyway") }
                }
            },
            dismissButton = { TextButton(onClick = { duplicate = null }) { Text("Cancel") } }
        )
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
