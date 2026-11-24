package com.linkvault.app.download

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

/**
 * Puts finished downloads where the user expects them: Movies/LinkVault for video,
 * Music/LinkVault for audio, Pictures/LinkVault for images, Download/LinkVault for everything else, visible to the Gallery, Files and music apps. Needs no storage
 * permission on Android 10+. On Android 9 and older the file goes into the app's own external
 * folder instead, so no permission is needed there either.
 */
object MediaSaver {
    private const val FOLDER = "LinkVault"

    fun authority(context: Context) = "${context.packageName}.files"

    private enum class Kind { VIDEO, AUDIO, IMAGE, FILE }

    /** Chooses the folder from what the file really is, so pictures land in Pictures and PDFs in Download. */
    fun save(context: Context, file: File, audio: Boolean): Uri {
        val ext = file.extension.lowercase()
        var mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext).orEmpty()
        val kind = when {
            audio -> Kind.AUDIO
            mime.startsWith("video/") -> Kind.VIDEO
            mime.startsWith("image/") -> Kind.IMAGE
            mime.startsWith("audio/") -> Kind.AUDIO
            else -> Kind.FILE
        }
        if (kind == Kind.AUDIO && !mime.startsWith("audio/")) mime = "audio/mp4"
        if (mime.isEmpty()) mime = "application/octet-stream"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) saveToMediaStore(context, file, kind, mime)
        else saveToAppFolder(context, file, kind)
    }

    private fun saveToMediaStore(context: Context, file: File, kind: Kind, mime: String): Uri {
        val collection = when (kind) {
            Kind.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            Kind.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            Kind.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            Kind.FILE -> MediaStore.Downloads.EXTERNAL_CONTENT_URI
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, folderFor(kind) + "/" + FOLDER)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values) ?: throw IOException("Couldn't create the file on your device.")
        try {
            val out = resolver.openOutputStream(uri) ?: throw IOException("Couldn't write to your device.")
            out.use { o -> file.inputStream().use { it.copyTo(o) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            try { resolver.delete(uri, null, null) } catch (_: Exception) {}
            throw e
        }
        return uri
    }

    private fun folderFor(kind: Kind) = when (kind) {
        Kind.AUDIO -> Environment.DIRECTORY_MUSIC
        Kind.VIDEO -> Environment.DIRECTORY_MOVIES
        Kind.IMAGE -> Environment.DIRECTORY_PICTURES
        Kind.FILE -> Environment.DIRECTORY_DOWNLOADS
    }

    private fun saveToAppFolder(context: Context, file: File, kind: Kind): Uri {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(base, folderFor(kind) + "/" + FOLDER).apply { mkdirs() }
        var target = File(dir, file.name)
        var n = 1
        while (target.exists()) { target = File(dir, "${file.nameWithoutExtension} ($n).${file.extension}"); n++ }
        file.copyTo(target)
        return FileProvider.getUriForFile(context, authority(context), target)
    }

    /** A download may hold several files; their Uris are stored one per line. */
    private fun uris(text: String?): List<Uri> =
        text.orEmpty().lines().filter { it.isNotBlank() }.mapNotNull { try { Uri.parse(it.trim()) } catch (e: Exception) { null } }

    /** Best-effort removal of the saved file(s). Returns true if something was deleted. */
    fun delete(context: Context, uriString: String?): Boolean {
        var any = false
        for (uri in uris(uriString)) {
            val ok = try {
                if (uri.authority == authority(context)) {
                    val rel = uri.path.orEmpty().removePrefix("/dl/")
                    File(context.getExternalFilesDir(null) ?: context.filesDir, rel).delete()
                } else context.contentResolver.delete(uri, null, null) > 0
            } catch (e: Exception) { false }
            if (ok) any = true
        }
        return any
    }

    /** Opens the first file of the download. */
    fun open(context: Context, uriString: String?, audio: Boolean): Boolean {
        val uri = uris(uriString).firstOrNull() ?: return false
        val mime = try { context.contentResolver.getType(uri) } catch (e: Exception) { null } ?: if (audio) "audio/*" else "*/*"
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return try { context.startActivity(intent); true } catch (e: ActivityNotFoundException) { false } catch (e: SecurityException) { false }
    }

    fun share(context: Context, uriString: String?, audio: Boolean) {
        val list = uris(uriString)
        if (list.isEmpty()) return
        val send = if (list.size == 1) {
            val mime = try { context.contentResolver.getType(list[0]) } catch (e: Exception) { null } ?: if (audio) "audio/*" else "*/*"
            Intent(Intent.ACTION_SEND).apply { type = mime; putExtra(Intent.EXTRA_STREAM, list[0]) }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply { type = "*/*"; putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(list)) }
        }
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try { context.startActivity(Intent.createChooser(send, "Share file").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
    }
}
