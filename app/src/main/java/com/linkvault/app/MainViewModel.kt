package com.linkvault.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.linkvault.app.data.*
import com.linkvault.app.download.DownloadCenter
import com.linkvault.app.download.DownloadEngine
import com.linkvault.app.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class SortOrder(val label: String) {
    RECENT("Recently added"), OLDEST("Oldest"), ALPHA("Alphabetical"),
    MOST_USED("Most used"), FAVORITES("Favorites first"), PINNED("Pinned first")
}

data class Filters(
    val query: String = "",
    val categoryId: Long? = null,
    val platform: Platform? = null,
    val tag: String? = null,
    val favoritesOnly: Boolean = false,
    val pinnedOnly: Boolean = false,
    val sort: SortOrder = SortOrder.RECENT
) {
    val isActive get() = query.isNotBlank() || categoryId != null || platform != null || tag != null || favoritesOnly || pinnedOnly
    val activeCount get() = listOf(categoryId != null, platform != null, tag != null, favoritesOnly, pinnedOnly, sort != SortOrder.RECENT).count { it }
}

const val UNCATEGORIZED = -1L

data class Stats(val total: Int = 0, val categories: Int = 0, val favorites: Int = 0)

data class DeadLink(val link: LinkEntity, val reason: String)

data class ScanState(
    val total: Int = 0, val done: Int = 0, val dead: List<DeadLink> = emptyList(), val unchecked: Int = 0,
    val running: Boolean = false, val finished: Boolean = false, val offline: Boolean = false
)

data class UiMessage(val text: String, val actionLabel: String? = null, val action: (() -> Unit)? = null)

sealed class PendingNav {
    data class Add(val url: String?) : PendingNav()
    data object Search : PendingNav()
    data object Favorites : PendingNav()
    data object Pinned : PendingNav()
    data object Downloads : PendingNav()
}

sealed class SaveOutcome {
    data class Saved(val id: Long) : SaveOutcome()
    data class Duplicate(val existing: LinkEntity) : SaveOutcome()
    data class Invalid(val message: String) : SaveOutcome()
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    val repo = LinkRepository(app, db)
    private val settingsStore = SettingsStore(app)

    val settings: StateFlow<Settings?> = settingsStore.flow.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val categories: StateFlow<List<CategoryEntity>> =
        repo.categories.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val allLinks: StateFlow<List<LinkEntity>> =
        repo.allLinks.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val downloads: StateFlow<List<DownloadEntity>> =
        db.downloadDao().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val stats: StateFlow<Stats> = combine(allLinks, categories) { l, c ->
        Stats(l.size, c.size, l.count { it.isFavorite })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Stats())

