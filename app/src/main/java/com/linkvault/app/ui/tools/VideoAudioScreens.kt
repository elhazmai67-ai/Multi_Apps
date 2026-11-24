package com.linkvault.app.ui.tools

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.linkvault.app.tools.Ffmpeg
import com.linkvault.app.tools.MediaTools
import com.linkvault.app.tools.ToolRunner
import com.linkvault.app.tools.VideoInfo
import com.linkvault.app.ui.components.*
import com.linkvault.app.ui.components.NeuButton as Button
import com.linkvault.app.ui.components.NeuTonalButton as FilledTonalButton
import com.linkvault.app.ui.components.NeuOutlinedButton as OutlinedButton
import com.linkvault.app.ui.components.NeuCheckbox as Checkbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val VIDEO_MIME = arrayOf("video/*")
private val AUDIO_MIME = arrayOf("audio/*", "video/*")

@Composable
fun VideoConvertScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val id = "video_convert"
    val pick = rememberPicker(VIDEO_MIME, true)
    var format by remember { mutableIntStateOf(0) }
    var res by remember { mutableIntStateOf(0) } // 0 = same as source
    var quality by remember { mutableIntStateOf(1) }

    ToolPage("Video converter", "Change the format or size of a video", onBack) {
        FilePickerCard("Choose videos", pick)
        OptionLabel("Convert to")
        ChipRows(MediaTools.videoFormats.mapIndexed { i, f -> f to (i == format) }, 4) { format = it }
        OptionLabel("Resolution")
        val labels = listOf("Same as source") + MediaTools.presets.map { it.label }
        ChipRows(labels.mapIndexed { i, l -> l to (i == res) }, 3) { res = it }
        if (res > 0) Text("${MediaTools.presets[res - 1].detail}. The picture keeps its shape.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OptionLabel("Quality")
        ChipRows(MediaTools.videoQualities.mapIndexed { i, l -> l to (i == quality) }, 3) { quality = it }
        RunButton("Convert", pick.files.isNotEmpty(), id) {
            val files = pick.files.toList()
            val ext = MediaTools.videoFormats[format]
            val preset = if (res > 0) MediaTools.presets[res - 1] else null
            val q = quality
            ToolRunner.start(id, context) { tc ->
                files.mapIndexed { i, f -> MediaTools.convertVideo(tc, f, ext, preset, q, i, files.size) }
            }
        }
        ToolResultPanel(id)
    }
}

@Composable
fun VideoResizeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val id = "video_resize"
    val pick = rememberPicker(VIDEO_MIME, true)
    var sel by remember { mutableIntStateOf(1) } // index in presets, presets.size = custom
    var customW by remember { mutableStateOf("") }
    var customH by remember { mutableStateOf("") }
    var keep by remember { mutableStateOf(true) }
    val custom = sel == MediaTools.presets.size

    ToolPage("Resize video", "Make a video smaller or change its resolution", onBack) {
        FilePickerCard("Choose videos", pick)
        OptionLabel("New size")
        val labels = MediaTools.presets.map { it.label } + "Custom"
        ChipRows(labels.mapIndexed { i, l -> l to (i == sel) }, 3) { sel = it }
        if (!custom) Text(MediaTools.presets[sel].detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (custom) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { NeuTextField(customW, { customW = it.filter(Char::isDigit).take(4) }, "Width", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) }
                Box(Modifier.weight(1f)) { NeuTextField(customH, { customH = it.filter(Char::isDigit).take(4) }, "Height", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) }
            }
        }
        Row(Modifier.fillMaxWidth().clickable { keep = !keep }, verticalAlignment = Alignment.CenterVertically) {
            Checkbox(keep, null)
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Keep the picture's shape", style = MaterialTheme.typography.bodyLarge)
                Text("Turn off to stretch the video to exactly this size.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val cw = customW.toIntOrNull() ?: 0
        val ch = customH.toIntOrNull() ?: 0
        RunButton("Resize", pick.files.isNotEmpty() && (!custom || (cw >= 64 && ch >= 64)), id) {
            val files = pick.files.toList()
            val w = if (custom) cw else MediaTools.presets[sel].width
            val h = if (custom) ch else MediaTools.presets[sel].height
            val k = keep
            ToolRunner.start(id, context) { tc ->
                files.mapIndexed { i, f -> MediaTools.resizeVideo(tc, f, w, h, k, i, files.size) }
            }
        }
        ToolResultPanel(id)
    }
}

