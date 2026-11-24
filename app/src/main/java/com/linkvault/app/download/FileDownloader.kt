package com.linkvault.app.download

import android.net.Uri
import android.webkit.MimeTypeMap
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Plain HTTP downloader for everything that is just a file: images, PDFs, documents, archives,
 * audio. yt-dlp is only for video sites, so these never need it. If the link is a web page rather
 * than a file, the page's main picture (its og:image) is saved instead, which covers public
 * image posts. Pages with nothing to save throw [NotDownloadableException].
 */
object FileDownloader {
    class NotDownloadableException(message: String) : IOException(message)

    private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    /** Instagram only hands its preview tags to link-preview crawlers, so we ask like one for the post page. */
    private const val UA_CRAWLER = "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)"
    private val cancelled: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Extensions we are sure are files, so these links skip yt-dlp entirely. */
    private val fileExtensions = setOf(
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif", "tif", "tiff", "svg", "ico",
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "txt", "csv", "rtf", "epub", "mobi",
        "zip", "rar", "7z", "tar", "gz", "apk", "json", "xml", "ttf", "otf", "mp3", "m4a", "wav", "flac", "ogg", "aac"
    )

    private val metaImage = Regex(
        """<meta[^>]+(?:property|name)=["'](?:og:image(?::secure_url)?|twitter:image)["'][^>]*content=["']([^"']+)["']""" +
            """|<meta[^>]+content=["']([^"']+)["'][^>]+(?:property|name)=["'](?:og:image(?::secure_url)?|twitter:image)["']""",
        RegexOption.IGNORE_CASE
    )

    fun cancel(processId: String) { cancelled.add(processId) }

    fun looksLikeFile(url: String): Boolean {
        val path = try { Uri.parse(url).path.orEmpty() } catch (e: Exception) { "" }
        return path.substringAfterLast('.', "").lowercase() in fileExtensions
    }

    /** Blocking. Leaves the finished file in [dir]. [onProgress] gets percent (negative if unknown) and seconds left (0 if unknown). */
    fun download(url: String, dir: File, processId: String, onProgress: (Float, Long) -> Unit) {
        try {
            dir.mkdirs()
            if ("instagram.com" in url) {
                // yt-dlp only handles Instagram videos. Photo posts (single or several) come from the public embed.
                val images = instagramImages(url)
                if (images.isNotEmpty()) {
                    images.forEachIndexed { i, image ->
                        save(open(image), dir, processId) { p, eta -> onProgress((i * 100f + maxOf(p, 0f)) / images.size, eta) }
                    }
                    return
                }
            }
            val conn = open(url)
            val type = conn.contentType.orEmpty().substringBefore(';').trim().lowercase()
            if (type == "text/html" || type == "application/xhtml+xml") {
                val html = conn.inputStream.use { readHead(it, 600_000) }
                conn.disconnect()
                val image = pageImage(html, conn.url.toString())
                    ?: throw NotDownloadableException("This page has no file to download.")
                save(open(image), dir, processId, onProgress)
            } else {
                save(conn, dir, processId, onProgress)
            }
        } finally {
            cancelled.remove(processId)
        }
    }

    private fun instagramImages(url: String): List<String> {
        val code = Regex("""instagram\.com/(?:[^/]+/)?(?:p|reel|reels|tv)/([A-Za-z0-9_-]+)""").find(url)?.groupValues?.get(1) ?: return emptyList()
        val found = linkedSetOf<String>()
        fun clean(raw: String) = raw.replace("\\/", "/").replace("\\u0026", "&").replace("&amp;", "&").trim()
        // 1) The embed page lists every photo of a post.
        try {
            val c = open("https://www.instagram.com/p/$code/embed/captioned/")
            val html = c.inputStream.use { readHead(it, 1_500_000) }
            c.disconnect()
            // A reel's embed carries the video itself; if present, that is what the person wants.
            Regex("\"video_url\"\\s*:\\s*\"([^\"]+)\"").findAll(html).forEach { m -> clean(m.groupValues[1]).takeIf { it.startsWith("http") }?.let { found += it } }
            if (found.isNotEmpty()) return found.toList()
            Regex("\"display_url\"\\s*:\\s*\"([^\"]+)\"").findAll(html).forEach { m -> clean(m.groupValues[1]).takeIf { it.startsWith("http") }?.let { found += it } }
            if (found.isEmpty()) {
                Regex("class=\"EmbeddedMediaImage\"[^>]*src=\"([^\"]+)\"").find(html)?.let { m -> clean(m.groupValues[1]).takeIf { it.startsWith("http") }?.let { found += it } }
            }
        } catch (e: Exception) { /* fall through to the next way */ }
        // 2) The post page as a link-preview crawler sees it (one picture).
        if (found.isEmpty()) {
            try {
                val c = open(url, UA_CRAWLER)
                val html = c.inputStream.use { readHead(it, 600_000) }
                c.disconnect()
                pageImage(html, url)?.let { found += it }
            } catch (e: Exception) { /* nothing found */ }
        }
        return found.toList()
    }

