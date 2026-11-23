package com.example.data

import kotlinx.coroutines.flow.Flow

class BrowserRepository(
  private val database: AppDatabase,
  val preferences: VoltPreferences,
) {
  private val bookmarkDao = database.bookmarkDao()
  private val historyDao = database.historyDao()
  private val downloadDao = database.downloadDao()

  val allBookmarks: Flow<List<BookmarkEntity>> = bookmarkDao.observeAllBookmarks()
  val speedDialBookmarks: Flow<List<BookmarkEntity>> = bookmarkDao.observeSpeedDial()
  val downloads: Flow<List<DownloadItemEntity>> = downloadDao.observeDownloads()
  val settings: Flow<BrowserSettings> = preferences.settings

  fun observeIsBookmarked(url: String): Flow<Boolean> = bookmarkDao.observeIsBookmarked(url)

  suspend fun initializeCleanSession() {
    // Strictly enforce zero-history & zero-link policy by wiping any legacy history or default tiles on launch.
    historyDao.clearAll()
  }

  suspend fun toggleBookmark(url: String, title: String, isCurrentlyBookmarked: Boolean) {
    if (url.isBlank()) return
    if (isCurrentlyBookmarked) {
      bookmarkDao.deleteByUrl(url)
    } else {
      bookmarkDao.insert(
        BookmarkEntity(
          url = url,
          title = title.ifBlank { url },
          category = "Saved",
          isSpeedDial = false,
        )
      )
    }
  }

  suspend fun addSpeedDial(url: String, title: String) {
    bookmarkDao.insert(
      BookmarkEntity(
        url = url,
        title = title.ifBlank { url },
        category = "Speed Dial",
        isSpeedDial = true,
      )
    )
  }

  suspend fun deleteBookmark(id: Long) = bookmarkDao.deleteById(id)

  suspend fun insertDownload(item: DownloadItemEntity): Long = downloadDao.insert(item)

  suspend fun updateDownloadProgress(
    id: Long,
    downloaded: Long,
    total: Long,
    status: DownloadStatus,
    localPath: String,
  ) {
    downloadDao.updateProgress(id, downloaded, total, status.name, localPath)
  }

  suspend fun updateDownloadMetadata(
    id: Long,
    fileName: String,
    mimeType: String,
    localPath: String,
  ) {
    downloadDao.updateMetadata(id, fileName, mimeType, localPath)
  }

  suspend fun deleteDownload(id: Long) = downloadDao.deleteById(id)

  suspend fun wipeAllPersistedData() {
    historyDao.clearAll()
    downloadDao.clearAll()
    bookmarkDao.clearAll()
    preferences.clearAll()
  }
}
