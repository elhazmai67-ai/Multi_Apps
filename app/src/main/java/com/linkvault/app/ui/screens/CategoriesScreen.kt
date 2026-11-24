package com.linkvault.app.ui.screens

import com.linkvault.app.ui.components.NeuButton as Button
import com.linkvault.app.ui.components.NeuTonalButton as FilledTonalButton
import com.linkvault.app.ui.components.NeuOutlinedButton as OutlinedButton
import com.linkvault.app.ui.components.NeuIconButton as IconButton
import com.linkvault.app.ui.components.NeuSwitch as Switch
import com.linkvault.app.ui.components.NeuCheckbox as Checkbox
import com.linkvault.app.ui.components.neuRaised
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.linkvault.app.MainViewModel
import com.linkvault.app.UNCATEGORIZED
import com.linkvault.app.data.CategoryEntity
import com.linkvault.app.ui.components.*
import com.linkvault.app.ui.theme.Neon
import com.linkvault.app.ui.theme.Radius
import kotlinx.coroutines.launch

@Composable
fun CategoriesScreen(vm: MainViewModel, navigate: (String) -> Unit) {
    val categories by vm.categories.collectAsState()
    val links by vm.allLinks.collectAsState()
    val counts = remember(links) { links.groupingBy { it.categoryId }.eachCount() }
    val uncategorized = counts[null] ?: 0

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        item {
            ScreenTitle(
                "Categories", subtitle = "Tap a folder to open it",
                trailing = { TextButton(onClick = { navigate("manage_categories") }) { Text("Manage") } }
            )
        }
        if (categories.isEmpty() && uncategorized == 0) {
            item {
                EmptyState("No categories", "Create a category to group your links.", actionLabel = "Add category", onAction = { navigate("manage_categories") })
            }
        }
        val tiles = buildList {
            categories.forEach { c -> add(Triple(c.name, CategoryIcons.get(c.iconKey), counts[c.id] ?: 0) to "category/${c.id}") }
            if (uncategorized > 0) add(Triple("Uncategorized", Icons.Outlined.FolderOff, uncategorized) to "category/$UNCATEGORIZED")
        }
        val palette = listOf(Neon.lime, Neon.green, Neon.coral, Neon.teal)
        itemsIndexed(tiles.chunked(2)) { rowIndex, pair ->
            Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEachIndexed { col, (info, route) ->
                    StatCard(
                        "${info.third}", info.first, info.second, palette[(rowIndex * 2 + col) % palette.size],
                        Modifier.weight(1f).height(116.dp), onClick = { navigate(route) }
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CategoryRow(icon: androidx.compose.ui.graphics.vector.ImageVector, name: String, count: Int, onClick: () -> Unit) {
    Surface(
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().neuRaised(Radius.card, 5.dp).clip(Radius.card).clickable(onClick = onClick),
        shape = Radius.card, color = MaterialTheme.colorScheme.surface
    ) {
        Row(Modifier.heightIn(min = 56.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(Radius.small).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(14.dp))
            Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$count", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageCategoriesScreen(vm: MainViewModel, onBack: () -> Unit) {
    val categories by vm.categories.collectAsState()
    val links by vm.allLinks.collectAsState()
    val counts = remember(links) { links.groupingBy { it.categoryId }.eachCount() }
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<CategoryEntity?>(null) }

    fun move(index: Int, delta: Int) {
        val list = categories.toMutableList()
        val target = index + delta
        if (target !in list.indices) return
        list.add(target, list.removeAt(index))
        scope.launch { vm.repo.reorderCategories(list) }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            Text("Manage categories", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            FilledTonalButton(onClick = { creating = true }, shape = Radius.small) {
                Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Add")
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)) {
            itemsIndexed(categories, key = { _, c -> c.id }) { index, c ->
                Surface(
                    Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().neuRaised(Radius.card, 5.dp),
                    shape = Radius.card, color = MaterialTheme.colorScheme.surface
                ) {
                    Row(Modifier.padding(start = 12.dp, end = 2.dp).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(CategoryIcons.get(c.iconKey), null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${counts[c.id] ?: 0} links", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { move(index, -1) }, enabled = index > 0, modifier = Modifier.size(40.dp)) { Icon(Icons.Outlined.KeyboardArrowUp, "Move ${c.name} up") }
                        IconButton(onClick = { move(index, 1) }, enabled = index < categories.lastIndex, modifier = Modifier.size(40.dp)) { Icon(Icons.Outlined.KeyboardArrowDown, "Move ${c.name} down") }
                        IconButton(onClick = { editing = c }, Modifier.size(40.dp)) { Icon(Icons.Outlined.Edit, "Edit ${c.name}", Modifier.size(20.dp)) }
                        IconButton(onClick = { deleting = c }, Modifier.size(40.dp)) { Icon(Icons.Outlined.DeleteOutline, "Delete ${c.name}", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }

    if (creating) CategoryDialog(
        title = "New category", initial = null, existing = categories, confirmLabel = "Add",
        onConfirm = { name, icon -> scope.launch { vm.repo.addCategory(name, icon) }; creating = false },
        onDismiss = { creating = false }
    )
    editing?.let { c ->
        CategoryDialog(
            title = "Edit category", initial = c, existing = categories, confirmLabel = "Save",
            onConfirm = { name, icon -> scope.launch { vm.repo.updateCategory(c.copy(name = name, iconKey = icon)) }; editing = null },
            onDismiss = { editing = null }
        )
    }
    deleting?.let { c ->
        val n = counts[c.id] ?: 0
        ConfirmDialog(
            title = "Delete \"${c.name}\"?",
            text = if (n > 0) "$n link${if (n == 1) "" else "s"} in this category will be kept and moved to Uncategorized." else "This category is empty.",
            confirmLabel = "Delete", destructive = true,
            onConfirm = { scope.launch { vm.repo.deleteCategory(c.id) }; deleting = null; vm.toast("Category deleted") },
            onDismiss = { deleting = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryDialog(
    title: String, initial: CategoryEntity?, existing: List<CategoryEntity>, confirmLabel: String,
    onConfirm: (String, String) -> Unit, onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var icon by remember { mutableStateOf(initial?.iconKey ?: "folder") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss, shape = Radius.card, title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(30); error = null }, label = { Text("Name") },
                    singleLine = true, isError = error != null, shape = Radius.small,
                    supportingText = { error?.let { Text(it) } }, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text("Icon", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryIcons.all.forEach { (key, vec) ->
                        val sel = key == icon
                        Box(
                            Modifier.size(44.dp).clip(Radius.small)
                                .background(if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { icon = key },
                            contentAlignment = Alignment.Center
                        ) { Icon(vec, key, tint = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val n = name.trim()
                when {
                    n.isEmpty() -> error = "Please enter a name."
                    existing.any { it.id != initial?.id && it.name.equals(n, true) } -> error = "A category with this name already exists."
                    else -> onConfirm(n, icon)
                }
            }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
