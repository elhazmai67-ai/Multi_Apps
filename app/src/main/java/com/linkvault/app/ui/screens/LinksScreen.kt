package com.linkvault.app.ui.screens

import com.linkvault.app.ui.components.NeuButton as Button
import com.linkvault.app.ui.components.NeuTonalButton as FilledTonalButton
import com.linkvault.app.ui.components.NeuOutlinedButton as OutlinedButton
import com.linkvault.app.ui.components.NeuIconButton as IconButton
import com.linkvault.app.ui.components.NeuSwitch as Switch
import com.linkvault.app.ui.components.NeuCheckbox as Checkbox
import com.linkvault.app.ui.components.neuRaised
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.linkvault.app.Filters
import com.linkvault.app.MainViewModel
import com.linkvault.app.ui.components.*

/** A titled, searchable, filterable list. Used for Favorites and for a single category. */
@Composable
fun LinksScreen(
    vm: MainViewModel,
    title: String,
    filters: Filters,
    onFilters: (Filters) -> Unit,
    navigate: (String) -> Unit,
    emptyTitle: String,
    emptyBody: String,
    onBack: (() -> Unit)? = null,
    lockCategory: Boolean = false
) {
    val results by remember(filters) { vm.results(filters) }.collectAsState(initial = emptyList())
    val categories by vm.categories.collectAsState()
    val tags by vm.tagCounts.collectAsState()
    val controller = rememberLinkListController(vm, navigate)
    var showFilter by remember { mutableStateOf(false) }
    BackHandlerIfSelecting(controller)

    Column(Modifier.fillMaxSize()) {
        if (controller.selectionMode) {
            SelectionBar(vm, controller, categories, results.map { it.id })
        } else {
            ScreenTitle(title, onBack = onBack, trailing = { CountPill(results.size) })
        }
        SearchField(
            value = filters.query, onValueChange = { onFilters(filters.copy(query = it)) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            trailing = {
                FilterButton(filters.activeCount, onClick = { showFilter = true })
            }
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            if (results.isEmpty()) {
                item {
                    if (filters.query.isNotBlank() || filters.platform != null || filters.tag != null || filters.pinnedOnly || (filters.categoryId != null && !lockCategory)) {
                        EmptyState("No matching links", "Try a different word, or clear the filters.")
                    } else EmptyState(emptyTitle, emptyBody)
                }
            } else linkItems(results, categories, controller)
        }
    }
    LinkListOverlays(vm, controller, categories)
    if (showFilter) FilterSheet(filters, categories, tags, onFilters, { showFilter = false }, lockCategory = lockCategory)
}
