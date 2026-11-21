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
    // Strictly enforce zero-history policy by wiping any legacy history rows on launch.
    historyDao.clearAll()
    if (bookmarkDao.count() == 0) {
      bookmarkDao.insertAll(
        listOf(
          BookmarkEntity(
            url = "https://duckduckgo.com",
            title = "DuckDuckGo",
            category = "Search",
            isSpeedDial = true,
            accentHex = "#FF6D00",
          ),
          BookmarkEntity(
            url = "https://en.m.wikipedia.org",
            title = "Wikipedia",
            category = "Reference",
            isSpeedDial = true,
            accentHex = "#00E5FF",
          ),
          BookmarkEntity(
            url = "https://github.com/trending",
            title = "GitHub",
            category = "Dev",
            isSpeedDial = true,
            accentHex = "#B388FF",
          ),
          BookmarkEntity(
            url = "https://archive.org/details/movies",
            title = "Archive Media",
            category = "Media",
            isSpeedDial = true,
            accentHex = "#FFB300",
          ),
          BookmarkEntity(
            url = "https://commons.wikimedia.org/wiki/Main_Page",
            title = "Wikimedia",
            category = "Media",
            isSpeedDial = true,
            accentHex = "#00E676",
          ),
          BookmarkEntity(
            url = "https://news.ycombinator.com",
            title = "Hacker News",
            category = "Tech",
            isSpeedDial = true,
            accentHex = "#FF7043",
          ),
          BookmarkEntity(
            url = "https://developer.android.com",
            title = "Android Dev",
            category = "Dev",
            isSpeedDial = true,
            accentHex = "#3DDC84",
          ),
          BookmarkEntity(
            url = "https://www.npr.org",
            title = "NPR Audio",
            category = "News",
            isSpeedDial = true,
            accentHex = "#38BDF8",
          ),
        )
      )
    }
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

  suspend fun deleteDownload(id: Long) = downloadDao.deleteById(id)

  suspend fun wipeAllPersistedData() {
    historyDao.clearAll()
    downloadDao.clearAll()
    bookmarkDao.clearAll()
    preferences.clearAll()
  }
}
