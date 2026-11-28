package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Local Room database storing user bookmarks, Speed Dial pins, download jobs,
 * per-origin permissions, and per-host browser overrides.
 */
@Database(
  entities = [
    BookmarkEntity::class,
    HistoryEntryEntity::class,
    DownloadItemEntity::class,
    SitePermissionEntity::class,
    SiteSettingEntity::class,
  ],
  version = 3,
  exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun bookmarkDao(): BookmarkDao
  abstract fun historyDao(): HistoryDao
  abstract fun downloadDao(): DownloadDao
  abstract fun sitePermissionDao(): SitePermissionDao
  abstract fun siteSettingDao(): SiteSettingDao

  companion object {
    @Volatile private var INSTANCE: AppDatabase? = null

    internal val MIGRATION_1_2 =
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

    internal val MIGRATION_2_3 =
      object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `site_settings` (
              `host` TEXT NOT NULL,
              `jsEnabled` INTEGER,
              `desktopMode` INTEGER,
              `updatedAt` INTEGER NOT NULL,
              PRIMARY KEY(`host`)
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
              .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
              .fallbackToDestructiveMigration(true)
              .build()
              .also { INSTANCE = it }
        }
    }
  }
}
