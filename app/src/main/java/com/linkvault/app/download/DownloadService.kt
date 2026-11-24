package com.linkvault.app.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.linkvault.app.MainActivity
import com.linkvault.app.data.AppDatabase
import com.linkvault.app.data.DownloadEntity
import com.linkvault.app.data.DownloadQuality
import com.linkvault.app.data.DownloadStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs the download queue one item at a time. It is a foreground service so Android keeps it
 * alive with the screen off, and it stops itself as soon as the queue is empty.
 */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)
    @Volatile private var currentId: Long = -1
    private val dao by lazy { AppDatabase.get(applicationContext).downloadDao() }
    private val nm by lazy { getSystemService(NOTIFICATION_SERVICE) as NotificationManager }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        nm.createNotificationChannel(NotificationChannel(CH_PROGRESS, "Download progress", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_DONE, "Finished downloads", NotificationManager.IMPORTANCE_DEFAULT))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            val id = intent.getLongExtra(EXTRA_ID, -1)
            if (id > 0) scope.launch { DownloadCenter.cancel(applicationContext, id) }
            return START_NOT_STICKY
        }
        // Must be called within seconds of startForegroundService().
        ServiceCompat.startForeground(
            this, NOTIF_ID, progressNotification("Preparing download…", -1, -1),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
        if (running.compareAndSet(false, true)) scope.launch { runQueue() }
        return START_NOT_STICKY
    }

    private suspend fun runQueue() {
        try {
            dao.failInterrupted(System.currentTimeMillis())
            while (true) {
                while (true) {
                    val next = dao.nextQueued() ?: break
                    process(next)
                }
                running.set(false)
                if (dao.nextQueued() == null) { shutDown(); return }
                // Something was queued while we were finishing: carry on unless a new worker already took over.
                if (!running.compareAndSet(false, true)) return
            }
        } catch (e: CancellationException) {
            running.set(false); throw e
        }
    }

    private suspend fun shutDown() = withContext(Dispatchers.Main) {
        ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun process(d: DownloadEntity) {
        if (dao.markRunning(d.id) == 0) return
        currentId = d.id
        val quality = DownloadQuality.fromKey(d.quality)
        val workDir = File(cacheDir, "dl/${d.id}").apply { deleteRecursively(); mkdirs() }
        var lastNotify = 0L
        var lastPct = -2
        notifyProgress(d.title, -1, d.id)
        try {
            val key = DownloadCenter.processKey(d.id)
            val report: (Float, Long) -> Unit = { pct, eta ->
                DownloadCenter.setProgress(d.id, DownloadCenter.Progress(pct, eta))
                val now = System.currentTimeMillis()
                if (now - lastNotify > 1000 && pct.toInt() != lastPct) {
                    lastNotify = now; lastPct = pct.toInt()
                    notifyProgress(d.title, pct.toInt(), d.id)
                }
            }
            // Pictures, PDFs and other plain files go straight over HTTP; video sites go through yt-dlp.
            val direct = quality == DownloadQuality.FILE || (!quality.audio && FileDownloader.looksLikeFile(d.url))
            if (direct) {
                FileDownloader.download(d.url, workDir, key, report)
            } else {
                DownloadEngine.ensureInitialised(applicationContext)
                try {
                    DownloadEngine.download(d.url, quality, workDir, key, CookieStore.file(applicationContext), report)
                } catch (e: Exception) {
                    val stillRunning = dao.get(d.id)?.status == DownloadStatus.RUNNING
                    if (!stillRunning) throw e
                    var failure: Exception = e
                    var done = false
                    // Sites change often and an old yt-dlp is the usual cause: update it once and try again.
                    if (!DownloadEngine.isNetworkError(e) && DownloadEngine.autoUpdate(applicationContext)) {
                        workDir.listFiles()?.forEach { it.delete() }
                        try {
                            DownloadEngine.download(d.url, quality, workDir, key, CookieStore.file(applicationContext), report)
                            done = true
                        } catch (e2: Exception) {
                            if (dao.get(d.id)?.status != DownloadStatus.RUNNING) throw e2
                            failure = e2
                        }
                    }
                    if (!done) {
                        // yt-dlp can't do it: it may be a file, a page with a picture, or an Instagram post/reel with a public embed.
                        val worthTrying = !quality.audio && (DownloadEngine.isUnsupported(failure) || "instagram.com" in d.url)
                        if (!worthTrying) throw failure
                        workDir.listFiles()?.forEach { it.delete() }
                        try { FileDownloader.download(d.url, workDir, key, report) }
                        catch (f: Exception) { throw failure }
                    }
                }
            }
            val outs = DownloadEngine.findOutputs(workDir)
            if (outs.isEmpty()) throw IllegalStateException("The download finished but produced no file.")
            // The user may have cancelled in the last moment; don't save anything then.
            if (dao.get(d.id)?.status != DownloadStatus.RUNNING) return
            notifyProgress(d.title, 100, d.id)
            // A post with several photos or videos produces several files; save every one of them.
            val saved = mutableListOf<String>()
            try {
                outs.forEach { saved += MediaSaver.save(applicationContext, it, quality.audio).toString() }
            } catch (e: Exception) {
                saved.forEach { MediaSaver.delete(applicationContext, it) }
                throw e
            }
            val uriList = saved.joinToString("\n")
            val baseTitle = outs.first().nameWithoutExtension.ifBlank { d.title }
            val title = if (outs.size > 1) "$baseTitle (+${outs.size - 1} more)" else baseTitle
            if (dao.markDone(d.id, uriList, outs.sumOf { it.length() }, title, System.currentTimeMillis()) == 0) {
                MediaSaver.delete(applicationContext, uriList)   // cancelled while saving
            } else {
                notifyFinished(d.id, title, ok = true, detail = "Saved to your device")
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) { dao.markFailed(d.id, "Interrupted. Tap retry to start again.", System.currentTimeMillis()) }
            throw e
        } catch (e: Exception) {
            if (!DownloadCenter.consumeCancelled(d.id)) {
                val msg = DownloadEngine.friendlyError(e)
                dao.markFailed(d.id, msg, System.currentTimeMillis())
                notifyFinished(d.id, d.title, ok = false, detail = msg)
            }
        } finally {
            workDir.deleteRecursively()
            DownloadCenter.clearProgress(d.id)
            currentId = -1
        }
    }

    // ---- Notifications ----

    private fun openDownloadsIntent(): PendingIntent = PendingIntent.getActivity(
        this, 1, Intent(this, MainActivity::class.java).setAction("com.linkvault.app.DOWNLOADS"),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun progressNotification(title: String, percent: Int, id: Long): Notification {
        val b = NotificationCompat.Builder(this, CH_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(if (percent in 0..100) "$percent%" else "Starting…")
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setContentIntent(openDownloadsIntent())
            .setProgress(100, percent.coerceIn(0, 100), percent < 0)
        if (id > 0) {
            val cancel = PendingIntent.getService(
                this, id.toInt(), Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL).putExtra(EXTRA_ID, id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            b.addAction(0, "Cancel", cancel)
        }
        return b.build()
    }

    private fun notifyProgress(title: String, percent: Int, id: Long) = nm.notify(NOTIF_ID, progressNotification(title, percent, id))

    private fun notifyFinished(id: Long, title: String, ok: Boolean, detail: String) {
        val n = NotificationCompat.Builder(this, CH_DONE)
            .setSmallIcon(if (ok) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setContentTitle(if (ok) "Download complete" else "Download failed")
            .setContentText(title).setSubText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$title\n$detail"))
            .setAutoCancel(true).setContentIntent(openDownloadsIntent())
            .build()
        nm.notify(FINISHED_BASE + id.toInt(), n)
    }

    /** Android 15 caps data-sync foreground services; stop cleanly instead of being killed. */
    override fun onTimeout(startId: Int, fgsType: Int) { stopSelf() }

    override fun onDestroy() {
        if (currentId > 0) DownloadEngine.cancel(DownloadCenter.processKey(currentId))
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_CANCEL = "com.linkvault.app.download.CANCEL"
        const val EXTRA_ID = "download_id"
        private const val CH_PROGRESS = "downloads_progress"
        private const val CH_DONE = "downloads_done"
        private const val NOTIF_ID = 4101
        private const val FINISHED_BASE = 5000
    }
}
