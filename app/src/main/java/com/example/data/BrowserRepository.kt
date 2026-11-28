package com.example.data

import kotlinx.coroutines.flow.Flow

/**
 * Single repository coordinating Room persistence (bookmarks, downloads, site permissions,
 * per-host overrides) and encrypted/typed DataStore preferences.
 */
class BrowserRepository(
  private val database: AppDatabase,
  val preferences: VoltPreferences,
) {
  private val bookmarkDao = database.bookmarkDao()
  private val historyDao = database.historyDao()
  private val downloadDao = database.downloadDao()
  private val sitePermissionDao = database.sitePermissionDao()
  private val siteSettingDao = database.siteSettingDao()

  val allBookmarks: Flow<List<BookmarkEntity>> = bookmarkDao.observeAllBookmarks()
  val speedDialBookmarks: Flow<List<BookmarkEntity>> = bookmarkDao.observeSpeedDial()
  val downloads: Flow<List<DownloadItemEntity>> = downloadDao.observeDownloads()
  val sitePermissions: Flow<List<SitePermissionEntity>> = sitePermissionDao.observeAll()
  val siteSettings: Flow<List<SiteSettingEntity>> = siteSettingDao.observeAll()
  val settings: Flow<BrowserSettings> = preferences.settings

  fun observeIsBookmarked(url: String): Flow<Boolean> = bookmarkDao.observeIsBookmarked(url)

  suspend fun initializeCleanSession() {
    // Strictly enforce zero-history policy by clearing legacy history entries on launch.
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

  suspend fun getDownloadById(id: Long): DownloadItemEntity? = downloadDao.getById(id)

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

  suspend fun getSitePermissionsSnapshot(): List<SitePermissionEntity> =
    sitePermissionDao.getAllSnapshot()

  suspend fun saveSitePermission(host: String, type: SitePermissionType, granted: Boolean) {
    if (host.isBlank()) return
    sitePermissionDao.upsert(
      SitePermissionEntity(
        host = host,
        permissionType = type.name,
        granted = granted,
        updatedAt = System.currentTimeMillis(),
      )
    )
  }

  suspend fun clearSitePermissionsForHost(host: String) {
    if (host.isNotBlank()) sitePermissionDao.deleteForHost(host)
  }

  suspend fun clearAllSitePermissions() {
    sitePermissionDao.clearAll()
  }

  suspend fun saveSiteJsOverride(host: String, jsEnabled: Boolean?) {
    if (host.isBlank()) return
    val current = siteSettingDao.getByHost(host)
    val desktop = current?.desktopMode
    if (jsEnabled == null && desktop == null) {
      siteSettingDao.deleteForHost(host)
    } else {
      siteSettingDao.upsert(
        SiteSettingEntity(
          host = host,
          jsEnabled = jsEnabled,
          desktopMode = desktop,
          updatedAt = System.currentTimeMillis(),
        )
      )
    }
  }

  suspend fun saveSiteDesktopOverride(host: String, desktopMode: Boolean?) {
    if (host.isBlank()) return
    val current = siteSettingDao.getByHost(host)
    val js = current?.jsEnabled
    if (js == null && desktopMode == null) {
      siteSettingDao.deleteForHost(host)
    } else {
      siteSettingDao.upsert(
        SiteSettingEntity(
          host = host,
          jsEnabled = js,
          desktopMode = desktopMode,
          updatedAt = System.currentTimeMillis(),
        )
      )
    }
  }

  suspend fun clearSiteSettingsForHost(host: String) {
    if (host.isNotBlank()) siteSettingDao.deleteForHost(host)
  }

  suspend fun clearAllSiteSettings() {
    siteSettingDao.clearAll()
  }

  suspend fun wipeAllPersistedData() {
    historyDao.clearAll()
    downloadDao.clearAll()
    bookmarkDao.clearAll()
    sitePermissionDao.clearAll()
    siteSettingDao.clearAll()
    preferences.clearAll()
  }
}
