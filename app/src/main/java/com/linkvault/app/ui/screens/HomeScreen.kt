package com.linkvault.app.ui.screens

import com.linkvault.app.ui.components.NeuButton as Button
import com.linkvault.app.ui.components.NeuTonalButton as FilledTonalButton
import com.linkvault.app.ui.components.NeuOutlinedButton as OutlinedButton
import com.linkvault.app.ui.components.NeuIconButton as IconButton
import com.linkvault.app.ui.components.NeuSwitch as Switch
import com.linkvault.app.ui.components.NeuCheckbox as Checkbox
import com.linkvault.app.ui.components.neuRaised
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.linkvault.app.Filters
import com.linkvault.app.MainViewModel
import com.linkvault.app.SortOrder
import com.linkvault.app.ui.components.*
import com.linkvault.app.ui.theme.Neon
import com.linkvault.app.ui.theme.Radius
import com.linkvault.app.util.Platform
import com.linkvault.app.util.UrlResult
import com.linkvault.app.util.UrlUtils

@Composable
fun HomeScreen(vm: MainViewModel, navigate: (String) -> Unit, focusSearchSignal: Int) {
    val context = LocalContext.current
    val filters = vm.homeFilters
    val results by remember(filters) { vm.results(filters) }.collectAsState(initial = emptyList())
    val categories by vm.categories.collectAsState()
    val stats by vm.stats.collectAsState()
    val tags by vm.tagCounts.collectAsState()
    val settings by vm.settings.collectAsState()
    val all by vm.allLinks.collectAsState()
    val controller = rememberLinkListController(vm, navigate)
    var showFilter by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(focusSearchSignal) { if (focusSearchSignal > 0) runCatching { focus.requestFocus() } }

    // Clipboard suggestion: read once when Home first appears, never polled.
    var clipUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(settings?.clipboardSuggestions, all.size) {
        if (settings?.clipboardSuggestions == true && clipUrl == null) {
            val candidate = readClipboardUrl(context)
            if (candidate != null && candidate != vm.dismissedClip) {
                val parsed = (UrlUtils.parse(candidate) as? UrlResult.Ok)?.parsed
                if (parsed != null && all.none { it.normalizedUrl == parsed.normalized }) clipUrl = candidate
            }
        }
    }

    val unfiltered = !filters.isActive && filters.sort == SortOrder.RECENT
    // These walk the whole list, so compute them only when the list or filters actually change.
    val showMostUsed = settings?.showMostUsed == true
    val pinned = remember(results, unfiltered) { if (unfiltered) results.filter { it.isPinned } else emptyList() }
    val mostUsed = remember(all, unfiltered, showMostUsed) {
        if (unfiltered && showMostUsed) all.filter { it.openCount > 0 }.sortedByDescending { it.openCount }.take(3) else emptyList()
    }
    val mainList = remember(results, pinned, unfiltered) {
        if (unfiltered) { val ids = pinned.map { it.id }.toSet(); results.filter { it.id !in ids } } else results
    }
    val pinnedCount = remember(all) { all.count { it.isPinned } }

    BackHandlerIfSelecting(controller)

    Column(Modifier.fillMaxSize()) {
        if (controller.selectionMode) SelectionBar(vm, controller, categories, results.map { it.id })
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            if (!controller.selectionMode) {
                item(key = "header") {
                    Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Multi Apps", style = MaterialTheme.typography.headlineMedium)
                            Text("Your links, organized.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        AppMark()
                    }
                }
            }
            item(key = "search") {
                SearchField(
                    value = filters.query, onValueChange = { vm.homeFilters = filters.copy(query = it) },
                    placeholder = "Search links…", focusRequester = focus,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    trailing = { FilterButton(filters.activeCount, onClick = { showFilter = true }) }
                )
            }
            clipUrl?.let { url ->
                item(key = "clip") {
                    ClipboardSuggestion(
                        url = url,
                        onSave = { clipUrl = null; navigate("add?url=" + android.net.Uri.encode(url)) },
                        onDismiss = { vm.dismissedClip = url; clipUrl = null }
                    )
                }
            }
            item(key = "chips") {
                val allSelected = !filters.isActive || (filters.categoryId == null && filters.platform == null && filters.tag == null && !filters.favoritesOnly && !filters.pinnedOnly)
                ChipGrid(
                    items = buildList {
                        add(ChipItem("All", allSelected) {
                            vm.homeFilters = filters.copy(categoryId = null, platform = null, tag = null, favoritesOnly = false, pinnedOnly = false)
                        })
                        add(ChipItem("YouTube", filters.platform == Platform.YOUTUBE) {
                            vm.homeFilters = filters.copy(platform = if (filters.platform == Platform.YOUTUBE) null else Platform.YOUTUBE, categoryId = null)
                        })
                        add(ChipItem("Instagram", filters.platform == Platform.INSTAGRAM) {
                            vm.homeFilters = filters.copy(platform = if (filters.platform == Platform.INSTAGRAM) null else Platform.INSTAGRAM, categoryId = null)
                        })
                        categories.forEach { c ->
                            add(ChipItem(c.name, filters.categoryId == c.id) {
                                vm.homeFilters = filters.copy(categoryId = if (filters.categoryId == c.id) null else c.id, platform = null)
                            })
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            if (!filters.isActive) {
                item(key = "stats") {
                    Column(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard("${stats.total}", "Total links", Icons.Outlined.Link, Neon.lime, Modifier.weight(1f).height(108.dp))
                            StatCard("${stats.categories}", "Categories", Icons.Outlined.Category, Neon.green, Modifier.weight(1f).height(108.dp))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard("${stats.favorites}", "Favorites", Icons.Outlined.StarBorder, Neon.coral, Modifier.weight(1f).height(108.dp))
                            StatCard("$pinnedCount", "Pinned", Icons.Outlined.PushPin, Neon.teal, Modifier.weight(1f).height(108.dp))
                        }
                    }
                }
            }

            if (all.isEmpty()) {
                item(key = "empty") {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        AppMark(Modifier.padding(top = 28.dp), size = 84.dp)
                        EmptyState(
                            title = "No links saved yet.",
                            body = "Save your first important link and keep everything in one place. You can also share a link straight from any app.",
                            actionLabel = "+ Add Link", onAction = { navigate("add") },
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            } else {
                if (pinned.isNotEmpty()) {
                    item(key = "h-pinned") { SectionHeader("Pinned", Modifier.padding(horizontal = 20.dp)) }
                    item(key = "pinned-row") {
                        val palette = listOf(Neon.lime, Neon.green, Neon.coral, Neon.teal)
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            itemsIndexed(pinned, key = { _, l -> "pin${l.id}" }) { i, l ->
                                PinnedFolder(
                                    l, palette[i % palette.size],
                                    onClick = { if (controller.selectionMode) controller.actions.onSelectToggle(l) else controller.actions.onOpen(l) },
                                    onLongClick = { controller.actions.onSelectToggle(l) }
                                )
                            }
                        }
                    }
                }
                if (mostUsed.isNotEmpty()) {
                    item(key = "h-used") { SectionHeader("Most used", Modifier.padding(horizontal = 20.dp)) }
                    linkItems(mostUsed, categories, controller, keyPrefix = "m")
                }
                item(key = "h-main") {
                    SectionHeader(
                        when {
                            filters.isActive -> "${results.size} result${if (results.size == 1) "" else "s"}"
                            filters.sort == SortOrder.RECENT -> "Recently Added"
                            else -> filters.sort.label
                        },
                        Modifier.padding(horizontal = 20.dp)
                    )
                }
                if (mainList.isEmpty() && filters.isActive) {
                    item(key = "no-match") {
                        EmptyState(
                            title = "No matching links", body = "Try a different word, or clear the filters.",
                            actionLabel = "Clear filters", onAction = { vm.homeFilters = Filters() }
                        )
                    }
                } else {
                    linkItems(mainList, categories, controller, keyPrefix = "r")
                }
            }
        }
    }

    LinkListOverlays(vm, controller, categories)
    if (showFilter) FilterSheet(filters, categories, tags, { vm.homeFilters = it }, { showFilter = false })
}

@Composable
private fun ClipboardSuggestion(url: String, onSave: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        Modifier.padding(horizontal = 16.dp).padding(bottom = 14.dp).fillMaxWidth().neuRaised(Radius.card, 5.dp),
        shape = Radius.card, color = MaterialTheme.colorScheme.surface
    ) {
        Row(Modifier.padding(start = 14.dp, top = 4.dp, bottom = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Copied link", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(UrlUtils.shortUrl(url), style = MaterialTheme.typography.bodyMedium, maxLines = 1, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            TextButton(onClick = onSave) { Text("Save") }
            IconButton(onClick = onDismiss, Modifier.size(40.dp)) { Icon(Icons.Outlined.Close, "Dismiss suggestion", Modifier.size(18.dp)) }
        }
    }
}

private fun readClipboardUrl(context: Context): String? = try {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
    UrlUtils.extractUrl(text)
} catch (e: Exception) { null }

fun readClipboardForAdd(context: Context) = readClipboardUrl(context)

@Composable
fun BackHandlerIfSelecting(c: LinkListController) {
    androidx.activity.compose.BackHandler(enabled = c.selectionMode) { c.clearSelection() }
}
