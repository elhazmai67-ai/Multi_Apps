package com.linkvault.app.download

import android.content.Context
import com.linkvault.app.data.DownloadQuality
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Thin wrapper around yt-dlp (via the youtubedl-android library). yt-dlp does the real work of
 * understanding each site; this class only builds the command line and reads the result.
 */
object DownloadEngine {
    private val initMutex = Mutex()
    @Volatile private var ready = false

    /** First call unpacks Python + ffmpeg into app storage, which takes a few seconds. Later calls are free. */
    suspend fun ensureInitialised(context: Context) {
        if (ready) return
        initMutex.withLock {
            if (ready) return
            withContext(Dispatchers.IO) {
                YoutubeDL.getInstance().init(context.applicationContext)
                FFmpeg.getInstance().init(context.applicationContext)
            }
            ready = true
        }
    }

    /**
     * Blocking: runs yt-dlp until the file is in [dir]. [onProgress] gets percent (negative while
     * unknown) and seconds remaining. Throws if yt-dlp fails or the process is destroyed (cancel).
     */
    fun download(url: String, quality: DownloadQuality, dir: File, processId: String, cookies: File? = null, onProgress: (Float, Long) -> Unit) {
        val request = YoutubeDLRequest(url)
        // Instagram posts with several photos/videos are "playlists" to yt-dlp, so let those through (capped).
        if ("instagram.com" in url) request.addOption("--playlist-end", "30") else request.addOption("--no-playlist")
        request.addOption("--no-mtime")
        request.addOption("-N", "4")
        if (cookies != null && cookies.isFile) request.addOption("--cookies", cookies.absolutePath)
        request.addOption("-o", dir.absolutePath + "/%(title).100B%(playlist_index& - {}|)s.%(ext)s")
        if (quality.audio) {
            request.addOption("-f", "bestaudio[ext=m4a]/bestaudio/best")
            request.addOption("-x")
            request.addOption("--audio-format", if (quality == DownloadQuality.AUDIO_MP3) "mp3" else "m4a")
            request.addOption("--audio-quality", "0")
        } else {
            request.addOption("-f", videoSelector(quality.maxHeight))
            request.addOption("--merge-output-format", "mp4")
        }
        YoutubeDL.getInstance().execute(request, processId) { progress, eta, _ -> onProgress(progress, eta) }
    }

    /**
     * Blocking. Asks yt-dlp what the link offers (without downloading) and estimates the size of each
     * quality. A missing entry/null means the site did not say. Never throws.
     */
    fun estimateSizes(url: String, processId: String, cookies: File? = null): Map<DownloadQuality, Long?> {
        val none = DownloadQuality.entries.associateWith<DownloadQuality, Long?> { null }
        if (FileDownloader.looksLikeFile(url)) {
            val len = FileDownloader.contentLength(url) ?: return none
            return DownloadQuality.entries.associateWith<DownloadQuality, Long?> { len }
        }
        return try {
            val request = YoutubeDLRequest(url)
            request.addOption("--dump-single-json")
            request.addOption("--no-playlist")
            request.addOption("--no-warnings")
            request.addOption("--socket-timeout", "10")
            if (cookies != null && cookies.isFile) request.addOption("--cookies", cookies.absolutePath)
            val out = YoutubeDL.getInstance().execute(request, processId) { _, _, _ -> }.out
            val root = org.json.JSONObject(out.substring(out.indexOf('{')))
            val duration = root.optDouble("duration", 0.0).let { if (it.isNaN()) 0.0 else it }
            val formats = root.optJSONArray("formats") ?: return none

            class F(val height: Int, val video: Boolean, val audio: Boolean, val size: Long, val tbr: Double, val ext: String)
            val list = ArrayList<F>()
            for (i in 0 until formats.length()) {
                val o = formats.optJSONObject(i) ?: continue
                val v = o.optString("vcodec", "none").let { it.isNotEmpty() && it != "none" && it != "null" }
                val a = o.optString("acodec", "none").let { it.isNotEmpty() && it != "none" && it != "null" }
                val tbr = o.optDouble("tbr", 0.0).let { if (it.isNaN()) 0.0 else it }
                var size = o.optLong("filesize", 0L)
                if (size <= 0) size = o.optLong("filesize_approx", 0L)
                if (size <= 0 && tbr > 0 && duration > 0) size = (tbr * 1000 / 8 * duration).toLong()
                list.add(F(o.optInt("height", 0), v, a, size, tbr, o.optString("ext", "")))
            }
            val bestAudio = list.filter { it.audio && !it.video && it.size > 0 }
                .maxWithOrNull(compareBy<F>({ it.ext == "m4a" }, { it.tbr }))

            fun video(maxHeight: Int?): Long? {
                val v = list.filter { it.video && it.height > 0 && (maxHeight == null || it.height <= maxHeight) }
                    .maxWithOrNull(compareBy<F>({ it.height }, { it.tbr })) ?: return null
                if (v.size <= 0) return null
                return if (v.audio) v.size else v.size + (bestAudio?.size ?: 0L)
            }

            val result = HashMap<DownloadQuality, Long?>()
            for (q in DownloadQuality.entries) {
                result[q] = when {
                    q == DownloadQuality.FILE -> null
                    q == DownloadQuality.AUDIO_MP3 -> if (duration > 0) (duration * 192_000 / 8).toLong() else bestAudio?.size
                    q.audio -> bestAudio?.size
                    else -> video(q.maxHeight)
                }
            }
            result
        } catch (e: Exception) { none }
    }

