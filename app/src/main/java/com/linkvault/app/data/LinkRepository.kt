package com.linkvault.app.data

import android.content.Context
import androidx.room.withTransaction
import com.linkvault.app.util.UrlUtils
import com.linkvault.app.widget.PinnedWidgetProvider
import kotlinx.coroutines.flow.Flow

class LinkRepository(private val context: Context, private val db: AppDatabase) {
    private val links = db.linkDao()
    private val cats = db.categoryDao()

    val allLinks: Flow<List<LinkEntity>> = links.observeAll()
    val categories: Flow<List<CategoryEntity>> = cats.observeAll()

    fun observeLink(id: Long): Flow<LinkEntity?> = links.observe(id)
    suspend fun getLink(id: Long) = links.get(id)
    suspend fun findDuplicate(normalized: String, ignoreId: Long? = null): LinkEntity? =
        links.findByNormalized(normalized)?.takeIf { it.id != ignoreId }

    private fun refreshWidget() = PinnedWidgetProvider.requestUpdate(context)

    suspend fun save(link: LinkEntity): Long {
        val id = if (link.id == 0L) links.insert(link) else { links.update(link); link.id }
        refreshWidget()
        return id
    }

    suspend fun update(link: LinkEntity) { links.update(link); refreshWidget() }

    suspend fun delete(ids: List<Long>) {
        if (ids.isEmpty()) return
        ids.chunked(500).forEach { links.deleteByIds(it) }
        refreshWidget()
    }

    /** Re-inserts previously deleted links with their original ids (used by Undo). */
    suspend fun restore(deleted: List<LinkEntity>) {
        if (deleted.isEmpty()) return
        links.insertAll(deleted)
        refreshWidget()
    }

    suspend fun recordOpen(id: Long) = links.recordOpen(id, System.currentTimeMillis())

    suspend fun setFavorite(ids: List<Long>, value: Boolean) = mutate(ids) { it.copy(isFavorite = value) }
    suspend fun setPinned(ids: List<Long>, value: Boolean) = mutate(ids) { it.copy(isPinned = value) }
    suspend fun moveToCategory(ids: List<Long>, categoryId: Long?) = mutate(ids) { it.copy(categoryId = categoryId) }
    suspend fun addTag(ids: List<Long>, tag: String) {
        val t = Tags.clean(tag); if (t.isEmpty()) return
        mutate(ids) { it.copy(tags = Tags.join(it.tagList + t)) }
    }
    suspend fun removeTag(ids: List<Long>, tag: String) =
        mutate(ids) { l -> l.copy(tags = Tags.join(l.tagList.filterNot { it.equals(tag, true) })) }

    /** Renames or merges a tag across every link. */
    suspend fun renameTag(old: String, new: String) {
        val n = Tags.clean(new); if (n.isEmpty()) return
        val affected = links.getAll().filter { l -> l.tagList.any { it.equals(old, true) } }
        mutate(affected.map { it.id }) { l ->
            l.copy(tags = Tags.join(l.tagList.map { if (it.equals(old, true)) n else it }))
        }
    }

    suspend fun deleteTag(tag: String) {
        val affected = links.getAll().filter { l -> l.tagList.any { it.equals(tag, true) } }
        removeTagFrom(affected.map { it.id }, tag)
    }

    private suspend fun removeTagFrom(ids: List<Long>, tag: String) = removeTag(ids, tag)

    private suspend fun mutate(ids: List<Long>, f: (LinkEntity) -> LinkEntity) {
        if (ids.isEmpty()) return
        val now = System.currentTimeMillis()
        db.withTransaction {
            ids.chunked(500).forEach { chunk ->
                val updated = chunk.mapNotNull { links.get(it) }.map { f(it).copy(updatedAt = now) }
                links.updateAll(updated)
            }
        }
        refreshWidget()
    }

    // ---- Categories ----
    suspend fun addCategory(name: String, icon: String): Long =
        cats.insert(CategoryEntity(name = name.trim(), iconKey = icon, sortOrder = cats.maxOrder() + 1))

    suspend fun updateCategory(c: CategoryEntity) = cats.update(c)

    /** Links in a deleted category are kept and become uncategorised; they are never deleted. */
    suspend fun deleteCategory(id: Long) = db.withTransaction {
        links.clearCategory(id); cats.delete(id)
    }

    suspend fun reorderCategories(ordered: List<CategoryEntity>) =
        cats.updateAll(ordered.mapIndexed { i, c -> c.copy(sortOrder = i) })

    // ---- Export / import ----
    suspend fun exportJson(): String = Backup.toJson(cats.getAll(), links.getAll(), System.currentTimeMillis())
    suspend fun exportCsv(): String = Backup.toCsv(cats.getAll(), links.getAll())

    /** Parses first so a corrupt file can never touch existing data. */
    suspend fun importData(text: String, mode: ImportMode): ImportResult {
        val now = System.currentTimeMillis()
        val parsed = Backup.parse(text, now)
        var added = 0; var updated = 0; var skipped = 0
        db.withTransaction {
            if (mode == ImportMode.REPLACE) { links.deleteAll(); cats.deleteAll() }

            val existingCats = cats.getAll().toMutableList()
            fun categoryIdFor(name: String?, icon: String = "folder", order: Int = 0): Long? {
                if (name == null) return null
                return existingCats.firstOrNull { it.name.equals(name, true) }?.id
            }
            // Create any categories from the backup that don't exist yet.
            for (pc in parsed.categories.sortedBy { it.order }) {
                if (categoryIdFor(pc.name) == null) {
                    val id = cats.insert(CategoryEntity(name = pc.name, iconKey = pc.icon, sortOrder = cats.maxOrder() + 1))
                    existingCats += CategoryEntity(id, pc.name, pc.icon, existingCats.size)
                }
            }
            for (pl in parsed.links) {
                var catId = categoryIdFor(pl.categoryName)
                if (catId == null && pl.categoryName != null) {
                    val id = cats.insert(CategoryEntity(name = pl.categoryName, sortOrder = cats.maxOrder() + 1))
                    existingCats += CategoryEntity(id, pl.categoryName, "folder", existingCats.size)
                    catId = id
                }
                val incoming = pl.link.copy(categoryId = catId)
                val dup = links.findByNormalized(incoming.normalizedUrl)
                when {
                    dup == null -> { links.insert(incoming); added++ }
                    mode == ImportMode.MERGE_UPDATE_DUPLICATES -> {
                        links.update(
                            dup.copy(
                                title = incoming.title, categoryId = incoming.categoryId ?: dup.categoryId,
                                tags = Tags.join(dup.tagList + incoming.tagList),
                                notes = incoming.notes.ifBlank { dup.notes },
                                isFavorite = dup.isFavorite || incoming.isFavorite,
                                isPinned = dup.isPinned || incoming.isPinned,
                                openCount = maxOf(dup.openCount, incoming.openCount),
                                updatedAt = now
                            )
                        ); updated++
                    }
                    else -> skipped++
                }
            }
        }
        refreshWidget()
        return ImportResult(added, updated, skipped, parsed.invalid)
    }

    suspend fun pinnedSnapshot(limit: Int) = links.pinnedSnapshot(limit)
    fun normalize(url: String) = UrlUtils.normalizeString(url)
}
