package com.linkvault.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [LinkEntity::class, CategoryEntity::class, DownloadEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun linkDao(): LinkDao
    abstract fun categoryDao(): CategoryDao
    abstract fun downloadDao(): DownloadDao

    companion object {
        /** Default categories: name to icon key. Order here is the initial display order. */
        val DEFAULT_CATEGORIES = listOf(
            "Study" to "school",
            "Work" to "work",
            "Personal" to "person",
            "Social" to "group",
            "Shopping" to "shopping",
            "Entertainment" to "movie",
            "Tools" to "build",
            "Finance" to "finance",
            "Important" to "star",
            "Other" to "folder"
        )

        /** v1 -> v2: adds the downloads table. Existing links and categories are untouched. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `downloads` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `linkId` INTEGER, " +
                        "`url` TEXT NOT NULL, `title` TEXT NOT NULL, `quality` TEXT NOT NULL, " +
                        "`status` TEXT NOT NULL, `fileUri` TEXT, `error` TEXT, " +
                        "`sizeBytes` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `finishedAt` INTEGER)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_downloads_status` ON `downloads` (`status`)")
            }
        }

        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "linkvault.db")
                .addMigrations(MIGRATION_1_2)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        DEFAULT_CATEGORIES.forEachIndexed { index, (name, icon) ->
                            db.execSQL(
                                "INSERT INTO categories (name, iconKey, sortOrder) VALUES (?, ?, ?)",
                                arrayOf<Any>(name, icon, index)
                            )
                        }
                    }
                })
                // Deliberately no fallbackToDestructiveMigration(): saved links must never be wiped by an update.
                .build()
                .also { instance = it }
        }
    }
}
