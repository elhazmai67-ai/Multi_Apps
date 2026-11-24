package com.linkvault.app.ui.tools

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.linkvault.app.ui.components.NeuBadge
import com.linkvault.app.ui.components.NeuCard
import com.linkvault.app.ui.components.ScreenTitle
import com.linkvault.app.ui.components.SectionHeader
import com.linkvault.app.ui.theme.Neon

private class ToolEntry(val id: String, val title: String, val hint: String, val icon: ImageVector, val color: Color)

private val sections: List<Pair<String, List<ToolEntry>>> = listOf(
    "Video" to listOf(
        ToolEntry("video_logo", "Remove logo", "Blur a logo out of a video", Icons.Outlined.AutoFixHigh, Neon.coral),
        ToolEntry("video_resize", "Resize video", "1080p, 720p, 480p and more", Icons.Outlined.AspectRatio, Neon.lime)
    ),
    "Audio" to listOf(
        ToolEntry("audio_trim", "Trim audio", "Cut the part you want", Icons.Outlined.ContentCut, Neon.green)
    ),
    "Converters" to listOf(
        ToolEntry("video_convert", "Video converter", "mp4, avi, mov, mkv, webm…", Icons.Outlined.Movie, Neon.lime),
        ToolEntry("audio_convert", "Audio converter", "mp3, wav, m4a, flac, ogg…", Icons.Outlined.MusicNote, Neon.green),
        ToolEntry("image_convert", "Image converter", "jpg, png, webp, gif, bmp…", Icons.Outlined.Image, Neon.coral),
        ToolEntry("doc_convert", "Document converter", "PDF, Word, Excel, HTML, images", Icons.Outlined.Description, Neon.teal)
    ),
    "Files" to listOf(
        ToolEntry("compress", "Compress", "Photos, PDFs and documents", Icons.Outlined.Compress, Neon.lime),
        ToolEntry("unlock", "Unlock", "Open a locked bank statement", Icons.Outlined.LockOpen, Neon.green),
        ToolEntry("protect", "Protect", "Put a password on any file", Icons.Outlined.Lock, Neon.coral)
    )
)

@Composable
fun ToolsHubScreen(navigate: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { ScreenTitle("Tools", subtitle = "Everything runs on your phone. Nothing is uploaded.") }
        sections.forEach { (name, entries) ->
            item { SectionHeader(name, Modifier.padding(horizontal = 20.dp)) }
            entries.chunked(2).forEach { row ->
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        row.forEach { t -> ToolTile(t, Modifier.weight(1f)) { navigate("tool/${t.id}") } }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolTile(t: ToolEntry, modifier: Modifier, onClick: () -> Unit) {
    NeuCard(modifier.heightIn(min = 138.dp), onClick = onClick) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NeuBadge(t.color) { Icon(t.icon, null, Modifier.size(22.dp)) }
            Spacer(Modifier.height(2.dp))
            Text(t.title, style = MaterialTheme.typography.titleSmall)
            Text(t.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ToolRouter(id: String, onBack: () -> Unit) {
    when (id) {
        "video_convert" -> VideoConvertScreen(onBack)
        "video_resize" -> VideoResizeScreen(onBack)
        "video_logo" -> RemoveLogoScreen(onBack)
        "audio_trim" -> TrimAudioScreen(onBack)
        "audio_convert" -> AudioConvertScreen(onBack)
        "image_convert" -> ImageConvertScreen(onBack)
        "doc_convert" -> DocConvertScreen(onBack)
        "compress" -> CompressScreen(onBack)
        "unlock" -> UnlockScreen(onBack)
        "protect" -> ProtectScreen(onBack)
        else -> ToolPage("Tool", null, onBack) { Text("This tool isn't available.") }
    }
}

@Suppress("unused") private val keepAlign = Alignment.Center
