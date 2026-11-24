package com.linkvault.app.download

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Optional login cookies for sites that only show things to a signed-in person (Instagram stories,
 * private posts). The user exports a Netscape-format cookies.txt from their own browser and picks
 * it here. It stays in the app's private storage and is only handed to yt-dlp.
 */
object CookieStore {
    private const val MAX_BYTES = 2 * 1024 * 1024

    fun file(context: Context) = File(context.filesDir, "cookies.txt")
    fun has(context: Context) = file(context).let { it.isFile && it.length() > 0 }

    /** Returns false if the picked file isn't a cookies.txt. */
    fun import(context: Context, uri: Uri): Boolean = try {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readNBytesCompat(MAX_BYTES + 1) } ?: ByteArray(0)
        val text = String(bytes, Charsets.UTF_8)
        val looksRight = bytes.size in 1..MAX_BYTES &&
            (text.contains("Netscape HTTP Cookie File") || text.lineSequence().any { it.count { c -> c == '\t' } >= 6 })
        if (looksRight) file(context).writeBytes(bytes)
        looksRight
    } catch (e: Exception) { false }

    fun clear(context: Context) { file(context).delete() }

    private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (out.size() < max) {
            val n = read(buf, 0, minOf(buf.size, max - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
