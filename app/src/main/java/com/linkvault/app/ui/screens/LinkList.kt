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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.linkvault.app.MainViewModel
import com.linkvault.app.data.CategoryEntity
import com.linkvault.app.data.LinkEntity
import com.linkvault.app.ui.components.*
import com.linkvault.app.ui.theme.Radius
import com.linkvault.app.util.LinkOpener
import kotlinx.coroutines.launch

/**
 * Selection + per-link actions shared by Home, Favorites and Category screens, so they all
 * behave identically.
 */
@Stable
class LinkListController(
    private val vm: MainViewModel,
    private val context: Context,
    private val navigate: (String) -> Unit
) {
    val selected = mutableStateListOf<Long>()
    val selectionMode get() = selected.isNotEmpty()
    var pendingDelete by mutableStateOf<List<Long>?>(null)
    var swipeTarget by mutableStateOf<LinkEntity?>(null)

    fun toggleSelect(id: Long) { if (id in selected) selected.remove(id) else selected.add(id) }
    fun clearSelection() = selected.clear()

    val actions = LinkActions(
        onOpen = { vm.open(it, context) },
        onDetails = { navigate("detail/${it.id}") },
        onEdit = { navigate("edit/${it.id}") },
        onCopy = {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Link", it.url))
            vm.toast("Link copied")
        },
        onShare = { LinkOpener.share(context, it.url, it.title) },
        onToggleFavorite = { vm.toggleFavorite(it) },
        onTogglePinned = { vm.togglePinned(it); vm.toast(if (it.isPinned) "Unpinned" else "Pinned to home") },
        onDelete = { pendingDelete = listOf(it.id) },
        onSelectToggle = { toggleSelect(it.id) }
    )
}

@Composable
fun rememberLinkListController(vm: MainViewModel, navigate: (String) -> Unit): LinkListController {
    val context = LocalContext.current
    return remember(vm, context) { LinkListController(vm, context, navigate) }
}

fun LazyListScope.linkItems(
    links: List<LinkEntity>,
    categories: List<CategoryEntity>,
    c: LinkListController,
    keyPrefix: String = ""
) {
    val names = categories.associate { it.id to it.name }
    items(links, key = { "$keyPrefix${it.id}" }, contentType = { "link" }) { link ->
        SwipeableLinkCard(
            link = link,
            categoryName = link.categoryId?.let { names[it] },
            selectionMode = c.selectionMode,
            selected = link.id in c.selected,
            actions = c.actions,
            onSwipeLeft = { c.swipeTarget = it },
            modifier = Modifier.animateItem().padding(horizontal = 16.dp, vertical = 4.dp)
        )
    }
}

/** Dialogs + sheets that go with a [LinkListController]. Place once per screen. */
@Composable
fun LinkListOverlays(vm: MainViewModel, c: LinkListController, categories: List<CategoryEntity>) {
    val pending = c.pendingDelete
    if (pending != null) {
        ConfirmDialog(
            title = if (pending.size == 1) "Delete this link?" else "Delete ${pending.size} links?",
            text = if (pending.size == 1) "It will be removed from LinkVault. You can undo right after." else "These links will be removed from LinkVault. You can undo right after.",
            confirmLabel = "Delete", destructive = true,
            onConfirm = { vm.delete(pending); c.selected.removeAll(pending.toSet()); c.pendingDelete = null },
            onDismiss = { c.pendingDelete = null }
        )
    }
    c.swipeTarget?.let { l ->
        LinkActionSheet(
            link = l,
            onEdit = { c.swipeTarget = null; c.actions.onEdit(l) },
            onDelete = { c.swipeTarget = null; c.pendingDelete = listOf(l.id) },
            onDismiss = { c.swipeTarget = null }
        )
    }
}

/** Contextual top bar replacement while links are selected. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionBar(
    vm: MainViewModel,
    c: LinkListController,
    categories: List<CategoryEntity>,
    visibleIds: List<Long>
) {
    val scope = rememberCoroutineScope()
    var moveSheet by remember { mutableStateOf(false) }
    var addTag by remember { mutableStateOf(false) }
    var removeTag by remember { mutableStateOf(false) }
    var confirmBulkDelete by remember { mutableStateOf(false) }
    val tagCounts by vm.tagCounts.collectAsState()
    val ids = c.selected.toList()

    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 0.dp) {
        Column {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { c.clearSelection() }) { Icon(Icons.Outlined.Close, "Cancel selection") }
                Text("${ids.size} selected", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    if (ids.size == visibleIds.size) c.clearSelection() else { c.selected.clear(); c.selected.addAll(visibleIds) }
                }) { Text(if (ids.size == visibleIds.size) "None" else "All") }
            }
            androidx.compose.foundation.lazy.LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 10.dp)
            ) {
                val chips = listOf<Pair<String, () -> Unit>>(
                    "Favorite" to { scope.launch { vm.repo.setFavorite(ids, true) }; vm.toast("Added to favorites") },
                    "Unfavorite" to { scope.launch { vm.repo.setFavorite(ids, false) }; vm.toast("Removed from favorites") },
                    "Pin" to { scope.launch { vm.repo.setPinned(ids, true) }; vm.toast("Pinned") },
                    "Unpin" to { scope.launch { vm.repo.setPinned(ids, false) }; vm.toast("Unpinned") },
                    "Move" to { moveSheet = true },
                    "Add tag" to { addTag = true },
                    "Remove tag" to { removeTag = true },
                    "Delete" to { confirmBulkDelete = true }
                )
                items(chips.size) { i ->
                    val (label, action) = chips[i]
                    AssistChip(
                        onClick = action, label = { Text(label) }, shape = Radius.small,
                        colors = if (label == "Delete") AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.error) else AssistChipDefaults.assistChipColors()
                    )
                }
            }
        }
    }

    if (moveSheet) CategoryPickerSheet(categories, onPick = { cat ->
        scope.launch { vm.repo.moveToCategory(ids, cat) }; moveSheet = false; vm.toast("Moved ${ids.size} link${if (ids.size == 1) "" else "s"}")
    }, onDismiss = { moveSheet = false })
    if (addTag) TextInputDialog(
        title = "Add tag", label = "Tag name", confirmLabel = "Add", suggestions = tagCounts.map { it.name },
        validate = { if (it.isBlank()) "Enter a tag name" else null },
        onConfirm = { t -> scope.launch { vm.repo.addTag(ids, t) }; addTag = false; vm.toast("Tag added") },
        onDismiss = { addTag = false }
    )
    if (removeTag) {
        val onSelected = remember(ids, vm.allLinks.value) {
            vm.allLinks.value.filter { it.id in ids }.flatMap { it.tagList }.distinctBy { it.lowercase() }
        }
        TextInputDialog(
            title = "Remove tag", label = "Tag name", confirmLabel = "Remove", suggestions = onSelected,
            validate = { if (it.isBlank()) "Enter a tag name" else null },
            onConfirm = { t -> scope.launch { vm.repo.removeTag(ids, t) }; removeTag = false; vm.toast("Tag removed") },
            onDismiss = { removeTag = false }
        )
    }
    if (confirmBulkDelete) ConfirmDialog(
        title = "Delete ${ids.size} links?",
        text = "This removes ${ids.size} link${if (ids.size == 1) "" else "s"} from LinkVault. You can undo right after.",
        confirmLabel = "Delete ${ids.size}", destructive = true,
        onConfirm = { vm.delete(ids); c.clearSelection(); confirmBulkDelete = false },
        onDismiss = { confirmBulkDelete = false }
    )
}
