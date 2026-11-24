package com.linkvault.app.tools

import java.io.File
import java.util.Locale

class ResolutionPreset(val label: String, val width: Int, val height: Int) {
    val detail: String get() = "${width}x$height"
}

class AudioFormat(val label: String, val ext: String)

object MediaTools {
    val videoFormats = listOf("mp4", "avi", "mpeg", "mov", "flv", "3gp", "webm", "mkv")

    val presets = listOf(
        ResolutionPreset("HD 1080p", 1920, 1080),
        ResolutionPreset("HD 720p", 1280, 720),
        ResolutionPreset("480p", 854, 480),
        ResolutionPreset("360p", 640, 360),
        ResolutionPreset("240p", 426, 240),
        ResolutionPreset("DVD", 720, 576),
        ResolutionPreset("TV", 640, 480),
        ResolutionPreset("Mobile", 320, 240)
    )

    val audioFormats = listOf(
        AudioFormat("mp3", "mp3"), AudioFormat("wav", "wav"), AudioFormat("iPhone ringtone", "m4r"),
        AudioFormat("m4a", "m4a"), AudioFormat("flac", "flac"), AudioFormat("ogg", "ogg"),
        AudioFormat("mp2", "mp2"), AudioFormat("amr", "amr")
    )

    /** Audio quality presets, in kbps. */
    val audioQualities = listOf("Economy" to 64, "Standard" to 128, "Good" to 192, "Best" to 320)

    val videoQualities = listOf("Small file", "Balanced", "High quality")

    private fun even(v: Int) = (v and 1.inv()).coerceAtLeast(2)

    /** Scale filter for a target size. Keeps the picture's shape unless [keepAspect] is false. */
    fun scaleFilter(info: VideoInfo, targetW: Int, targetH: Int, keepAspect: Boolean): String {
        val landscape = info.width >= info.height
        val w = even(targetW)
        val h = even(targetH)
        return if (keepAspect) {
            if (landscape) "scale=-2:$h,setsar=1" else "scale=$h:-2,setsar=1"
        } else {
            if (landscape) "scale=$w:$h,setsar=1" else "scale=$h:$w,setsar=1"
        }
    }