    val tagCounts: StateFlow<List<TagCount>> = allLinks.map { links ->
        val map = LinkedHashMap<String, Pair<String, Int>>()
        links.forEach { l -> l.tagList.forEach { t ->
            val k = t.lowercase(); val cur = map[k]; map[k] = (cur?.first ?: t) to ((cur?.second ?: 0) + 1)
        } }
        map.values.map { TagCount(it.first, it.second) }.sortedWith(compareByDescending<TagCount> { it.count }.thenBy { it.name.lowercase() })
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Filters for the two tab screens live here so they survive navigation.
    var homeFilters by mutableStateOf(Filters())
    var favoritesFilters by mutableStateOf(Filters(favoritesOnly = true))

    val messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    var pendingNav by mutableStateOf<PendingNav?>(null)

    // ---- Lock state ----
    var unlocked by mutableStateOf(false)
    private var backgroundedAt = 0L
    fun onBackgrounded() { backgroundedAt = System.currentTimeMillis() }
    fun onForegrounded() {
        if (backgroundedAt > 0 && System.currentTimeMillis() - backgroundedAt > LOCK_GRACE_MS) unlocked = false
        backgroundedAt = 0
    }

    // ---- Clipboard suggestion ----
    var dismissedClip by mutableStateOf<String?>(null)

    // ---- Querying ----
    @OptIn(ExperimentalCoroutinesApi::class)
    fun results(filters: Filters): Flow<List<LinkEntity>> =
        combine(allLinks, categories) { links, cats -> links to cats }
            .mapLatest { (links, cats) -> applyFilters(links, cats, filters) }
            .flowOn(Dispatchers.Default)

    private fun applyFilters(links: List<LinkEntity>, cats: List<CategoryEntity>, f: Filters): List<LinkEntity> {
        val catName = cats.associate { it.id to it.name.lowercase() }
        val tokens = f.query.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val tag = f.tag?.lowercase()?.let { ",$it," }
        val filtered = links.filter { l ->
            (f.categoryId == null || (f.categoryId == UNCATEGORIZED && l.categoryId == null) || l.categoryId == f.categoryId) &&
                (f.platform == null || l.platform == f.platform.name) &&
                (!f.favoritesOnly || l.isFavorite) &&
                (!f.pinnedOnly || l.isPinned) &&
                (tag == null || l.tags.lowercase().contains(tag)) &&
                (tokens.isEmpty() || haystack(l, catName[l.categoryId]).let { h -> tokens.all { it in h } })
        }
        return when (f.sort) {
            SortOrder.RECENT -> filtered.sortedByDescending { it.createdAt }
            SortOrder.OLDEST -> filtered.sortedBy { it.createdAt }
            SortOrder.ALPHA -> filtered.sortedBy { it.title.lowercase() }
            SortOrder.MOST_USED -> filtered.sortedWith(compareByDescending<LinkEntity> { it.openCount }.thenByDescending { it.lastOpenedAt ?: 0L })
            SortOrder.FAVORITES -> filtered.sortedWith(compareByDescending<LinkEntity> { it.isFavorite }.thenByDescending { it.createdAt })
            SortOrder.PINNED -> filtered.sortedWith(compareByDescending<LinkEntity> { it.isPinned }.thenByDescending { it.createdAt })
        }
    }

    private fun haystack(l: LinkEntity, category: String?): String = buildString {
        append(l.title.lowercase()); append('\n')
        append(l.url.lowercase()); append('\n')
        append(l.domain.lowercase()); append('\n')
        append(Platform.fromKey(l.platform).label.lowercase()); append('\n')
        append(category.orEmpty()); append('\n')
        append(l.tags.lowercase()); append('\n')
        append(l.notes.lowercase())
    }

    // ---- Actions ----
    fun toast(text: String) { messages.tryEmit(UiMessage(text)) }

    fun open(link: LinkEntity, context: android.content.Context) {
        val mode = settings.value?.openMode ?: OpenMode.SMART
        val ok = LinkOpener.open(context, link.url, Platform.fromKey(link.platform), mode)
        if (ok) viewModelScope.launch { repo.recordOpen(link.id) }
        else toast("Couldn't open this link. Check that it's a valid address.")
    }

    fun toggleFavorite(l: LinkEntity) = viewModelScope.launch { repo.setFavorite(listOf(l.id), !l.isFavorite) }
    fun togglePinned(l: LinkEntity) = viewModelScope.launch { repo.setPinned(listOf(l.id), !l.isPinned) }

    fun delete(ids: List<Long>) {
        val snapshot = allLinks.value.filter { it.id in ids.toSet() }
        viewModelScope.launch {
            repo.delete(ids)
            messages.tryEmit(
                UiMessage(if (ids.size == 1) "Link deleted" else "${ids.size} links deleted", "Undo") {
                    viewModelScope.launch { repo.restore(snapshot) }
                }
            )
        }
    }

    /**
     * Validates and saves. If [editingId] is null and the URL already exists, returns Duplicate
     * unless [force] is set.
     */
    suspend fun saveLink(
        editingId: Long?, rawUrl: String, title: String, categoryId: Long?, tags: List<String>,
        notes: String, favorite: Boolean, pinned: Boolean, force: Boolean
    ): SaveOutcome {
        val parsed = when (val r = UrlUtils.parse(rawUrl)) {
            is UrlResult.Error -> return SaveOutcome.Invalid(r.message)
            is UrlResult.Ok -> r.parsed
        }
        if (!force) repo.findDuplicate(parsed.normalized, ignoreId = editingId)?.let { return SaveOutcome.Duplicate(it) }
        val now = System.currentTimeMillis()
        val existing = editingId?.let { repo.getLink(it) }
        val entity = (existing ?: LinkEntity(
            url = parsed.url, normalizedUrl = parsed.normalized, title = "", domain = parsed.domain,
            platform = parsed.platform.name, createdAt = now, updatedAt = now
        )).copy(
            url = parsed.url, normalizedUrl = parsed.normalized, domain = parsed.domain,
            platform = parsed.platform.name,
            title = title.trim().ifEmpty { parsed.suggestedTitle },
            categoryId = categoryId, tags = Tags.join(tags), notes = notes.trim(),
            isFavorite = favorite, isPinned = pinned, updatedAt = now
        )
        val id = repo.save(entity)
        if (parsed.platform == Platform.WEB && settings.value?.fetchTitles != false) {
            viewModelScope.launch { MetadataFetcher.fetchFavicon(getApplication(), parsed.domain) }
        }
        return SaveOutcome.Saved(id)
    }

    // ---- Clean up: dead link scan ----
    var scanState by mutableStateOf(ScanState())
        private set
    private var scanJob: Job? = null

    fun startScan() {
        if (scanJob?.isActive == true) return
        val links = allLinks.value
        scanState = ScanState(total = links.size, running = true)
        scanJob = viewModelScope.launch {
            val online = withContext(Dispatchers.IO) { LinkChecker.isOnline() }
            if (!online) { scanState = ScanState(offline = true, finished = true); return@launch }
            val gate = Semaphore(6)
            val lock = Mutex()
            coroutineScope {
                links.map { l ->
                    async(Dispatchers.IO) {
                        val r = gate.withPermit { LinkChecker.check(l.url, Platform.fromKey(l.platform)) }
                        lock.withLock {
                            val s = scanState
                            scanState = s.copy(
                                done = s.done + 1,
                                dead = if (r.health == LinkHealth.DEAD) s.dead + DeadLink(l, r.reason) else s.dead,
                                unchecked = s.unchecked + if (r.health == LinkHealth.UNKNOWN) 1 else 0
                            )
                        }
                    }
                }.awaitAll()
            }
            scanState = scanState.copy(running = false, finished = true)
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        scanState = scanState.copy(running = false, finished = true)
    }

    // ---- Downloads ----
    /** Queues a download. [link] is the saved link it came from, or null for a pasted URL. */
    fun download(link: LinkEntity?, url: String, title: String, quality: DownloadQuality) {
        viewModelScope.launch {
            try {
                DownloadCenter.enqueue(getApplication(), link?.id, url, title, quality)
            } catch (e: Exception) {
                toast("Couldn't start the download.")
            }
        }
    }

    fun cancelDownload(id: Long) = viewModelScope.launch { DownloadCenter.cancel(getApplication(), id) }
    fun retryDownload(id: Long) = viewModelScope.launch { DownloadCenter.retry(getApplication(), id) }
    fun removeDownload(id: Long, deleteFile: Boolean) = viewModelScope.launch { DownloadCenter.remove(getApplication(), id, deleteFile) }
    fun clearFinishedDownloads() = viewModelScope.launch { DownloadCenter.clearFinished(getApplication()) }

    fun updateDownloader() {
        viewModelScope.launch {
            toast("Updating yt-dlp…")
            toast(try { DownloadEngine.update(getApplication()) } catch (e: Exception) { "Update failed: " + DownloadEngine.friendlyError(e) })
        }
    }

    // ---- Settings ----
    fun setTheme(t: ThemeMode) = viewModelScope.launch { settingsStore.setTheme(t) }
    fun setOpenMode(m: OpenMode) = viewModelScope.launch { settingsStore.setOpenMode(m) }
    fun setClipboardSuggestions(v: Boolean) = viewModelScope.launch { settingsStore.setClipboardSuggestions(v) }
    fun setShowMostUsed(v: Boolean) = viewModelScope.launch { settingsStore.setShowMostUsed(v) }
    fun setFetchTitles(v: Boolean) = viewModelScope.launch { settingsStore.setFetchTitles(v) }
    fun setDownloadQuality(q: DownloadQuality) = viewModelScope.launch { settingsStore.setDownloadQuality(q) }
    fun setFloatingButton(v: Boolean) = viewModelScope.launch { settingsStore.setFloatingButton(v) }
    fun setBiometric(v: Boolean) = viewModelScope.launch { settingsStore.setBiometric(v) }
    fun markBackup() = viewModelScope.launch { settingsStore.setLastBackup(System.currentTimeMillis()) }

    fun enableLock(pin: String) = viewModelScope.launch {
        val salt = PinHasher.newSalt()
        val hash = kotlinx.coroutines.withContext(Dispatchers.Default) { PinHasher.hash(pin, salt) }
        settingsStore.enableLock(hash, salt)
        unlocked = true
    }

    fun disableLock() = viewModelScope.launch { settingsStore.disableLock() }

    suspend fun verifyPin(pin: String): Boolean {
        val s = settings.value ?: return false
        return kotlinx.coroutines.withContext(Dispatchers.Default) { PinHasher.verify(pin, s.pinSalt, s.pinHash) }
    }

    companion object { const val LOCK_GRACE_MS = 30_000L }
}
