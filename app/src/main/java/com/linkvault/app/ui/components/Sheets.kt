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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.linkvault.app.Filters
import com.linkvault.app.SortOrder
import com.linkvault.app.data.CategoryEntity
import com.linkvault.app.data.LinkEntity
import com.linkvault.app.data.TagCount
import com.linkvault.app.ui.theme.Radius
import com.linkvault.app.util.Platform

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(
    filters: Filters,
    categories: List<CategoryEntity>,
    tags: List<TagCount>,
    onChange: (Filters) -> Unit,
    onDismiss: () -> Unit,
    lockCategory: Boolean = false
) {
    ModalBottomSheet(onDismissRequest = onDismiss, shape = Radius.card) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sort & filter", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { onChange(filters.copy(categoryId = if (lockCategory) filters.categoryId else null, platform = null, tag = null, favoritesOnly = false, pinnedOnly = false, sort = SortOrder.RECENT)) }) { Text("Reset") }
            }
            Label("Sort by")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SortOrder.entries.forEach { s -> CategoryChip(s.label, filters.sort == s, { onChange(filters.copy(sort = s)) }) }
            }
            Label("Show only")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CategoryChip("Favorites", filters.favoritesOnly, { onChange(filters.copy(favoritesOnly = !filters.favoritesOnly)) })
                CategoryChip("Pinned", filters.pinnedOnly, { onChange(filters.copy(pinnedOnly = !filters.pinnedOnly)) })
            }
            Label("Platform")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Platform.entries.forEach { p ->
                    CategoryChip(p.label, filters.platform == p, { onChange(filters.copy(platform = if (filters.platform == p) null else p)) })
                }
            }
            if (!lockCategory && categories.isNotEmpty()) {
                Label("Category")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    categories.forEach { c ->
                        CategoryChip(c.name, filters.categoryId == c.id, { onChange(filters.copy(categoryId = if (filters.categoryId == c.id) null else c.id)) })
                    }
                }
            }
            if (tags.isNotEmpty()) {
                Label("Tag")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    tags.take(30).forEach { t ->
                        CategoryChip("#${t.name}", filters.tag.equals(t.name, true), { onChange(filters.copy(tag = if (filters.tag.equals(t.name, true)) null else t.name)) })
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(48.dp), shape = Radius.pill) { Text("Done") }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
}

/** Shown after a swipe-left on a card. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkActionSheet(link: LinkEntity, onEdit: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, shape = Radius.card) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(link.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            SheetRow(Icons.Outlined.Edit, "Edit link", onClick = onEdit)
            SheetRow(Icons.Outlined.DeleteOutline, "Delete link", destructive = true, onClick = onDelete)
        }
    }
}

@Composable
fun SheetRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, destructive: Boolean = false, onClick: () -> Unit) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (destructive) color else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, color = color, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPickerSheet(categories: List<CategoryEntity>, onPick: (Long?) -> Unit, onDismiss: () -> Unit, title: String = "Move to category") {
    ModalBottomSheet(onDismissRequest = onDismiss, shape = Radius.card) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            categories.forEach { c -> SheetRow(CategoryIcons.get(c.iconKey), c.name) { onPick(c.id) } }
            SheetRow(Icons.Outlined.FolderOff, "No category") { onPick(null) }
        }
    }
}

/** Single-field dialog used for adding/removing a tag in bulk, creating and renaming. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TextInputDialog(
    title: String, label: String, confirmLabel: String, initial: String = "",
    suggestions: List<String> = emptyList(),
    validate: (String) -> String? = { null },
    onConfirm: (String) -> Unit, onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss, shape = Radius.card, title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text, onValueChange = { text = it; error = null }, label = { Text(label) },
                    singleLine = true, isError = error != null, shape = Radius.small,
                    supportingText = { error?.let { Text(it) } }, modifier = Modifier.fillMaxWidth()
                )
                if (suggestions.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        suggestions.take(12).forEach { s -> CategoryChip("#$s", text.equals(s, true), { text = s; error = null }) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val e = validate(text.trim())
                if (e != null) error = e else onConfirm(text.trim())
            }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