@Composable
fun RemoveLogoScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val id = "video_logo"
    val pick = rememberPicker(VIDEO_MIME, false)
    val file = pick.files.firstOrNull()
    var info by remember { mutableStateOf<VideoInfo?>(null) }
    var frame by remember { mutableStateOf<Bitmap?>(null) }
    var frameSec by remember { mutableFloatStateOf(1f) }
    var loadSec by remember { mutableFloatStateOf(1f) }
    var sel by remember { mutableStateOf(Rect(0.70f, 0.04f, 0.97f, 0.14f)) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(file?.uri) {
        info = null; frame = null; error = null; frameSec = 1f; loadSec = 1f
        if (file != null) {
            try { info = withContext(Dispatchers.IO) { Ffmpeg.videoInfo(context, file.uri) } }
            catch (e: Exception) { error = e.message }
        }
    }
    LaunchedEffect(file?.uri, loadSec) {
        if (file != null && error == null) {
            frame = withContext(Dispatchers.IO) {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(context, file.uri)
                    r.getFrameAtTime((loadSec * 1_000_000L).toLong(), MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } catch (e: Exception) { null } finally { runCatching { r.release() } }
            }
        }
    }

    ToolPage("Remove logo", "Hide a logo or watermark in a video", onBack) {
        FilePickerCard("Choose a video", pick)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        val bmp = frame
        val vi = info
        if (bmp != null && vi != null) {
            Text("Drag on the picture to draw a box over the logo.", style = MaterialTheme.typography.bodyMedium)
            LogoSelector(bmp, sel) { sel = it }
            if (vi.durationSec > 2) {
                Text("Pick a moment where the logo is visible: ${formatTime(frameSec.toDouble())}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = frameSec, onValueChange = { frameSec = it }, onValueChangeFinished = { loadSec = frameSec },
                    valueRange = 0f..(vi.durationSec.toFloat() - 0.5f)
                )
            }
            RunButton("Remove logo", true, id) {
                val f = file!!
                val r = sel
                val x = (r.left * vi.width).toInt()
                val y = (r.top * vi.height).toInt()
                val w = (r.width * vi.width).toInt()
                val h = (r.height * vi.height).toInt()
                ToolRunner.start(id, context) { tc -> listOf(MediaTools.removeLogo(tc, f, x, y, w, h)) }
            }
            Text("The box is blurred into its surroundings, so a small box works best. Long videos take a while.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ToolResultPanel(id)
    }
}

@Composable
private fun LogoSelector(bitmap: Bitmap, sel: Rect, onChange: (Rect) -> Unit) {
    val aspect = bitmap.width.toFloat() / bitmap.height.toFloat()
    var size by remember { mutableStateOf(IntSize.Zero) }
    var anchor by remember { mutableStateOf(Offset.Zero) }
    val accent = MaterialTheme.colorScheme.primary
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val maxH = 400.dp
        val w = if (maxWidth / aspect > maxH) maxH * aspect else maxWidth
        Box(
            Modifier.width(w).aspectRatio(aspect).clip(RoundedCornerShape(12.dp))
                .onSizeChanged { size = it }
                .pointerInput(bitmap) {
                    detectDragGestures(
                        onDragStart = { o -> anchor = o; onChange(Rect(o.x / size.width, o.y / size.height, o.x / size.width, o.y / size.height)) },
                        onDrag = { change, _ ->
                            if (size.width > 0 && size.height > 0) {
                                val px = change.position.x.coerceIn(0f, size.width.toFloat())
                                val py = change.position.y.coerceIn(0f, size.height.toFloat())
                                onChange(
                                    Rect(
                                        minOf(anchor.x, px) / size.width, minOf(anchor.y, py) / size.height,
                                        maxOf(anchor.x, px) / size.width, maxOf(anchor.y, py) / size.height
                                    )
                                )
                            }
                        }
                    )
                }
        ) {
            Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            Canvas(Modifier.fillMaxSize()) {
                val tl = Offset(sel.left * this.size.width, sel.top * this.size.height)
                val sz = Size(sel.width * this.size.width, sel.height * this.size.height)
                drawRect(Color(0x33FFFFFF), tl, sz)
                drawRect(accent, tl, sz, style = Stroke(width = 3.dp.toPx()))
            }
        }
    }
}

@Composable
fun AudioConvertScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val id = "audio_convert"
    val pick = rememberPicker(AUDIO_MIME, true)
    var format by remember { mutableIntStateOf(0) }
    var quality by remember { mutableIntStateOf(1) }
    val fmt = MediaTools.audioFormats[format]

    ToolPage("Audio converter", "Convert songs, recordings or the sound of a video", onBack) {
        FilePickerCard("Choose audio or video files", pick)
        OptionLabel("Convert to")
        ChipRows(MediaTools.audioFormats.mapIndexed { i, f -> f.label to (i == format) }, 3) { format = it }
        if (fmt.ext == "m4r") Text("iPhone ringtones are limited to 40 seconds.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (fmt.ext == "mp3" || fmt.ext == "m4a" || fmt.ext == "ogg" || fmt.ext == "mp2" || fmt.ext == "m4r") {
            OptionLabel("Quality")
            ChipRows(MediaTools.audioQualities.mapIndexed { i, (l, k) -> "$l · $k" to (i == quality) }, 2) { quality = it }
        }
        RunButton("Convert", pick.files.isNotEmpty(), id) {
            val files = pick.files.toList()
            val ext = fmt.ext
            val kbps = MediaTools.audioQualities[quality].second
            ToolRunner.start(id, context) { tc ->
                files.mapIndexed { i, f -> MediaTools.convertAudio(tc, f, ext, kbps, i, files.size) }
            }
        }
        ToolResultPanel(id)
    }
}

