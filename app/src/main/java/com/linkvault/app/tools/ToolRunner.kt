package com.linkvault.app.tools

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.linkvault.app.download.MediaSaver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** A message that is safe to show to the person as-is. */
class ToolException(message: String) : Exception(message)

/** A file the person chose with the system picker. */
class PickedFile(val uri: Uri, val name: String, val size: Long) {
    val ext: String get() = name.substringAfterLast('.', "").lowercase()
    val baseName: String get() = name.substringBeforeLast('.', name)
}

fun queryPicked(context: Context, uri: Uri): PickedFile {
    var name = "file"
    var size = -1L
    try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val si = c.getColumnIndex(OpenableColumns.SIZE)
                if (ni >= 0 && !c.isNull(ni)) name = c.getString(ni)
                if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
            }
        }
    } catch (_: Exception) { }
    if (name == "file") uri.lastPathSegment?.let { name = it.substringAfterLast('/') }
    return PickedFile(uri, name, size)
}

/** A finished file, already saved where the person can find it. */
class OutFile(val file: File, val audio: Boolean = false)

data class ToolResult(val name: String, val uri: String, val size: Long, val audio: Boolean, val originalSize: Long = -1L)

data class ToolState(
    val running: Boolean = false,
    val progress: Float = -1f,
    val message: String = "",
    val results: List<ToolResult> = emptyList(),
    val error: String? = null,
    val note: String? = null
)

class ToolSession {
    val state = MutableStateFlow(ToolState())
    @Volatile var job: Job? = null
    @Volatile var process: Process? = null
    @Volatile var cancelled = false
}

/** Handed to a tool's work function so it can report progress, make temp files and be cancelled. */
class ToolContext(val app: Context, private val session: ToolSession) {
    private val temps = ArrayList<File>()
    var note: String? = null

    val cancelled: Boolean get() = session.cancelled
    fun checkCancelled() { if (session.cancelled) throw CancellationException("cancelled") }
    fun setProcess(p: Process?) { session.process = p }

    fun progress(p: Float, message: String? = null) {
        session.state.update { it.copy(progress = p, message = message ?: it.message) }
    }

    private fun workDir(): File = File(app.cacheDir, "tools").apply { mkdirs() }

    /** A new empty temp file that is deleted when the job ends. */
    fun tempFile(name: String): File {
        val safe = name.replace(Regex("[^A-Za-z0-9._ -]"), "_")
        val f = File(workDir(), "${System.nanoTime()}_$safe")
        temps.add(f)
        return f
    }

    /** A temp directory that is deleted when the job ends. */
    fun tempDir(name: String): File {
        val d = File(workDir(), "${System.nanoTime()}_$name").apply { mkdirs() }
        temps.add(d)
        return d
    }

    /** Copies a picked file into the app so tools that need a real path can read it. */
    fun copyIn(p: PickedFile): File {
        val f = tempFile(p.name)
        app.contentResolver.openInputStream(p.uri)?.use { input ->
            f.outputStream().use { out -> input.copyTo(out) }
        } ?: throw ToolException("Couldn't open ${p.name}.")
        return f
    }

    fun cleanup() { temps.forEach { runCatching { it.deleteRecursively() } }; temps.clear() }
}

object ToolRunner {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessions = ConcurrentHashMap<String, ToolSession>()

    fun session(id: String): ToolSession = sessions.getOrPut(id) { ToolSession() }
    fun state(id: String): StateFlow<ToolState> = session(id).state.asStateFlow()

    fun isRunning(id: String) = session(id).state.value.running

    /** Runs [block] in the background; it keeps going if the person leaves the screen. */
    fun start(id: String, app: Context, originalSize: Long = -1L, block: suspend (ToolContext) -> List<OutFile>) {
        val s = session(id)
        if (s.state.value.running) return
        s.cancelled = false
        s.state.value = ToolState(running = true, message = "Starting…")
        val appCtx = app.applicationContext
        s.job = scope.launch {
            val tc = ToolContext(appCtx, s)
            try {
                val outs = block(tc)
                if (outs.isEmpty()) throw ToolException("Nothing was produced.")
                tc.progress(-1f, "Saving…")
                val results = outs.map { o ->
                    val uri = MediaSaver.save(appCtx, o.file, o.audio)
                    ToolResult(o.file.name, uri.toString(), o.file.length(), o.audio, originalSize)
                }
                s.state.value = ToolState(results = results, note = tc.note)
            } catch (e: CancellationException) {
                s.state.value = ToolState(message = "Cancelled")
            } catch (e: ToolException) {
                s.state.value = ToolState(error = e.message)
            } catch (e: OutOfMemoryError) {
                s.state.value = ToolState(error = "This file is too big for the phone's memory. Try a smaller one.")
            } catch (e: Throwable) {
                s.state.value = ToolState(error = e.message?.take(300) ?: "Something went wrong.")
            } finally {
                s.process = null
                tc.cleanup()
            }
        }
    }

    fun cancel(id: String) {
        val s = session(id)
        s.cancelled = true
        try { s.process?.destroy() } catch (_: Exception) { }
        s.job?.cancel()
    }

    fun clear(id: String) {
        val s = session(id)
        if (!s.state.value.running) s.state.value = ToolState()
    }
}
