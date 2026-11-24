package com.linkvault.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.text.Html
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class LinkMeta(val title: String?, val thumbnail: Bitmap?)

/** Video sites we try to show a cover image for. Everything else keeps its quiet icon tile. */
object MediaLinks {
    private val videoHosts = setOf(
        "vimeo.com", "tiktok.com", "dailymotion.com", "twitch.tv", "rumble.com", "bilibili.com", "ted.com"
    )

    fun isVideo(platform: Platform, domain: String, url: String): Boolean {
        val d = domain.lowercase()
        val path = try { Uri.parse(url).path.orEmpty().lowercase() } catch (e: Exception) { "" }
        return when {
            platform == Platform.YOUTUBE -> true
            platform == Platform.INSTAGRAM -> listOf("/reel", "/p/", "/tv/").any { path.contains(it) }
            platform == Platform.FACEBOOK -> d == "fb.watch" || listOf("/watch", "/reel", "/videos").any { path.contains(it) }
            else -> videoHosts.any { d == it || d.endsWith(".$it") }
        }
    }

    /** Video id from watch, youtu.be, shorts, embed and live URLs; null for channels/playlists. */
    fun youtubeId(url: String): String? {
        val uri = try { Uri.parse(url) } catch (e: Exception) { return null }
        val host = uri.host?.lowercase()?.removePrefix("www.")?.removePrefix("m.") ?: return null
        val seg = uri.pathSegments
        val id: String? = when {
            host == "youtu.be" -> seg.firstOrNull()
            host.endsWith("youtube.com") || host.endsWith("youtube-nocookie.com") -> {
                val v = uri.getQueryParameter("v")
                if (v != null) v else if (seg.size >= 2 && seg[0] in setOf("shorts", "embed", "live", "v")) seg[1] else null
            }
            else -> null
        }
        return id?.takeIf { Regex("^[A-Za-z0-9_-]{11}$").matches(it) }
    }
}

/**
 * Optional, best-effort metadata for a link the user is adding: the real title and, for video
 * links, a cover image. Requests go to the saved site itself (or YouTube's own oEmbed and image
 * servers for YouTube links), never to a third-party service. Every failure returns null quietly.
 */
object MetadataFetcher {
    private const val UA = "Mozilla/5.0 (Android) LinkVault"
    private const val HTML_LIMIT = 160 * 1024
    private const val IMAGE_LIMIT = 3 * 1024 * 1024

    suspend fun fetchMeta(url: String, platform: Platform, wantImage: Boolean): LinkMeta = withContext(Dispatchers.IO) {
        var title: String? = null
        val images = mutableListOf<String>()

        if (platform == Platform.YOUTUBE) {
            try {
                val json = JSONObject(readText("https://www.youtube.com/oembed?format=json&url=" + Uri.encode(url), 32 * 1024))
                title = json.optString("title").trim().ifEmpty { null }
            } catch (e: Exception) { /* fall through to the page itself */ }
            MediaLinks.youtubeId(url)?.let { id ->
                images += "https://i.ytimg.com/vi/$id/maxresdefault.jpg"
                images += "https://i.ytimg.com/vi/$id/mqdefault.jpg"
            }
        }

        if (title == null || (wantImage && images.isEmpty())) {
            try {
                val html = readText(url, HTML_LIMIT, stopAt = "</head>")
                if (title == null) title = metaContent(html, "og:title", "twitter:title") ?: titleTag(html)
                if (wantImage && images.isEmpty()) {
                    val img = metaContent(html, "og:image", "og:image:url", "twitter:image")
                    val resolved = if (img != null) resolve(url, img) else null
                    if (resolved != null) images += resolved
                }
            } catch (e: Exception) { /* page not reachable or not HTML */ }
        }

        var bitmap: Bitmap? = null
        if (wantImage) {
            for (img in images) {
                bitmap = downloadBitmap(img)
                if (bitmap != null) break
            }
        }
        val clean = title?.removeSuffix(" - YouTube")?.trim()?.take(140)?.takeIf { it.isNotEmpty() }
        LinkMeta(clean, bitmap)
    }

    suspend fun fetchFavicon(context: Context, domain: String) = withContext(Dispatchers.IO) {
        val file = Favicons.file(context, domain)
        if (file.exists()) return@withContext
        try {
            val conn = open("https://$domain/favicon.ico")
            val bytes = try { conn.inputStream.use { it.readBytes() } } finally { conn.disconnect() }
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext
            file.parentFile?.mkdirs()
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } catch (_: Exception) { }
    }