@Composable
fun TrimAudioScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val id = "audio_trim"
    val pick = rememberPicker(AUDIO_MIME, false)
    val file = pick.files.firstOrNull()
    var duration by remember { mutableDoubleStateOf(0.0) }
    var range by remember { mutableStateOf(0f..1f) }
    var outFormat by remember { mutableIntStateOf(0) } // 0 = same as original
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    val formats = listOf("Same as original", "mp3", "m4a", "wav")

    fun stopPlayer() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        playing = false
    }
    DisposableEffect(Unit) { onDispose { stopPlayer() } }
    LaunchedEffect(file?.uri) {
        stopPlayer()
        duration = 0.0
        if (file != null) {
            duration = withContext(Dispatchers.IO) { Ffmpeg.durationSec(context, file.uri) }
            range = 0f..duration.toFloat().coerceAtLeast(1f)
        }
    }
    LaunchedEffect(playing) {
        while (playing) {
            val p = player
            if (p == null || runCatching { p.currentPosition }.getOrDefault(0) >= range.endInclusive * 1000) stopPlayer()
            delay(100)
        }
    }

    ToolPage("Trim audio", "Keep just the part you want", onBack) {
        FilePickerCard("Choose an audio file", pick)
        if (file != null && duration > 0.5) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Start ${formatTime(range.start.toDouble())}", style = MaterialTheme.typography.titleSmall)
                Text("End ${formatTime(range.endInclusive.toDouble())}", style = MaterialTheme.typography.titleSmall)
            }
            RangeSlider(value = range, onValueChange = { range = it }, valueRange = 0f..duration.toFloat())
            Text("Length ${formatTime((range.endInclusive - range.start).toDouble())} of ${formatTime(duration)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilledTonalButton(
                onClick = {
                    if (playing) stopPlayer() else {
                        val mp = MediaPlayer()
                        try {
                            mp.setDataSource(context, file.uri)
                            mp.setOnPreparedListener { it.seekTo((range.start * 1000).toInt()); it.start(); playing = true }
                            mp.setOnErrorListener { _, _, _ -> stopPlayer(); true }
                            mp.prepareAsync()
                            player = mp
                        } catch (e: Exception) { runCatching { mp.release() } }
                    }
                }, modifier = Modifier.fillMaxWidth()
            ) { Text(if (playing) "Stop preview" else "Play selected part") }
            OptionLabel("Save as")
            ChipRows(formats.mapIndexed { i, l -> l to (i == outFormat) }, 2) { outFormat = it }
            RunButton("Trim", true, id) {
                stopPlayer()
                val f = file
                val s = range.start.toDouble()
                val e = range.endInclusive.toDouble()
                val fmt = if (outFormat == 0) null else formats[outFormat]
                ToolRunner.start(id, context) { tc -> listOf(MediaTools.trimAudio(tc, f, s, e, fmt, 192)) }
            }
        }
        ToolResultPanel(id)
    }
}
