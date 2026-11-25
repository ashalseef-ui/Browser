package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
  entities = [
    BookmarkEntity::class,
    HistoryEntryEntity::class,
    DownloadItemEntity::class,
    SitePermissionEntity::class,
  ],
  version = 2,
  exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun bookmarkDao(): BookmarkDao
  abstract fun historyDao(): HistoryDao
  abstract fun downloadDao(): DownloadDao
  abstract fun sitePermissionDao(): SitePermissionDao

  companion object {
    @Volatile private var INSTANCE: AppDatabase? = null

    private val MIGRATION_1_2 =
      object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `site_permissions` (
              `host` TEXT NOT NULL,
              `permissionType` TEXT NOT NULL,
              `granted` INTEGER NOT NULL,
              `updatedAt` INTEGER NOT NULL,
              PRIMARY KEY(`host`, `permissionType`)
            )
            """.trimIndent()
          )
        }
      }

    fun getInstance(context: Context): AppDatabase {
      return INSTANCE
        ?: synchronized(this) {
          INSTANCE
            ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "volt_browser.db",
              )
              .addMigrations(MIGRATION_1_2)
              .fallbackToDestructiveMigration(true)
              .build()
              .also { INSTANCE = it }
        }
    }
  }
}
