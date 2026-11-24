package com.linkvault.app.data

import com.linkvault.app.util.UrlResult
import com.linkvault.app.util.UrlUtils
import com.linkvault.app.util.Platform
import org.json.JSONArray
import org.json.JSONObject

enum class ImportMode { MERGE_SKIP_DUPLICATES, MERGE_UPDATE_DUPLICATES, REPLACE }

data class ImportResult(val added: Int, val updated: Int, val skipped: Int, val invalid: Int)

class BackupFormatException(message: String) : Exception(message)

data class BackupData(val categories: List<CategoryEntity>, val links: List<LinkEntity>, val categoryNameById: Map<Long, String>)

/** Backup file format. Links reference categories by name so a restore works on any device. */
object Backup {
    const val VERSION = 1

    fun toJson(categories: List<CategoryEntity>, links: List<LinkEntity>, exportedAt: Long): String {
        val names = categories.associate { it.id to it.name }
        val root = JSONObject()
        root.put("app", "LinkVault")
        root.put("version", VERSION)
        root.put("exportedAt", exportedAt)
        root.put("categories", JSONArray().apply {
            categories.forEach { c ->
                put(JSONObject().put("name", c.name).put("icon", c.iconKey).put("order", c.sortOrder))
            }
        })
        root.put("links", JSONArray().apply {
            links.forEach { l ->
                put(JSONObject().apply {
                    put("id", l.id)
                    put("url", l.url)
                    put("title", l.title)
                    put("category", l.categoryId?.let { names[it] } ?: JSONObject.NULL)
                    put("tags", JSONArray(l.tagList))
                    put("notes", l.notes)
                    put("favorite", l.isFavorite)
                    put("pinned", l.isPinned)
                    put("createdAt", l.createdAt)
                    put("updatedAt", l.updatedAt)
                    put("lastOpenedAt", l.lastOpenedAt ?: JSONObject.NULL)
                    put("openCount", l.openCount)
                })
            }
        })
        return root.toString(2)
    }

    fun toCsv(categories: List<CategoryEntity>, links: List<LinkEntity>): String {
        val names = categories.associate { it.id to it.name }
        val sb = StringBuilder("id,url,title,category,tags,notes,favorite,pinned,created_at,last_opened_at,open_count\n")
        links.forEach { l ->
            sb.append(
                listOf(
                    l.id.toString(), l.url, l.title, l.categoryId?.let { names[it] }.orEmpty(),
                    l.tagList.joinToString(";"), l.notes, l.isFavorite.toString(), l.isPinned.toString(),
                    l.createdAt.toString(), l.lastOpenedAt?.toString().orEmpty(), l.openCount.toString()
                ).joinToString(",") { csv(it) }
            ).append('\n')
        }
        return sb.toString()
    }

    private fun csv(v: String): String {
        // Prefix formula-looking cells so spreadsheets don't execute them.
        val safe = if (v.isNotEmpty() && v[0] in "=+@" ) "'$v" else v
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    /** Parses a backup. Throws [BackupFormatException] with a human-readable message. */
    fun parse(text: String, now: Long): ParsedBackup {
        val root = try { JSONObject(text.trim().removePrefix("﻿")) } catch (e: Exception) {
            throw BackupFormatException("This file isn't a valid LinkVault backup.")
        }
        val linksJson = root.optJSONArray("links")
            ?: throw BackupFormatException("This backup file doesn't contain any links section.")
        val cats = mutableListOf<ParsedCategory>()
        root.optJSONArray("categories")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.optString("name").trim()
                if (name.isNotEmpty()) cats += ParsedCategory(name, o.optString("icon", "folder"), o.optInt("order", i))
            }
        }
        val links = mutableListOf<ParsedLink>()
        var invalid = 0
        for (i in 0 until linksJson.length()) {
            val o = linksJson.optJSONObject(i)
            val rawUrl = o?.optString("url").orEmpty()
            val parsed = (UrlUtils.parse(rawUrl) as? UrlResult.Ok)?.parsed
            if (o == null || parsed == null) { invalid++; continue }
            val tagsArr = o.optJSONArray("tags")
            val tags = if (tagsArr != null) (0 until tagsArr.length()).map { tagsArr.optString(it) } else Tags.split(o.optString("tags"))
            val created = o.optLong("createdAt", now).takeIf { it > 0 } ?: now
            links += ParsedLink(
                LinkEntity(
                    url = parsed.url, normalizedUrl = parsed.normalized,
                    title = o.optString("title").ifBlank { parsed.suggestedTitle },
                    domain = parsed.domain, platform = parsed.platform.name,
                    categoryId = null, tags = Tags.join(tags), notes = o.optString("notes"),
                    isFavorite = o.optBoolean("favorite"), isPinned = o.optBoolean("pinned"),
                    createdAt = created, updatedAt = o.optLong("updatedAt", created),
                    lastOpenedAt = if (o.isNull("lastOpenedAt")) null else o.optLong("lastOpenedAt").takeIf { it > 0 },
                    openCount = o.optInt("openCount", 0).coerceAtLeast(0)
                ),
                categoryName = if (o.isNull("category")) null else o.optString("category").trim().ifEmpty { null }
            )
        }
        if (links.isEmpty() && invalid == 0 && cats.isEmpty()) throw BackupFormatException("This backup file is empty.")
        return ParsedBackup(cats, links, invalid)
    }
}

data class ParsedCategory(val name: String, val icon: String, val order: Int)
data class ParsedLink(val link: LinkEntity, val categoryName: String?)
data class ParsedBackup(val categories: List<ParsedCategory>, val links: List<ParsedLink>, val invalid: Int)
