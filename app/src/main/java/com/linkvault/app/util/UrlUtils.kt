package com.linkvault.app.util

import android.net.Uri

data class ParsedUrl(
    val url: String,
    val normalized: String,
    val domain: String,
    val platform: Platform,
    val suggestedTitle: String
)

sealed class UrlResult {
    data class Ok(val parsed: ParsedUrl) : UrlResult()
    data class Error(val message: String) : UrlResult()
}

object UrlUtils {
    private val urlRegex = Regex("""(?i)\b((?:https?://|www\.)[^\s<>"']+)""")
    private val trackingParams = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "fbclid", "gclid", "igshid", "si", "feature", "ref", "ref_src", "mc_cid", "mc_eid"
    )

    /** Pulls the first URL out of shared text such as "Check this out https://… " */
    fun extractUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val m = urlRegex.find(text) ?: return null
        return m.value.trimEnd('.', ',', ')', ';', '!', '?')
    }

    fun parse(input: String): UrlResult {
        val raw = input.trim()
        if (raw.isEmpty()) return UrlResult.Error("Please enter a URL.")
        if (raw.any { it.isWhitespace() }) return UrlResult.Error("That doesn't look like a single link.")

        val withScheme = when {
            raw.startsWith("http://", true) || raw.startsWith("https://", true) -> raw
            raw.contains("://") -> return UrlResult.Error("Only web links (http or https) are supported.")
            else -> "https://$raw"
        }
        val uri = try { Uri.parse(withScheme) } catch (e: Exception) { null }
        val host = uri?.host?.lowercase()
        if (uri == null || host.isNullOrBlank() || !host.contains('.') || host.startsWith(".") || host.endsWith(".")) {
            return UrlResult.Error("That doesn't look like a valid link. Check it and try again.")
        }
        val domain = host.removePrefix("www.")
        val platform = Platform.detect(domain, uri.path.orEmpty())
        return UrlResult.Ok(
            ParsedUrl(
                url = withScheme,
                normalized = normalize(uri),
                domain = domain,
                platform = platform,
                suggestedTitle = suggestTitle(domain, uri, platform)
            )
        )
    }

    /** Lower-cased host, no www, no fragment, no tracking params, sorted query, no trailing slash. */
    fun normalize(uri: Uri): String {
        val host = uri.host.orEmpty().lowercase().removePrefix("www.")
        var path = uri.encodedPath.orEmpty().trimEnd('/')
        if (host == "youtu.be") {
            return "youtube.com/watch?v=" + path.trimStart('/')
        }
        val query = uri.queryParameterNames
            .filter { it.lowercase() !in trackingParams }
            .sorted()
            .flatMap { name -> uri.getQueryParameters(name).map { "$name=$it" } }
            .joinToString("&")
        val port = if (uri.port != -1 && uri.port != 80 && uri.port != 443) ":${uri.port}" else ""
        return host + port + path + if (query.isNotEmpty()) "?$query" else ""
    }

    fun normalizeString(url: String): String =
        (parse(url) as? UrlResult.Ok)?.parsed?.normalized ?: url.trim().lowercase()

    private fun suggestTitle(domain: String, uri: Uri, platform: Platform): String {
        val segments = uri.pathSegments.filter { it.isNotBlank() }
        return when (platform) {
            Platform.GITHUB -> if (segments.size >= 2) "${segments[0]}/${segments[1]}" else "GitHub"
            Platform.INSTAGRAM -> when {
                segments.firstOrNull() in setOf("p", "reel", "reels") -> "Instagram ${segments.first().replace("reels", "reel").replace("p", "post")}"
                segments.isNotEmpty() -> "@${segments[0]} on Instagram"
                else -> "Instagram"
            }
            Platform.YOUTUBE -> when {
                segments.firstOrNull()?.startsWith("@") == true -> "${segments[0]} on YouTube"
                "list" in uri.queryParameterNames && "v" !in uri.queryParameterNames -> "YouTube playlist"
                segments.firstOrNull() in setOf("channel", "c", "user") -> "YouTube channel"
                else -> "YouTube video"
            }
            Platform.WEB -> domain.substringBeforeLast('.').substringAfterLast('.')
                .ifEmpty { domain }.replaceFirstChar { it.uppercase() }
            else -> platform.label + if (segments.isNotEmpty()) " · ${segments.last().take(30)}" else ""
        }
    }

    /** Short display form: domain plus a trimmed path. */
    fun shortUrl(url: String): String {
        val uri = try { Uri.parse(url) } catch (e: Exception) { return url }
        val host = uri.host?.removePrefix("www.") ?: return url.take(40)
        val path = uri.path.orEmpty().trimEnd('/')
        val s = host + path
        return if (s.length > 48) s.take(45) + "…" else s
    }
}
