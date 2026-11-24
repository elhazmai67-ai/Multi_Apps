package com.linkvault.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val iconKey: String = "folder",
    val sortOrder: Int = 0
)

/**
 * Tags are stored on the link as a delimited string like ",cma,revision," so that a tag can be
 * matched exactly with LIKE '%,cma,%' and searched together with the other text columns.
 * Use [tagList] / [Tags.join] instead of touching the raw string.
 */
@Entity(
    tableName = "links",
    indices = [Index("normalizedUrl"), Index("categoryId"), Index("createdAt")]
)
data class LinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val normalizedUrl: String,
    val title: String,
    val domain: String,
    val platform: String,
    val categoryId: Long? = null,
    val tags: String = "",
    val notes: String = "",
    val isFavorite: Boolean = false,
    val isPinned: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val lastOpenedAt: Long? = null,
    val openCount: Int = 0
) {
    val tagList: List<String> get() = Tags.split(tags)
}

object Tags {
    fun clean(raw: String): String =
        raw.trim().trimStart('#').replace(",", " ").replace(Regex("\\s+"), " ").trim()

    fun split(stored: String): List<String> =
        stored.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    fun join(tags: Collection<String>): String {
        val unique = LinkedHashMap<String, String>()
        tags.map { clean(it) }.filter { it.isNotEmpty() }.forEach { unique.putIfAbsent(it.lowercase(), it) }
        return if (unique.isEmpty()) "" else "," + unique.values.joinToString(",") + ","
    }
}

/** A tag with how many links use it. Derived from links; there is no separate tag table to get out of sync. */
data class TagCount(val name: String, val count: Int)
