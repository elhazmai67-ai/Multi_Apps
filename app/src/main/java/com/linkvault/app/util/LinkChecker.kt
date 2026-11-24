package com.linkvault.app.util

import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException

enum class LinkHealth { ALIVE, DEAD, UNKNOWN }

data class CheckResult(val health: LinkHealth, val reason: String)

/**
 * Decides whether a saved link still works. It only calls a link "dead" when the site clearly says so
 * (404 / 410, a YouTube video that no longer exists, or a domain that no longer resolves). Blocked,
 * slow or erroring sites are "unknown" so a good link is never flagged by mistake.
 */
object LinkChecker {
    private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    /** These sites answer bots with login walls or fake errors, so a check would say nothing useful. */
    private val unreliable = setOf(
        Platform.INSTAGRAM, Platform.FACEBOOK, Platform.LINKEDIN, Platform.X, Platform.WHATSAPP,
        Platform.TELEGRAM, Platform.AMAZON, Platform.FLIPKART, Platform.NETFLIX, Platform.REDDIT
    )

    /** One quick request to a well-known endpoint, so "site not found" is never blamed on a phone that is offline. */
    fun isOnline(): Boolean = try { statusOf("https://www.google.com/generate_204", "GET") in 200..399 } catch (e: Exception) { false }

    fun check(url: String, platform: Platform): CheckResult {
        if (platform in unreliable) return CheckResult(LinkHealth.UNKNOWN, "Can't be checked")
        return try {
            if (platform == Platform.YOUTUBE) {
                if (MediaLinks.youtubeId(url) == null) return CheckResult(LinkHealth.UNKNOWN, "Can't be checked")
                val code = statusOf("https://www.youtube.com/oembed?format=json&url=" + android.net.Uri.encode(url), "GET")
                return when (code) {
                    in 200..299 -> CheckResult(LinkHealth.ALIVE, "OK")
                    404 -> CheckResult(LinkHealth.DEAD, "Video removed or not found")
                    else -> CheckResult(LinkHealth.UNKNOWN, "Private or can't be checked")
                }
            }
            var code = statusOf(url, "HEAD")
            // Some servers mishandle HEAD, so anything that isn't a clear success gets a second look with GET.
            if (code !in 200..399) code = statusOf(url, "GET")
            when (code) {
                in 200..399 -> CheckResult(LinkHealth.ALIVE, "OK")
                404 -> CheckResult(LinkHealth.DEAD, "Page not found (404)")
                410 -> CheckResult(LinkHealth.DEAD, "Page removed (410)")
                else -> CheckResult(LinkHealth.UNKNOWN, "Site answered $code")
            }
        } catch (e: UnknownHostException) {
            CheckResult(LinkHealth.DEAD, "Website no longer exists")
        } catch (e: Exception) {
            CheckResult(LinkHealth.UNKNOWN, "Couldn't connect")
        }
    }

    /** Status code after following up to 6 redirects by hand (also across http -> https). */
    private fun statusOf(startUrl: String, method: String): Int {
        var current = startUrl
        repeat(7) {
            val c = URL(current).openConnection() as HttpURLConnection
            try {
                c.instanceFollowRedirects = false
                c.connectTimeout = 8_000
                c.readTimeout = 8_000
                c.requestMethod = method
                c.setRequestProperty("User-Agent", UA)
                c.setRequestProperty("Accept", "*/*")
                val code = c.responseCode
                if (code in 300..399) {
                    val loc = c.getHeaderField("Location") ?: return code
                    current = URL(URL(current), loc).toString()
                } else return code
            } finally { c.disconnect() }
        }
        return 310
    }
}
