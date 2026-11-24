package com.linkvault.app.ui.components

import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.linkvault.app.data.LinkEntity
import com.linkvault.app.ui.theme.Radius
import com.linkvault.app.util.MediaLinks
import com.linkvault.app.util.Platform
import com.linkvault.app.util.Thumbnails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val thumbCache = LruCache<String, ImageBitmap>(40)

/** Loads a saved cover image off the main thread; null while loading or when there is none. */
@Composable
fun rememberThumbnail(normalizedUrl: String, enabled: Boolean): ImageBitmap? {
    val context = LocalContext.current
    val version = Thumbnails.version
    val result by produceState<ImageBitmap?>(null, normalizedUrl, version, enabled) {
        value = if (!enabled) {
            null
        } else {
            val key = "$version|$normalizedUrl"
            thumbCache.get(key) ?: withContext(Dispatchers.IO) {
                Thumbnails.load(context, normalizedUrl)?.asImageBitmap()
            }?.also { thumbCache.put(key, it) }
        }
    }
    return result
}

/** Leading visual for a link row: the video cover when we have one, otherwise the platform tile. */
@Composable
fun LinkLeading(link: LinkEntity) {
    val platform = Platform.fromKey(link.platform)
    val isVideo = remember(link.url, link.platform) { MediaLinks.isVideo(platform, link.domain, link.url) }
    val thumb = rememberThumbnail(link.normalizedUrl, isVideo)
    if (thumb != null) {
        Box(
            Modifier.width(72.dp).height(44.dp).clip(Radius.small)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Image(thumb, contentDescription = "Video thumbnail", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    } else {
        PlatformIcon(platform, link.domain)
    }
}

/** Large 16:9 cover used on the Add and Details screens. */
@Composable
fun CoverImage(bitmap: ImageBitmap, modifier: Modifier = Modifier) {
    Image(
        bitmap, contentDescription = "Video cover",
        modifier = modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(Radius.card),
        contentScale = ContentScale.Crop
    )
}
