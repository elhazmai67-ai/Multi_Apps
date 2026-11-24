package com.linkvault.app.ui.screens

import com.linkvault.app.ui.components.NeuButton as Button
import com.linkvault.app.ui.components.NeuTonalButton as FilledTonalButton
import com.linkvault.app.ui.components.NeuOutlinedButton as OutlinedButton
import com.linkvault.app.ui.components.NeuIconButton as IconButton
import com.linkvault.app.ui.components.NeuSwitch as Switch
import com.linkvault.app.ui.components.NeuCheckbox as Checkbox
import com.linkvault.app.ui.components.neuRaised
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.linkvault.app.MainViewModel
import com.linkvault.app.data.LinkEntity
import com.linkvault.app.ui.components.*
import com.linkvault.app.ui.theme.Radius
import com.linkvault.app.util.UrlUtils

private data class DupGroup(val keep: LinkEntity, val extras: List<LinkEntity>)

/** Links with the same address. The favorited (then oldest) copy is kept, the rest are extras. */
private fun findDuplicates(links: List<LinkEntity>): List<DupGroup> =
    links.groupBy { it.normalizedUrl }.values.filter { it.size > 1 }.map { g ->
        val sorted = g.sortedWith(compareByDescending<LinkEntity> { it.isFavorite }.thenByDescending { it.isPinned }.thenBy { it.createdAt })
        DupGroup(sorted.first(), sorted.drop(1))
    }

@Composable
fun CleanupScreen(vm: MainViewModel, navigate: (String) -> Unit, onBack: () -> Unit) {
    val all by vm.allLinks.collectAsState()
    val scan = vm.scanState
    val groups = remember(all) { findDuplicates(all) }
    val extraIds = remember(groups) { groups.flatMap { g -> g.extras.map { it.id } } }
    val existing = remember(all) { all.map { it.id }.toSet() }
    val dead = scan.dead.filter { it.link.id in existing }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item { ScreenTitle("Clean up", subtitle = "Find duplicate and dead links", onBack = onBack) }

        // ---- Duplicates ----
        item { SectionHeader("Duplicates", Modifier.padding(horizontal = 20.dp)) }
        if (groups.isEmpty()) {
            item { InfoCard("No duplicates", "Every link in your vault is saved only once.", showTick = true) }
        } else {
            item {
                Card {
                    Text("${extraIds.size} duplicate ${if (extraIds.size == 1) "link" else "links"} in ${groups.size} ${if (groups.size == 1) "group" else "groups"}", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "One copy of each is kept (a favorited one first, otherwise the oldest). You can undo right after deleting.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )
                    Button(onClick = { vm.delete(extraIds) }, shape = Radius.pill, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Text("Delete ${extraIds.size} ${if (extraIds.size == 1) "duplicate" else "duplicates"}")
                    }
                }
            }
            items(groups, key = { "g${it.keep.id}" }) { g ->
                Card {
                    LinkLine(g.keep, "Kept", MaterialTheme.colorScheme.primary, null) { navigate("detail/${g.keep.id}") }
                    g.extras.forEach { e ->
                        LinkLine(e, "Duplicate", MaterialTheme.colorScheme.error, { vm.delete(listOf(e.id)) }) { navigate("detail/${e.id}") }
                    }
                }
            }
        }

        // ---- Dead links ----
        item { SectionHeader("Dead links", Modifier.padding(horizontal = 20.dp)) }
        item {
            when {
                scan.running -> Card {
                    Text("Checking ${scan.done} of ${scan.total}", style = MaterialTheme.typography.titleSmall)
                    LinearProgressIndicator(
                        progress = { if (scan.total == 0) 0f else scan.done / scan.total.toFloat() },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                    )
                    Text("${dead.size} dead so far", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { vm.cancelScan() }, shape = Radius.pill, modifier = Modifier.padding(top = 12.dp)) { Text("Stop") }
                }
                scan.offline -> Card {
                    Text("You're offline", style = MaterialTheme.typography.titleSmall)
                    Text("Connect to the internet and try again.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                    Button(onClick = { vm.startScan() }, shape = Radius.pill) { Text("Try again") }
                }
                scan.finished -> Card {
                    Text(
                        if (dead.isEmpty()) "No dead links found" else "${dead.size} dead ${if (dead.size == 1) "link" else "links"} found",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        "Checked ${scan.done} links." + if (scan.unchecked > 0) " ${scan.unchecked} couldn't be checked (blocked, private or slow), so they were left alone." else "",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )
                    if (dead.isNotEmpty()) {
                        Button(onClick = { vm.delete(dead.map { it.link.id }) }, shape = Radius.pill, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                            Text("Delete all ${dead.size} dead ${if (dead.size == 1) "link" else "links"}")
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    OutlinedButton(onClick = { vm.startScan() }, shape = Radius.pill, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Check again") }
                }
                else -> Card {
                    Text("Check for dead links", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Visits each saved link to find pages that are gone: 404 errors, removed YouTube videos and websites that no longer exist. Needs internet. " +
                            "Instagram, Facebook, X, LinkedIn, WhatsApp and shopping links block checks, so they're skipped.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )
                    Button(onClick = { vm.startScan() }, enabled = all.isNotEmpty(), shape = Radius.pill, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Text("Check ${all.size} links")
                    }
                }
            }
        }
        if (dead.isNotEmpty()) {
            item {
                Card {
                    dead.forEach { d ->
                        LinkLine(d.link, d.reason, MaterialTheme.colorScheme.error, { vm.delete(listOf(d.link.id)) }) { navigate("detail/${d.link.id}") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp).fillMaxWidth().neuRaised(Radius.card, 5.dp), shape = Radius.card, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun InfoCard(title: String, body: String, showTick: Boolean) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showTick) {
                Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
            }
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LinkLine(link: LinkEntity, tag: String, tagColor: androidx.compose.ui.graphics.Color, onDelete: (() -> Unit)?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(link.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(UrlUtils.shortUrl(link.url), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(tag, style = MaterialTheme.typography.labelSmall, color = tagColor)
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.DeleteOutline, "Delete ${link.title}", tint = MaterialTheme.colorScheme.error) }
        }
    }
}
