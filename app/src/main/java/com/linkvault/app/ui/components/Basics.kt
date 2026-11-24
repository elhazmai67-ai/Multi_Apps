package com.linkvault.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.ui.res.painterResource
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import com.linkvault.app.ui.components.NeuButton as Button
import com.linkvault.app.ui.components.NeuIconButton as IconButton
import com.linkvault.app.ui.theme.FolderShape
import com.linkvault.app.ui.theme.Neon
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkvault.app.ui.theme.Radius
import com.linkvault.app.util.Favicons
import com.linkvault.app.util.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Quiet monogram tile. Known platforms get a muted tint; unknown sites use their favicon if cached. */
@Composable
fun PlatformIcon(platform: Platform, domain: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val context = LocalContext.current
    val favicon by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, platform, domain) {
        value = if (platform == Platform.WEB)
            withContext(Dispatchers.IO) { Favicons.load(context, domain)?.asImageBitmap() } else null
    }
    val tint = Color(platform.tint)
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (platform == Platform.WEB) MaterialTheme.colorScheme.surfaceVariant else tint.copy(alpha = 0.18f))
            .semantics { contentDescription = platform.label },
        contentAlignment = Alignment.Center
    ) {
        val fav = favicon
        when {
            fav != null -> Image(fav, null, Modifier.size(size * 0.55f))
            platform == Platform.WEB -> {
                val letter = domain.firstOrNull { it.isLetterOrDigit() }?.uppercase()
                if (letter != null) Text(letter, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else Icon(Icons.Outlined.Language, null, Modifier.size(size * 0.5f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> Text(
                platform.monogram, fontWeight = FontWeight.Bold,
                fontSize = (size.value * (if (platform.monogram.length > 1) 0.34f else 0.44f)).sp,
                color = tint.let { if (MaterialTheme.colorScheme.background.luminanceDark()) it.lighten() else it }
            )
        }
    }
}

private fun Color.luminanceDark() = (red * 0.299f + green * 0.587f + blue * 0.114f) < 0.5f
private fun Color.lighten(): Color = Color(
    red = red + (1 - red) * 0.45f, green = green + (1 - green) * 0.45f, blue = blue + (1 - blue) * 0.45f, alpha = alpha
)

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search links…",
    trailing: @Composable (() -> Unit)? = null,
    onSearch: () -> Unit = {},
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .neuInset(Radius.card, 4.dp)
            .clip(Radius.card)
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            BasicTextField(
                value = value, onValueChange = onValueChange, singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth()
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    .semantics { contentDescription = "Search links" }
            )
        }
        if (value.isNotEmpty()) {
            Box(Modifier.size(40.dp).clip(CircleShape).clickable { onValueChange("") }, contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Close, "Clear search", Modifier.size(18.dp))
            }
        }
        trailing?.invoke()
    }
}

/** Sort/filter button for the search field. The count badge sits fully inside the button so the field's rounded corner never clips it. */
@Composable
fun FilterButton(activeCount: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.padding(end = 4.dp).size(40.dp), contentAlignment = Alignment.Center) {
        IconButton(onClick = onClick, Modifier.fillMaxSize()) {
            Icon(Icons.Outlined.Tune, "Sort and filter")
        }
        if (activeCount > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 3.dp, end = 3.dp)
                    .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$activeCount", color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(Neon.lime))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** Big screen heading used by every top-level and pushed screen, so they all line up. */
@Composable
fun ScreenTitle(
    title: String, modifier: Modifier = Modifier, subtitle: String? = null,
    onBack: (() -> Unit)? = null, trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier.fillMaxWidth().padding(start = if (onBack != null) 4.dp else 20.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}

/** Small lime pill showing a number, e.g. the result count on a list screen. */
@Composable
fun CountPill(count: Int, modifier: Modifier = Modifier) {
    Box(modifier.neuRaised(Radius.pill, 3.dp, Neon.lime).clip(Radius.pill).padding(horizontal = 12.dp, vertical = 5.dp)) {
        Text("$count", style = MaterialTheme.typography.labelLarge, color = Color.White)
    }
}

/** The app mark on a black rounded tile, for headers. */
@Composable
fun AppMark(modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val markShape = RoundedCornerShape(size * 0.32f)
    Box(modifier.size(size).neuRaised(markShape, 5.dp, Neon.ink).clip(markShape), contentAlignment = Alignment.Center) {
        Image(painterResource(com.linkvault.app.R.drawable.ic_launcher_foreground), null, Modifier.requiredSize(size * 1.55f))
    }
}

/** Raised highlight tile: tinted icon badge, big number, small label. */
@Composable
fun StatCard(value: String, label: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val shape = Radius.card
    Box(
        modifier.neuRaised(shape, 6.dp).clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Column(Modifier.padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(color.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(18.dp), tint = color)
            }
            Spacer(Modifier.weight(1f))
            Text(
                value, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false,
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp, lineHeight = 30.sp)
            )
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    NeuChip(label, selected, onClick, modifier)
}

/**
 * Same look as [CategoryChip] but with a fixed height and a centred label. Give it a width (for
 * example weight(1f) in a Row) so a set of them lines up evenly. Long names are shortened with "…".
 */
@Composable
fun UniformChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    NeuChip(label, selected, onClick, modifier, fixedHeight = true)
}

class ChipItem(val label: String, val selected: Boolean, val onClick: () -> Unit)

/**
 * Chips laid out in an even grid, nothing to scroll sideways. Collapsed it shows [collapsedRows] rows with
 * a "More" chip at the end; tapping it shows everything. If the active filter is one of the hidden chips,
 * the grid opens by itself so you can always see what is selected.
 */
@Composable
fun ChipGrid(items: List<ChipItem>, modifier: Modifier = Modifier, columns: Int = 3, collapsedRows: Int = 2) {
    var expanded by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val visibleWhenCollapsed = columns * collapsedRows - 1
    val needsToggle = items.size > columns * collapsedRows
    val forcedOpen = needsToggle && !expanded && items.drop(visibleWhenCollapsed).any { it.selected }
    val cells: List<ChipItem> = when {
        !needsToggle -> items
        expanded -> items + ChipItem("Less", false) { expanded = false }
        forcedOpen -> items
        else -> items.take(visibleWhenCollapsed) + ChipItem("More +${items.size - visibleWhenCollapsed}", false) { expanded = true }
    }
    Column(modifier.fillMaxWidth().animateContentSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        cells.chunked(columns).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowItems.forEach { UniformChip(it.label, it.selected, it.onClick, Modifier.weight(1f)) }
                repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun EmptyState(
    title: String, body: String, modifier: Modifier = Modifier,
    actionLabel: String? = null, onAction: (() -> Unit)? = null
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            Button(onClick = onAction, shape = Radius.small) { Text(actionLabel) }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String, text: String, confirmLabel: String,
    onConfirm: () -> Unit, onDismiss: () -> Unit, destructive: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = Radius.card,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm, colors = if (destructive) ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error) else ButtonDefaults.textButtonColors()) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun TagPill(text: String, modifier: Modifier = Modifier) {
    Text(
        "#$text", modifier = modifier.clip(Radius.small).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 8.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1
    )
}
