package com.linkvault.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkvault.app.data.LinkEntity
import com.linkvault.app.ui.theme.FolderShape
import com.linkvault.app.ui.theme.Neon
import com.linkvault.app.util.Platform

/** A pinned link shown as a small coloured folder. Tap opens the link; long-press selects it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PinnedFolder(
    link: LinkEntity,
    color: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val platform = Platform.fromKey(link.platform)
    val mark = if (platform == Platform.WEB) link.domain.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "•" else platform.monogram
    Box(
        modifier
            .width(168.dp).height(120.dp)
            .neuRaised(FolderShape(), 5.dp, color)
            .clip(FolderShape())
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics { contentDescription = "Pinned link ${link.title}" }
    ) {
        Column(Modifier.padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(Neon.ink), contentAlignment = Alignment.Center) {
                    Text(mark, color = Color.White, fontWeight = FontWeight.Bold, fontSize = if (mark.length > 1) 11.sp else 14.sp, maxLines = 1)
                }
            }
            Spacer(Modifier.weight(1f))
            Text(link.title, style = MaterialTheme.typography.titleSmall, color = Neon.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(link.domain, style = MaterialTheme.typography.labelSmall, color = Neon.ink.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(
            Icons.Outlined.PushPin, null, Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 12.dp).size(16.dp),
            tint = Neon.ink.copy(alpha = 0.55f)
        )
    }
}
