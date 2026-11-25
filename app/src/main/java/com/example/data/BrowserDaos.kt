package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
  @Query("SELECT * FROM bookmarks ORDER BY isSpeedDial DESC, createdAt DESC")
  fun observeAllBookmarks(): Flow<List<BookmarkEntity>>

  @Query("SELECT * FROM bookmarks WHERE isSpeedDial = 1 ORDER BY createdAt ASC")
  fun observeSpeedDial(): Flow<List<BookmarkEntity>>

  @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE url = :url)")
  fun observeIsBookmarked(url: String): Flow<Boolean>

  @Query("SELECT COUNT(*) FROM bookmarks")
  suspend fun count(): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(bookmark: BookmarkEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAll(bookmarks: List<BookmarkEntity>)

  @Query("DELETE FROM bookmarks WHERE url = :url")
  suspend fun deleteByUrl(url: String)

  @Query("DELETE FROM bookmarks WHERE id = :id")
  suspend fun deleteById(id: Long)

  @Query("DELETE FROM bookmarks")
  suspend fun clearAll()
}

@Dao
interface HistoryDao {
  @Query("SELECT * FROM history_entries ORDER BY visitedAt DESC LIMIT 200")
  fun observeHistory(): Flow<List<HistoryEntryEntity>>

  @Query(
    "SELECT * FROM history_entries WHERE title LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%' ORDER BY visitedAt DESC LIMIT 8"
  )
  suspend fun searchSuggestions(query: String): List<HistoryEntryEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(entry: HistoryEntryEntity)

  @Query("DELETE FROM history_entries WHERE id = :id")
  suspend fun deleteById(id: Long)

  @Query("DELETE FROM history_entries")
  suspend fun clearAll()
}

@Dao
interface DownloadDao {
  @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
  fun observeDownloads(): Flow<List<DownloadItemEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(item: DownloadItemEntity): Long

  @Query(
    "UPDATE downloads SET downloadedBytes = :downloaded, totalBytes = :total, status = :status, localPath = :localPath WHERE id = :id"
  )
  suspend fun updateProgress(
    id: Long,
    downloaded: Long,
    total: Long,
    status: String,
    localPath: String,
  )

  @Query(
    "UPDATE downloads SET fileName = :fileName, mimeType = :mimeType, localPath = :localPath WHERE id = :id"
  )
  suspend fun updateMetadata(
    id: Long,
    fileName: String,
    mimeType: String,
    localPath: String,
  )

  @Query("DELETE FROM downloads WHERE id = :id")
  suspend fun deleteById(id: Long)

  @Query("DELETE FROM downloads")
  suspend fun clearAll()
}

@Dao
interface SitePermissionDao {
  @Query("SELECT * FROM site_permissions ORDER BY updatedAt DESC")
  fun observeAll(): Flow<List<SitePermissionEntity>>

  @Query("SELECT * FROM site_permissions")
  suspend fun getAllSnapshot(): List<SitePermissionEntity>

  @Query("SELECT granted FROM site_permissions WHERE host = :host AND permissionType = :permissionType LIMIT 1")
  suspend fun getDecision(host: String, permissionType: String): Boolean?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: SitePermissionEntity)

  @Query("DELETE FROM site_permissions WHERE host = :host")
  suspend fun deleteForHost(host: String)

  @Query("DELETE FROM site_permissions")
  suspend fun clearAll()
}
