package com.linkvault.app.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

object ImageEngine {
    /** Formats the Image Converter can write. The first three are written by Android itself, the rest by ffmpeg. */
    val outputFormats = listOf("jpg", "png", "webp", "gif", "bmp", "tiff", "tga", "jp2", "pcx", "ppm", "pgm")
    private val nativeFormats = setOf("jpg", "png", "webp")

    /** Reads a picture (jpg, png, webp, gif, bmp, heic, heif ...) and applies its rotation. [maxEdge] 0 = full size. */
    fun decode(context: Context, uri: Uri, maxEdge: Int = 0): Bitmap {
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                return ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val longest = max(info.size.width, info.size.height)
                    if (maxEdge > 0 && longest > maxEdge) {
                        val s = maxEdge.toFloat() / longest
                        decoder.setTargetSize((info.size.width * s).toInt().coerceAtLeast(1), (info.size.height * s).toInt().coerceAtLeast(1))
                    }
                }
            } catch (e: Exception) { /* fall back to the older decoder below */ }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        if (maxEdge > 0) while (max(bounds.outWidth, bounds.outHeight) / sample > maxEdge * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw ToolException("This picture format can't be read on this phone.")
        val degrees = try {
            context.contentResolver.openInputStream(uri)?.use { s ->
                when (ExifInterface(s).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (e: Exception) { 0f }
        var result = bmp
        if (degrees != 0f) {
            result = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(degrees) }, true)
            if (result !== bmp) bmp.recycle()
        }
        return scaleDown(result, maxEdge)
    }

    fun scaleDown(bmp: Bitmap, maxEdge: Int): Bitmap {
        val longest = max(bmp.width, bmp.height)
        if (maxEdge <= 0 || longest <= maxEdge) return bmp
        val s = maxEdge.toFloat() / longest
        val out = Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt().coerceAtLeast(1), (bmp.height * s).toInt().coerceAtLeast(1), true)
        if (out !== bmp) bmp.recycle()
        return out
    }

    private fun flatten(bmp: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.WHITE)
        c.drawBitmap(bmp, 0f, 0f, null)
        return out
    }

    /** Writes [bmp] as jpg, png or webp. [quality] is 1..100 and only matters for jpg and webp. */
    fun write(bmp: Bitmap, ext: String, quality: Int, out: File) {
        FileOutputStream(out).use { os ->
            when (ext) {
                "png" -> bmp.compress(Bitmap.CompressFormat.PNG, 100, os)
                "webp" -> {
                    val fmt = if (Build.VERSION.SDK_INT >= 30) {
                        if (quality >= 100) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP_LOSSY
                    } else {
                        @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
                    }
                    bmp.compress(fmt, quality.coerceIn(1, 100), os)
                }
                else -> {
                    val flat = if (bmp.hasAlpha()) flatten(bmp) else bmp
                    flat.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), os)
                    if (flat !== bmp) flat.recycle()
                }
            }
        }
    }

    /** Image Converter for one picture. */
    suspend fun convert(tc: ToolContext, picked: PickedFile, ext: String, quality: Int): OutFile {
        val bmp = decode(tc.app, picked.uri, 8192)
        try {
            val out = tc.tempFile("${picked.baseName.take(80)}.$ext")
            if (ext in nativeFormats) {
                write(bmp, ext, quality, out)
            } else {
                Ffmpeg.prepare(tc.app)
                val png = tc.tempFile("${picked.baseName.take(60)}_src.png")
                write(bmp, "png", 100, png)
                MediaTools.convertStill(tc, png, ext, out)
            }
            if (!out.exists() || out.length() == 0L) throw ToolException("Couldn't convert ${picked.name}.")
            return OutFile(out)
        } finally {
            bmp.recycle()
        }
    }

    /** Photo compression: smaller dimensions and lower quality. [level] 0 = smallest file. */
    fun compress(tc: ToolContext, picked: PickedFile, level: Int): OutFile {
        val maxEdge = listOf(1280, 1920, 2560)[level]
        val quality = listOf(50, 70, 85)[level]
        val bmp = decode(tc.app, picked.uri, maxEdge)
        try {
            val ext = if (picked.ext == "png" && bmp.hasAlpha()) "webp" else "jpg"
            val out = tc.tempFile("${picked.baseName.take(80)}_compressed.$ext")
            write(bmp, ext, quality, out)
            return OutFile(out)
        } finally {
            bmp.recycle()
        }
    }
}
