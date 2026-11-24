package com.linkvault.app.util

/**
 * Known platforms. Icons are drawn as a neutral monogram tile (see PlatformIcon) rather than the
 * official brand logos, which keeps the UI quiet and avoids shipping trademarked artwork.
 */
enum class Platform(
    val label: String,
    val monogram: String,
    val tint: Long,
    val hosts: List<String>,
    val packages: List<String> = emptyList()
) {
    YOUTUBE("YouTube", "Y", 0xFFC0392B, listOf("youtube.com", "youtu.be", "youtube-nocookie.com"), listOf("com.google.android.youtube")),
    INSTAGRAM("Instagram", "Ig", 0xFFB03A6F, listOf("instagram.com", "instagr.am"), listOf("com.instagram.android")),
    FACEBOOK("Facebook", "f", 0xFF3B5BA5, listOf("facebook.com", "fb.com", "fb.watch", "fb.me"), listOf("com.facebook.katana")),
    TELEGRAM("Telegram", "T", 0xFF2481AD, listOf("t.me", "telegram.me", "telegram.org"), listOf("org.telegram.messenger", "org.telegram.messenger.web")),
    WHATSAPP("WhatsApp", "W", 0xFF1F8F52, listOf("wa.me", "whatsapp.com"), listOf("com.whatsapp", "com.whatsapp.w4b")),
    GOOGLE_DRIVE("Google Drive", "Dr", 0xFF3C7D5C, listOf("drive.google.com"), listOf("com.google.android.apps.docs")),
    GOOGLE_DOCS("Google Docs", "Dc", 0xFF3B6FC0, listOf("docs.google.com"), emptyList()),
    GITHUB("GitHub", "Gh", 0xFF4A5568, listOf("github.com", "gist.github.com"), listOf("com.github.android")),
    REDDIT("Reddit", "R", 0xFFC2521D, listOf("reddit.com", "redd.it"), listOf("com.reddit.frontpage")),
    LINKEDIN("LinkedIn", "in", 0xFF2B6CA3, listOf("linkedin.com", "lnkd.in"), listOf("com.linkedin.android")),
    X("X", "X", 0xFF3A3F45, listOf("x.com", "twitter.com", "t.co"), listOf("com.twitter.android")),
    SPOTIFY("Spotify", "S", 0xFF1E8A4A, listOf("spotify.com", "spoti.fi"), listOf("com.spotify.music")),
    NETFLIX("Netflix", "N", 0xFFB0242B, listOf("netflix.com"), listOf("com.netflix.mediaclient")),
    AMAZON("Amazon", "A", 0xFFB86A12, listOf("amazon.com", "amazon.in", "amzn.to", "amzn.in", "a.co"), listOf("in.amazon.mShop.android.shopping", "com.amazon.mShop.android.shopping")),
    FLIPKART("Flipkart", "Fk", 0xFF2B6FD1, listOf("flipkart.com", "fkrt.it"), listOf("com.flipkart.android")),
    WEB("Website", "", 0xFF5E6C69, emptyList(), emptyList());

    companion object {
        fun fromKey(key: String): Platform = entries.firstOrNull { it.name == key } ?: WEB

        fun detect(host: String, path: String = ""): Platform {
            val h = host.lowercase().removePrefix("www.")
            // Amazon runs on many regional domains (amazon.co.uk, amazon.de, ...).
            if (h.startsWith("amazon.") || h.contains(".amazon.")) return AMAZON
            return entries.firstOrNull { p ->
                p.hosts.any { d -> h == d || h.endsWith(".$d") }
            } ?: WEB
        }
    }
}

/** Native app packages worth trying first for a URL. Google Docs links route by document type. */
fun nativePackagesFor(platform: Platform, url: String): List<String> {
    if (platform == Platform.GOOGLE_DOCS) {
        val u = url.lowercase()
        return when {
            "/spreadsheets" in u -> listOf("com.google.android.apps.docs.editors.sheets")
            "/presentation" in u -> listOf("com.google.android.apps.docs.editors.slides")
            "/forms" in u -> emptyList()
            else -> listOf("com.google.android.apps.docs.editors.docs")
        }
    }
    return platform.packages
}