    private fun videoCandidates(ext: String, q: Int): List<List<String>> {
        val crf = listOf("30", "25", "21")[q]
        val qv = listOf("8", "5", "3")[q]
        val ab = listOf("96k", "128k", "192k")[q]
        val x264 = listOf("-c:v", "libx264", "-preset", "veryfast", "-crf", crf, "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", ab)
        val mpeg4 = listOf("-c:v", "mpeg4", "-q:v", qv, "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", ab)
        return when (ext) {
            "mp4", "mov" -> listOf(x264 + listOf("-movflags", "+faststart"), mpeg4)
            "mkv" -> listOf(x264, mpeg4)
            "flv" -> listOf(x264, listOf("-c:v", "flv", "-q:v", qv, "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", ab))
            "avi" -> listOf(
                listOf("-c:v", "mpeg4", "-q:v", qv, "-pix_fmt", "yuv420p", "-c:a", "libmp3lame", "-b:a", ab),
                listOf("-c:v", "mpeg4", "-q:v", qv, "-pix_fmt", "yuv420p", "-c:a", "ac3", "-b:a", ab)
            )
            "mpeg" -> listOf(
                listOf("-c:v", "mpeg2video", "-q:v", qv, "-pix_fmt", "yuv420p", "-c:a", "mp2", "-b:a", ab, "-ar", "44100"),
                listOf("-c:v", "mpeg1video", "-q:v", qv, "-pix_fmt", "yuv420p", "-c:a", "mp2", "-b:a", ab, "-ar", "44100")
            )
            "3gp" -> listOf(
                listOf("-c:v", "mpeg4", "-q:v", qv, "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "96k", "-ar", "44100", "-ac", "2"),
                x264
            )
            "webm" -> {
                val vcrf = listOf("32", "24", "16")[q]
                val vb = listOf("700k", "1500k", "3000k")[q]
                listOf(
                    listOf("-c:v", "libvpx", "-crf", vcrf, "-b:v", vb, "-deadline", "realtime", "-cpu-used", "6", "-c:a", "libvorbis", "-b:a", ab),
                    listOf("-c:v", "libvpx", "-crf", vcrf, "-b:v", vb, "-deadline", "realtime", "-cpu-used", "6", "-c:a", "libopus", "-b:a", ab),
                    listOf("-c:v", "libvpx-vp9", "-crf", "34", "-b:v", "0", "-deadline", "realtime", "-cpu-used", "8", "-row-mt", "1", "-c:a", "libopus", "-b:a", ab)
                )
            }
            else -> listOf(x264, mpeg4)
        }
    }

    private fun audioCandidates(ext: String, kbps: Int): List<List<String>> {
        val b = "${kbps}k"
        return when (ext) {
            "mp3" -> listOf(listOf("-c:a", "libmp3lame", "-b:a", b))
            "wav" -> listOf(listOf("-c:a", "pcm_s16le"))
            "m4a" -> listOf(listOf("-c:a", "aac", "-b:a", b))
            "flac" -> listOf(listOf("-c:a", "flac"))
            "ogg" -> listOf(
                listOf("-c:a", "libvorbis", "-b:a", b),
                listOf("-c:a", "vorbis", "-strict", "-2", "-b:a", b),
                listOf("-c:a", "libopus", "-b:a", b)
            )
            "mp2" -> listOf(listOf("-c:a", "mp2", "-b:a", b))
            "amr" -> listOf(listOf("-c:a", "libopencore_amrnb", "-ar", "8000", "-ac", "1", "-b:a", "12.2k"))
            "m4r" -> listOf(listOf("-c:a", "aac", "-b:a", b, "-t", "40", "-f", "ipod"))
            else -> listOf(listOf("-c:a", "aac", "-b:a", b))
        }
    }

    private fun mapArgs() = listOf("-map", "0:v:0", "-map", "0:a:0?")

    private fun name(base: String, suffix: String, ext: String) = "${base.take(80)}_$suffix.$ext"

    private fun fraction(index: Int, total: Int, p: Float) = ((index + p) / total).coerceIn(0f, 1f)

    /** Video Converter: any video to [ext], optionally at a new resolution. */
    suspend fun convertVideo(
        tc: ToolContext, picked: PickedFile, ext: String, preset: ResolutionPreset?, quality: Int, index: Int, total: Int
    ): OutFile {
        Ffmpeg.prepare(tc.app)
        val input = tc.copyIn(picked)
        val info = Ffmpeg.videoInfo(tc.app, input)
        val out = tc.tempFile(name(picked.baseName, "converted", ext))
        val vf = preset?.let { listOf("-vf", scaleFilter(info, it.width, it.height, true)) } ?: emptyList()
        Ffmpeg.runCandidates(
            tc, videoCandidates(ext, quality), info.durationSec, out,
            build = { c -> listOf("-i", input.absolutePath) + mapArgs() + vf + c + out.absolutePath },
            onProgress = { p -> tc.progress(fraction(index, total, p), "Converting ${index + 1} of $total") }
        )
        return OutFile(out)
    }

    /** Resize Video: new resolution, keeping the same kind of file where possible. */
    suspend fun resizeVideo(
        tc: ToolContext, picked: PickedFile, targetW: Int, targetH: Int, keepAspect: Boolean, index: Int, total: Int
    ): OutFile {
        Ffmpeg.prepare(tc.app)
        val input = tc.copyIn(picked)
        val info = Ffmpeg.videoInfo(tc.app, input)
        val ext = picked.ext.takeIf { it in videoFormats } ?: "mp4"
        val out = tc.tempFile(name(picked.baseName, "${targetW}x$targetH", ext))
        val vf = listOf("-vf", scaleFilter(info, targetW, targetH, keepAspect))
        Ffmpeg.runCandidates(
            tc, videoCandidates(ext, 1), info.durationSec, out,
            build = { c -> listOf("-i", input.absolutePath) + mapArgs() + vf + c + out.absolutePath },
            onProgress = { p -> tc.progress(fraction(index, total, p), "Resizing ${index + 1} of $total") }
        )
        return OutFile(out)
    }

    /** Remove Logo: blurs out the chosen rectangle (all numbers are in the video's own pixels). */
    suspend fun removeLogo(tc: ToolContext, picked: PickedFile, x: Int, y: Int, w: Int, h: Int): OutFile {
        Ffmpeg.prepare(tc.app)
        val input = tc.copyIn(picked)
        val info = Ffmpeg.videoInfo(tc.app, input)
        // delogo needs the box to sit at least one pixel inside the picture.
        val cx = x.coerceIn(1, (info.width - 3).coerceAtLeast(1))
        val cy = y.coerceIn(1, (info.height - 3).coerceAtLeast(1))
        val cw = w.coerceIn(2, (info.width - 1 - cx).coerceAtLeast(2))
        val ch = h.coerceIn(2, (info.height - 1 - cy).coerceAtLeast(2))
        val out = tc.tempFile(name(picked.baseName, "no-logo", "mp4"))
        val vf = listOf("-vf", "delogo=x=$cx:y=$cy:w=$cw:h=$ch")
        val x264 = listOf("-c:v", "libx264", "-preset", "veryfast", "-crf", "22", "-pix_fmt", "yuv420p")
        val mp4v = listOf("-c:v", "mpeg4", "-q:v", "3", "-pix_fmt", "yuv420p")
        Ffmpeg.runCandidates(
            tc,
            listOf(x264 + listOf("-c:a", "copy"), x264 + listOf("-c:a", "aac", "-b:a", "160k"), mp4v + listOf("-c:a", "aac", "-b:a", "160k")),
            info.durationSec, out,
            build = { c -> listOf("-i", input.absolutePath) + mapArgs() + vf + c + listOf("-movflags", "+faststart", out.absolutePath) },
            onProgress = { p -> tc.progress(p, "Removing logo") }
        )
        return OutFile(out)
    }

    /** Audio Converter. [format] is the target extension from [audioFormats]. */
    suspend fun convertAudio(
        tc: ToolContext, picked: PickedFile, format: String, kbps: Int, index: Int, total: Int
    ): OutFile {
        Ffmpeg.prepare(tc.app)
        val input = tc.copyIn(picked)
        val dur = Ffmpeg.durationSec(tc.app, input)
        val out = tc.tempFile(name(picked.baseName, "converted", format))
        Ffmpeg.runCandidates(
            tc, audioCandidates(format, kbps), dur, out,
            build = { c -> listOf("-i", input.absolutePath, "-vn") + c + out.absolutePath },
            onProgress = { p -> tc.progress(fraction(index, total, p), "Converting ${index + 1} of $total") }
        )
        return OutFile(out, audio = true)
    }

    /** Trim Audio: keeps [startSec]..[endSec]. A null [format] keeps the original type without re-encoding. */
    suspend fun trimAudio(tc: ToolContext, picked: PickedFile, startSec: Double, endSec: Double, format: String?, kbps: Int): OutFile {
        Ffmpeg.prepare(tc.app)
        val input = tc.copyIn(picked)
        val len = (endSec - startSec).coerceAtLeast(0.1)
        val ext = format ?: picked.ext.ifBlank { "m4a" }
        val out = tc.tempFile(name(picked.baseName, "trimmed", ext))
        val s = String.format(Locale.US, "%.3f", startSec)
        val d = String.format(Locale.US, "%.3f", len)
        val candidates = if (format == null) listOf(listOf("-c:a", "copy")) else audioCandidates(format, kbps)
        Ffmpeg.runCandidates(
            tc, candidates, len, out,
            build = { c -> listOf("-ss", s, "-i", input.absolutePath, "-t", d, "-vn") + c + out.absolutePath },
            onProgress = { p -> tc.progress(p, "Trimming") }
        )
        return OutFile(out, audio = true)
    }

    /** Converts a PNG made from any picture into one of ffmpeg's still-image formats. */
    fun convertStill(tc: ToolContext, png: File, ext: String, out: File) {
        val extra = when (ext) {
            "pcx", "ppm", "jp2" -> listOf("-pix_fmt", "rgb24")
            "pgm" -> listOf("-pix_fmt", "gray")
            "gif" -> listOf("-pix_fmt", "rgb8")
            else -> emptyList()
        }
        val err = Ffmpeg.run(tc, listOf("-i", png.absolutePath, "-frames:v", "1") + extra + out.absolutePath, 0.0) { }
        if (err != null || !out.exists() || out.length() == 0L) throw ToolException("This phone can't write .$ext pictures.")
    }
}
