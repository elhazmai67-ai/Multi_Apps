package com.linkvault.app.ui.components

import com.linkvault.app.ui.components.NeuButton as Button
import com.linkvault.app.ui.components.NeuTonalButton as FilledTonalButton
import com.linkvault.app.ui.components.NeuOutlinedButton as OutlinedButton
import com.linkvault.app.ui.components.NeuIconButton as IconButton
import com.linkvault.app.ui.components.NeuSwitch as Switch
import com.linkvault.app.ui.components.NeuCheckbox as Checkbox
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.linkvault.app.data.LinkEntity
import com.linkvault.app.ui.theme.Radius
import com.linkvault.app.util.Format
import com.linkvault.app.util.Platform
import com.linkvault.app.util.UrlUtils

data class LinkActions(
    val onOpen: (LinkEntity) -> Unit,
    val onDetails: (LinkEntity) -> Unit,
    val onEdit: (LinkEntity) -> Unit,
    val onCopy: (LinkEntity) -> Unit,
    val onShare: (LinkEntity) -> Unit,
    val onToggleFavorite: (LinkEntity) -> Unit,
    val onTogglePinned: (LinkEntity) -> Unit,
    val onDelete: (LinkEntity) -> Unit,
    val onSelectToggle: (LinkEntity) -> Unit
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LinkCard(
    link: LinkEntity,
    categoryName: String?,
    selectionMode: Boolean,
    selected: Boolean,
    actions: LinkActions,
    modifier: Modifier = Modifier
) {
    var menu by remember { mutableStateOf(false) }
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface,
        label = "card-bg"
    )
    Surface(
        modifier = modifier.fillMaxWidth().neuRaised(Radius.card, 5.dp, container),
        shape = Radius.card, color = container,
        border = if (selected) BorderStroke(1.5.dp, border) else null
    ) {
        Row(
            Modifier
                .combinedClickable(
                    onClick = { if (selectionMode) actions.onSelectToggle(link) else actions.onOpen(link) },
                    onLongClick = { actions.onSelectToggle(link) }
                )
                .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { actions.onSelectToggle(link) }, modifier = Modifier.padding(end = 4.dp))
            }
            LinkLeading(link)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(link.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (link.isPinned) {
                        Icon(Icons.Outlined.PushPin, "Pinned", Modifier.padding(start = 6.dp).size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Text(
                    UrlUtils.shortUrl(link.url), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOfNotNull(categoryName, Format.relative(link.createdAt)).joinToString("  ·  "),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (!selectionMode) {
                FavoriteButton(link.isFavorite, onToggle = { actions.onToggleFavorite(link) })
                Box {
                    IconButton(onClick = { menu = true }, Modifier.size(40.dp)) {
                        Icon(Icons.Outlined.MoreVert, "More options for ${link.title}", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = Radius.small) {
                        @Composable
                        fun item(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, destructive: Boolean = false, onClick: () -> Unit) =
                            DropdownMenuItem(
                                text = { Text(label, color = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified) },
                                leadingIcon = { Icon(icon, null, Modifier.size(20.dp), tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) },
                                onClick = { menu = false; onClick() }
                            )
                        item("Open", Icons.Outlined.OpenInNew) { actions.onOpen(link) }
                        item("Details", Icons.Outlined.Info) { actions.onDetails(link) }
                        item("Edit", Icons.Outlined.Edit) { actions.onEdit(link) }
                        item("Copy URL", Icons.Outlined.ContentCopy) { actions.onCopy(link) }
                        item("Share", Icons.Outlined.Share) { actions.onShare(link) }
                        item(if (link.isPinned) "Unpin" else "Pin to home", Icons.Outlined.PushPin) { actions.onTogglePinned(link) }
                        item("Delete", Icons.Outlined.DeleteOutline, destructive = true) { actions.onDelete(link) }
                    }
                }
            }
        }
    }
}

@Composable
fun FavoriteButton(isFavorite: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val scale by animateFloatAsState(if (isFavorite) 1f else 0.92f, spring(dampingRatio = 0.45f, stiffness = 600f), label = "fav-scale")
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    IconButton(onClick = {
        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
        onToggle()
    }, modifier.size(40.dp).semantics { contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites" }) {
        Icon(
            if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder, null,
            Modifier.size(22.dp).scale(scale),
            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Swipe right = favorite, swipe left = edit/delete actions. Cards always snap back. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableLinkCard(
    link: LinkEntity,
    categoryName: String?,
    selectionMode: Boolean,
    selected: Boolean,
    actions: LinkActions,
    onSwipeLeft: (LinkEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val latest by rememberUpdatedState(link)
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> actions.onToggleFavorite(latest)
                SwipeToDismissBoxValue.EndToStart -> onSwipeLeft(latest)
                else -> {}
            }
            false
        }
    )
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = !selectionMode,
        enableDismissFromEndToStart = !selectionMode,
        backgroundContent = {
            val dir = state.dismissDirection
            val fav = dir == SwipeToDismissBoxValue.StartToEnd
            val active = dir != SwipeToDismissBoxValue.Settled
            Box(
                Modifier.fillMaxSize()
                    .background(
                        when {
                            !active -> Color.Transparent
                            fav -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surfaceContainerHighest
                        }, Radius.card
                    )
                    .padding(horizontal = 20.dp),
                contentAlignment = if (fav) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                if (active) Icon(
                    if (fav) (if (link.isFavorite) Icons.Outlined.StarBorder else Icons.Filled.Star) else Icons.Outlined.MoreHoriz,
                    null, tint = MaterialTheme.colorScheme.onPrimaryContainer.takeIf { fav } ?: MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    ) {
        LinkCard(link, categoryName, selectionMode, selected, actions)
    }
}