    /** Size in bytes the server reports for [url], or null if it does not say. Never throws. */
    fun contentLength(url: String): Long? = try {
        val c = open(url)
        val len = c.contentLengthLong
        c.disconnect()
        if (len > 0) len else null
    } catch (e: Exception) { null }

    private fun open(startUrl: String, userAgent: String = UA): HttpURLConnection {
        var current = startUrl
        repeat(8) {
            val c = URL(current).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.setRequestProperty("User-Agent", userAgent)
            c.setRequestProperty("Accept", "*/*")
            val code = c.responseCode
            if (code in 300..399) {
                val location = c.getHeaderField("Location")
                c.disconnect()
                if (location.isNullOrBlank()) throw IOException("The link redirected nowhere.")
                current = URL(URL(current), location).toString()
            } else if (code in 200..299) {
                return c
            } else {
                c.disconnect()
                throw IOException(if (code == 401 || code == 403) "The site refused the download (error $code). It may need a login." else "The server answered $code.")
            }
        }
        throw IOException("Too many redirects.")
    }

    private fun readHead(input: InputStream, max: Int): String {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (out.size() < max) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toString("UTF-8")
    }

    private fun pageImage(html: String, base: String): String? {
        val m = metaImage.find(html) ?: return null
        val raw = (m.groupValues[1].ifEmpty { m.groupValues[2] }).replace("&amp;", "&").trim()
        if (raw.isEmpty()) return null
        return try { URL(URL(base), raw).toString() } catch (e: Exception) { null }
    }

    private fun save(conn: HttpURLConnection, dir: File, processId: String, onProgress: (Float, Long) -> Unit) {
        val type = conn.contentType.orEmpty().substringBefore(';').trim().lowercase()
        val total = conn.contentLengthLong
        val name = fileName(conn, conn.url.toString(), type)
        val part = File(dir, "$name.part")
        val target = File(dir, name)
        var done = 0L
        val started = System.currentTimeMillis()
        var lastReport = 0L
        conn.inputStream.use { input ->
            part.outputStream().use { output ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    if (processId in cancelled) throw IOException("Cancelled.")
                    val n = input.read(buf)
                    if (n < 0) break
                    output.write(buf, 0, n)
                    done += n
                    val now = System.currentTimeMillis()
                    if (now - lastReport > 250) {
                        lastReport = now
                        val pct = if (total > 0) done * 100f / total else -1f
                        val perSecond = done / maxOf(1L, (now - started) / 1000)
                        val eta = if (total > 0 && perSecond > 0) (total - done) / perSecond else 0L
                        onProgress(pct, eta)
                    }
                }
            }
        }
        conn.disconnect()
        if (done == 0L) { part.delete(); throw IOException("The file was empty.") }
        if (!part.renameTo(target)) { part.copyTo(target, overwrite = true); part.delete() }
        onProgress(100f, 0L)
    }

    private fun fileName(conn: HttpURLConnection, finalUrl: String, type: String): String {
        var name: String? = null
        val disposition = conn.getHeaderField("Content-Disposition")
        if (disposition != null) {
            Regex("""filename\*\s*=\s*[^']*'[^']*'([^;]+)""", RegexOption.IGNORE_CASE).find(disposition)?.groupValues?.get(1)?.let {
                name = try { URLDecoder.decode(it.trim(), "UTF-8") } catch (e: Exception) { it.trim() }
            }
            if (name == null) {
                Regex("""filename\s*=\s*"?([^";]+)"?""", RegexOption.IGNORE_CASE).find(disposition)?.groupValues?.get(1)?.let { name = it.trim() }
            }
        }
        if (name.isNullOrBlank()) name = try { Uri.parse(finalUrl).lastPathSegment } catch (e: Exception) { null }
        var clean = (name ?: "").ifBlank { "download" }
        clean = clean.replace(Regex("""[\\/:*?"<>|\u0000-\u001f]"""), "_").trim().take(120).ifBlank { "download" }
        if ('.' !in clean) {
            MimeTypeMap.getSingleton().getExtensionFromMimeType(type)?.let { clean += ".$it" }
        }
        return clean
    }
}
