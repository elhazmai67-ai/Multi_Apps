package com.linkvault.app.tools

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.linkvault.app.download.DownloadEngine
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class VideoInfo(val width: Int, val height: Int, val durationSec: Double)

/**
 * Runs the ffmpeg that already ships inside the app (it came with the downloader). Everything runs
 * on the phone; nothing is uploaded anywhere.
 */
object Ffmpeg {
    private val PROGRESS_KEYS = setOf(
        "frame", "fps", "stream_0_0_q", "stream_1_0_q", "bitrate", "total_size", "out_time_us", "out_time_ms",
        "out_time", "dup_frames", "drop_frames", "speed", "progress"
    )

    /** Unpacks the bundled ffmpeg the first time (a few seconds), then returns its binary. */
    suspend fun prepare(context: Context): File {
        DownloadEngine.ensureInitialised(context)
        return binary(context) ?: throw ToolException("The built-in converter isn't available on this phone.")
    }

    private fun binary(context: Context): File? {
        val f = File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")
        return if (f.exists()) f else null
    }

    /** Directories that hold ffmpeg's shared libraries, so the binary can start. */
    private fun libraryPath(context: Context): String {
        val dirs = LinkedHashSet<String>()
        val root = File(context.noBackupFilesDir, "youtubedl-android/packages/ffmpeg")
        if (root.exists()) {
            root.walkTopDown().maxDepth(5).forEach { f ->
                if (f.isFile && f.name.endsWith(".so")) f.parentFile?.absolutePath?.let { dirs.add(it) }
            }
            dirs.add(File(root, "usr/lib").absolutePath)
        }
        dirs.add(context.applicationInfo.nativeLibraryDir)
        return dirs.joinToString(":")
    }

    fun videoInfo(context: Context, file: File): VideoInfo {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(file.absolutePath)
            return readInfo(r)
        } catch (e: Exception) {
            throw ToolException("This doesn't look like a video the phone can read.")
        } finally { runCatching { r.release() } }
    }

    fun videoInfo(context: Context, uri: Uri): VideoInfo {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            return readInfo(r)
        } catch (e: Exception) {
            throw ToolException("This doesn't look like a video the phone can read.")
        } finally { runCatching { r.release() } }
    }

    private fun readInfo(r: MediaMetadataRetriever): VideoInfo {
        var w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        var h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        if (rot == 90 || rot == 270) { val t = w; w = h; h = t }
        val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        return VideoInfo(w, h, ms / 1000.0)
    }

    fun durationSec(context: Context, file: File): Double {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(file.absolutePath)
            (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) / 1000.0
        } catch (e: Exception) { 0.0 } finally { runCatching { r.release() } }
    }

    fun durationSec(context: Context, uri: Uri): Double {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, uri)
            (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) / 1000.0
        } catch (e: Exception) { 0.0 } finally { runCatching { r.release() } }
    }

    /**
     * Blocking. Runs ffmpeg once. [onProgress] gets 0..1 when the length is known. Returns null on
     * success, otherwise the tail of ffmpeg's error output.
     */
    fun run(tc: ToolContext, args: List<String>, durationSec: Double, onProgress: (Float) -> Unit): String? {
        val bin = binary(tc.app) ?: throw ToolException("The built-in converter isn't available on this phone.")
        val cmd = ArrayList<String>()
        cmd += bin.absolutePath
        cmd += listOf("-y", "-hide_banner", "-loglevel", "error", "-nostats", "-progress", "pipe:1")
        cmd += args
        val pb = ProcessBuilder(cmd)
        pb.environment()["LD_LIBRARY_PATH"] = libraryPath(tc.app)
        pb.redirectErrorStream(true)
        val process = pb.start()
        tc.setProcess(process)
        val log = ArrayList<String>()
        try {
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    val eq = line.indexOf('=')
                    val key = if (eq > 0) line.substring(0, eq) else ""
                    if (key == "out_time_us" || key == "out_time_ms") {
                        val us = line.substring(eq + 1).trim().toLongOrNull()
                        if (us != null && us >= 0 && durationSec > 0) onProgress((us / 1_000_000.0 / durationSec).toFloat().coerceIn(0f, 1f))
                    } else if (key !in PROGRESS_KEYS && line.isNotBlank()) {
                        log.add(line)
                        if (log.size > 40) log.removeAt(0)
                    }
                }
            }
            val code = process.waitFor()
            tc.checkCancelled()
            return if (code == 0) null else (log.takeLast(6).joinToString("\n").ifBlank { "The converter stopped (code $code)." })
        } finally {
            tc.setProcess(null)
            runCatching { process.destroy() }
        }
    }

    /**
     * Tries each argument set in turn (some phones' ffmpeg lack a particular encoder). The first one
     * that works wins. [argsFor] builds the full list for one candidate.
     */
    fun runCandidates(
        tc: ToolContext, candidates: List<List<String>>, durationSec: Double, output: File,
        build: (List<String>) -> List<String>, onProgress: (Float) -> Unit
    ) {
        var last: String? = null
        for (c in candidates) {
            tc.checkCancelled()
            runCatching { output.delete() }
            last = run(tc, build(c), durationSec, onProgress)
            if (last == null && output.exists() && output.length() > 0) return
        }
        throw ToolException(friendly(last))
    }

    private fun friendly(raw: String?): String {
        val r = raw.orEmpty()
        return when {
            r.contains("Unknown encoder", true) || r.contains("Encoder", true) && r.contains("not found", true) ||
                r.contains("Error selecting an encoder", true) -> "This phone's converter can't write that format. Try another format."
            r.contains("Invalid data found", true) || r.contains("moov atom not found", true) -> "This file looks damaged or isn't a media file."
            r.contains("No space left", true) -> "Not enough free space on the phone."
            r.isBlank() -> "The conversion didn't produce a file."
            else -> r.lines().last().take(200)
        }
    }
}
