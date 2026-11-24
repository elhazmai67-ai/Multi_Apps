package com.linkvault.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Lifecycle of a download. Stored as plain strings so the table never depends on enum ordering. */
object DownloadStatus {
    const val QUEUED = "QUEUED"
    const val RUNNING = "RUNNING"
    const val DONE = "DONE"
    const val FAILED = "FAILED"
    const val CANCELED = "CANCELED"

    val finished = setOf(DONE, FAILED, CANCELED)
}

/** What to download. [audio] qualities extract the sound track only. */
enum class DownloadQuality(val label: String, val hint: String, val audio: Boolean, val maxHeight: Int? = null) {
    BEST("Best available", "Highest quality video with sound", false),
    P1080("1080p", "Full HD, drops to the closest lower size", false, 1080),
    P720("720p", "HD, a good balance of size and quality", false, 720),
    P480("480p", "Smaller files, saves data and space", false, 480),
    AUDIO_M4A("Audio only · M4A", "Sound without video, no re-encoding when possible", true),
    AUDIO_MP3("Audio only · MP3", "Sound without video, plays everywhere", true),
    FILE("File · image, PDF, any file", "Saves the link itself: pictures, PDFs, documents, archives", false);

    companion object {
        fun fromKey(key: String?): DownloadQuality = entries.firstOrNull { it.name == key } ?: BEST
    }
}

@Entity(tableName = "downloads", indices = [Index("status")])
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The saved link this came from, if any. Not a foreign key: deleting a link keeps its downloads. */
    val linkId: Long? = null,
    val url: String,
    val title: String,
    val quality: String,
    val status: String = DownloadStatus.QUEUED,
    /** content:// Uri of the saved file (or a FileProvider Uri on Android 9 and older). */
    val fileUri: String? = null,
    val error: String? = null,
    val sizeBytes: Long = 0,
    val createdAt: Long,
    val finishedAt: Long? = null
)

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun get(id: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE status = 'QUEUED' ORDER BY createdAt ASC, id ASC LIMIT 1")
    suspend fun nextQueued(): DownloadEntity?

    @Insert
    suspend fun insert(download: DownloadEntity): Long

    @Update
    suspend fun update(download: DownloadEntity)

    /** QUEUED -> RUNNING. Returns 0 if the row was cancelled or removed in the meantime. */
    @Query("UPDATE downloads SET status = 'RUNNING', error = NULL WHERE id = :id AND status = 'QUEUED'")
    suspend fun markRunning(id: Long): Int

    /** Only succeeds while the row is still RUNNING, so a cancel can never be overwritten by a late result. */
    @Query("UPDATE downloads SET status = 'DONE', fileUri = :uri, sizeBytes = :size, title = :title, finishedAt = :now WHERE id = :id AND status = 'RUNNING'")
    suspend fun markDone(id: Long, uri: String, size: Long, title: String, now: Long): Int

    @Query("UPDATE downloads SET status = 'FAILED', error = :error, finishedAt = :now WHERE id = :id AND status = 'RUNNING'")
    suspend fun markFailed(id: Long, error: String, now: Long): Int

    @Query("UPDATE downloads SET status = 'CANCELED', error = NULL, finishedAt = :now WHERE id = :id AND status IN ('QUEUED', 'RUNNING')")
    suspend fun markCanceled(id: Long, now: Long): Int

    /** FAILED/CANCELED -> QUEUED. */
    @Query("UPDATE downloads SET status = 'QUEUED', error = NULL, finishedAt = NULL WHERE id = :id AND status IN ('FAILED', 'CANCELED')")
    suspend fun requeue(id: Long): Int

    /** After the app was killed mid-download, nothing is really running any more. */
    @Query("UPDATE downloads SET status = 'FAILED', error = 'Interrupted. Tap retry to start again.', finishedAt = :now WHERE status = 'RUNNING'")
    suspend fun failInterrupted(now: Long)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM downloads WHERE status IN ('DONE', 'FAILED', 'CANCELED')")
    suspend fun clearFinished()
}