    /**
     * Prefers H.264 + AAC in MP4, which every Android phone can play, then falls back to whatever
     * the site offers within the height limit.
     */
    private fun videoSelector(maxHeight: Int?): String {
        val h = if (maxHeight != null) "[height<=$maxHeight]" else ""
        return "bv*[ext=mp4]$h+ba[ext=m4a]/b[ext=mp4]$h/bv*$h+ba/b$h/b"
    }

    /** Every finished file in [dir], in name order (a multi-photo post gives several). */
    fun findOutputs(dir: File): List<File> =
        dir.listFiles()
            ?.filter { it.isFile && it.length() > 0 && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") && !it.name.endsWith(".temp") }
            ?.sortedBy { it.name } ?: emptyList()

    /** The finished file in [dir]: the biggest one that isn't a leftover fragment. */
    fun findOutput(dir: File): File? =
        dir.listFiles()
            ?.filter { it.isFile && it.length() > 0 && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") && !it.name.endsWith(".temp") }
            ?.maxByOrNull { it.length() }

    /** yt-dlp doesn't know this link, so it may still be a plain file or a page with a picture. */
    fun isUnsupported(e: Throwable): Boolean {
        val raw = e.message.orEmpty()
        return "Unsupported URL" in raw || "No video formats found" in raw || "There is no video in this post" in raw
    }

    fun cancel(processId: String) {
        FileDownloader.cancel(processId)
        try { YoutubeDL.getInstance().destroyProcessById(processId) } catch (_: Exception) {}
    }

    @Volatile private var lastAutoUpdate = 0L

    /**
     * After a failed download, refresh yt-dlp (nightly builds carry the newest Instagram/site fixes) so the
     * caller can retry once. At most once every 6 hours. True only if a newer yt-dlp was actually installed.
     */
    suspend fun autoUpdate(context: Context): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastAutoUpdate < 6 * 3600_000L) return false
        lastAutoUpdate = now
        return try { update(context) == "yt-dlp updated" }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { false }
    }

    fun isNetworkError(e: Throwable): Boolean {
        val raw = e.message.orEmpty()
        return "Unable to resolve host" in raw || "Temporary failure in name resolution" in raw || "getaddrinfo" in raw
    }

    /** Sites change often, so yt-dlp needs updating now and then to keep working. */
    suspend fun update(context: Context): String {
        ensureInitialised(context)
        return withContext(Dispatchers.IO) {
            val status = YoutubeDL.getInstance().updateYoutubeDL(context.applicationContext, YoutubeDL.UpdateChannel.NIGHTLY)
            val label = status?.toString().orEmpty()
            when {
                "ALREADY" in label -> "yt-dlp is already up to date"
                "DONE" in label -> "yt-dlp updated"
                else -> "Update finished"
            }
        }
    }

    /** yt-dlp errors are long and technical; keep the one line a person can act on. */
    fun friendlyError(e: Throwable): String {
        val raw = e.message.orEmpty()
        val line = raw.lineSequence().map { it.trim() }.firstOrNull { it.startsWith("ERROR:") }?.removePrefix("ERROR:")?.trim()
        val text = line ?: raw.lineSequence().map { it.trim() }.lastOrNull { it.isNotEmpty() } ?: "Download failed."
        return when {
            "Unsupported URL" in raw -> "This link isn't supported. Updating yt-dlp in Settings may help."
            "No video formats found" in raw || "There is no video in this post" in raw ->
                "Couldn't find a photo or video to save. If the post is private, import your login cookies in Settings > Downloads."
            "login" in raw.lowercase() || "cookies" in raw.lowercase() ->
                "This needs a signed-in account. Import your browser cookies in Settings > Downloads."
            "Unable to resolve host" in raw || "Temporary failure in name resolution" in raw || "getaddrinfo" in raw ->
                "No internet connection."
            else -> text.take(200)
        }
    }
}
