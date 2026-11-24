package com.linkvault.app.download

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.linkvault.app.data.AppDatabase
import com.linkvault.app.data.DownloadEntity
import com.linkvault.app.data.DownloadQuality
import com.linkvault.app.data.DownloadStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

/**
 * The one place the UI talks to for downloads. Durable state (queue, results) lives in Room;
 * live progress is kept in memory because it changes many times a second and is worthless
 * after a restart.
 */
object DownloadCenter {
    data class Progress(val percent: Float, val etaSeconds: Long)

    private val _progress = MutableStateFlow<Map<Long, Progress>>(emptyMap())
    val progress: StateFlow<Map<Long, Progress>> = _progress.asStateFlow()

    /** Ids the user cancelled while running, so the worker doesn't report the killed process as a failure. */
    private val cancelled: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    fun processKey(id: Long) = "lv-dl-$id"
    fun setProgress(id: Long, p: Progress) = _progress.update { it + (id to p) }
    fun clearProgress(id: Long) = _progress.update { it - id }
    fun consumeCancelled(id: Long): Boolean = cancelled.remove(id)

    private fun dao(context: Context) = AppDatabase.get(context).downloadDao()

    suspend fun enqueue(context: Context, linkId: Long?, url: String, title: String, quality: DownloadQuality): Long {
        val id = dao(context).insert(
            DownloadEntity(linkId = linkId, url = url, title = title, quality = quality.name, createdAt = System.currentTimeMillis())
        )
        start(context)
        return id
    }

    /** Row is always written before this is called, so the service can't miss it. */
    fun start(context: Context) {
        ContextCompat.startForegroundService(context.applicationContext, Intent(context.applicationContext, DownloadService::class.java))
    }

    suspend fun cancel(context: Context, id: Long) {
        val d = dao(context).get(id) ?: return
        if (d.status == DownloadStatus.RUNNING) cancelled.add(id)
        // Mark first, then kill: the worker checks the row before it saves anything.
        if (dao(context).markCanceled(id, System.currentTimeMillis()) > 0 && d.status == DownloadStatus.RUNNING) {
            DownloadEngine.cancel(processKey(id))
        } else cancelled.remove(id)
    }

    suspend fun retry(context: Context, id: Long) {
        if (dao(context).requeue(id) > 0) start(context)
    }

    suspend fun remove(context: Context, id: Long, deleteFile: Boolean) {
        val d = dao(context).get(id) ?: return
        if (d.status == DownloadStatus.RUNNING || d.status == DownloadStatus.QUEUED) cancel(context, id)
        if (deleteFile) MediaSaver.delete(context, d.fileUri)
        dao(context).delete(id)
    }

    suspend fun clearFinished(context: Context) = dao(context).clearFinished()
}
