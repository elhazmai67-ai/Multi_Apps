package com.linkvault.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LinkDao {
    @Query("SELECT * FROM links")
    fun observeAll(): Flow<List<LinkEntity>>

    @Query("SELECT * FROM links")
    suspend fun getAll(): List<LinkEntity>

    @Query("SELECT * FROM links WHERE id = :id")
    fun observe(id: Long): Flow<LinkEntity?>

    @Query("SELECT * FROM links WHERE id = :id")
    suspend fun get(id: Long): LinkEntity?

    @Query("SELECT * FROM links WHERE normalizedUrl = :normalized LIMIT 1")
    suspend fun findByNormalized(normalized: String): LinkEntity?

    @Insert
    suspend fun insert(link: LinkEntity): Long

    @Insert
    suspend fun insertAll(links: List<LinkEntity>)

    @Update
    suspend fun update(link: LinkEntity)

    @Update
    suspend fun updateAll(links: List<LinkEntity>)

    @Query("DELETE FROM links WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM links")
    suspend fun deleteAll()

    @Query("UPDATE links SET openCount = openCount + 1, lastOpenedAt = :time WHERE id = :id")
    suspend fun recordOpen(id: Long, time: Long)

    @Query("UPDATE links SET categoryId = NULL WHERE categoryId = :categoryId")
    suspend fun clearCategory(categoryId: Long)

    @Query("SELECT * FROM links WHERE isPinned = 1 ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun pinnedSnapshot(limit: Int): List<LinkEntity>
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, id ASC")
    suspend fun getAll(): List<CategoryEntity>

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Insert
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Update
    suspend fun update(category: CategoryEntity)

    @Update
    suspend fun updateAll(categories: List<CategoryEntity>)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM categories")
    suspend fun deleteAll()

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM categories")
    suspend fun maxOrder(): Int
}