    // ---- helpers ----

    private fun open(url: String): HttpURLConnection {
        // HttpURLConnection will not follow http -> https redirects, so ask for https up front.
        val secure = url.replaceFirst(Regex("^http://", RegexOption.IGNORE_CASE), "https://")
        require(secure.startsWith("https://", ignoreCase = true)) { "Unsupported scheme" }
        return (URL(secure).openConnection() as HttpURLConnection).apply {
            connectTimeout = 4000
            readTimeout = 5000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept-Language", "en")
        }
    }

    private fun readText(url: String, limit: Int, stopAt: String? = null): String {
        val conn = open(url)
        try {
            conn.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(4096)
                while (out.size() < limit) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (stopAt != null && String(out.toByteArray(), Charsets.UTF_8).contains(stopAt, ignoreCase = true)) break
                }
                return String(out.toByteArray(), Charsets.UTF_8)
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun readBounded(input: InputStream, limit: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > limit) return null
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun downloadBitmap(url: String): Bitmap? = try {
        val conn = open(url)
        val bytes = try { conn.inputStream.use { readBounded(it, IMAGE_LIMIT) } } finally { conn.disconnect() }
        if (bytes == null) null else decode(bytes)
    } catch (e: Exception) { null }

    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 960) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
        // Tiny images (placeholders, tracking pixels) make poor covers.
        return if (bmp.width < 120 || bmp.height < 60) null else bmp
    }

    private val metaTag = Regex("<meta\\s[^>]*>", RegexOption.IGNORE_CASE)

    private fun attr(tag: String, name: String): String? {
        val m = Regex("\\b" + name + "\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')", RegexOption.IGNORE_CASE).find(tag) ?: return null
        val value = m.groupValues[1].ifEmpty { m.groupValues[2] }
        return value.takeIf { it.isNotBlank() }
    }

    private fun metaContent(html: String, vararg keys: String): String? {
        for (m in metaTag.findAll(html)) {
            val tag = m.value
            val name = (attr(tag, "property") ?: attr(tag, "name"))?.lowercase() ?: continue
            if (name in keys) {
                val content = attr(tag, "content") ?: continue
                return Html.fromHtml(content, Html.FROM_HTML_MODE_LEGACY).toString().trim()
            }
        }
        return null
    }

    private fun titleTag(html: String): String? {
        val m = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)
        val raw = m?.groupValues?.get(1)?.trim().orEmpty()
        if (raw.isEmpty()) return null
        return Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY).toString().replace(Regex("\\s+"), " ").trim().ifEmpty { null }
    }

    private fun resolve(base: String, img: String): String? = try {
        URL(URL(base), img).toString().takeIf { it.startsWith("http", ignoreCase = true) }
    } catch (e: Exception) { null }
}

object Favicons {
    fun file(context: Context, domain: String) =
        File(context.filesDir, "favicons/" + domain.replace(Regex("[^a-zA-Z0-9.-]"), "_") + ".png")

    fun load(context: Context, domain: String): Bitmap? {
        val f = file(context, domain)
        return if (f.exists()) try { BitmapFactory.decodeFile(f.path) } catch (_: Exception) { null } else null
    }
}

/** Cover images, stored on the device and keyed by the link's normalized URL (no database change). */
object Thumbnails {
    /** Bumped after every save so lists and the detail screen reload their images. */
    var version by mutableIntStateOf(0)
        private set

    fun file(context: Context, normalized: String): File {
        val hash = MessageDigest.getInstance("SHA-1").digest(normalized.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(context.filesDir, "thumbs/$hash.jpg")
    }

    fun exists(context: Context, normalized: String) = file(context, normalized).exists()

    fun load(context: Context, normalized: String): Bitmap? {
        val f = file(context, normalized)
        return if (f.exists()) try { BitmapFactory.decodeFile(f.path) } catch (_: Exception) { null } else null
    }

    suspend fun save(context: Context, normalized: String, bitmap: Bitmap) = withContext(Dispatchers.IO) {
        try {
            val f = file(context, normalized)
            f.parentFile?.mkdirs()
            f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            version += 1
        } catch (_: Exception) { }
    }
}
