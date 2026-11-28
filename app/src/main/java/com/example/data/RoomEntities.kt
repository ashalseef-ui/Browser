package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Persisted user bookmark or Start Page Speed Dial workspace pin. */
@Entity(tableName = "bookmarks")
data class BookmarkEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val url: String,
  val title: String,
  val category: String = "General",
  val isSpeedDial: Boolean = false,
  val accentHex: String = "#00E5FF",
  val createdAt: Long = System.currentTimeMillis(),
)

/** Ephemeral history schema; cleared on startup and Exit & Erase for zero-history privacy. */
@Entity(tableName = "history_entries")
data class HistoryEntryEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val url: String,
  val title: String,
  val visitedAt: Long = System.currentTimeMillis(),
  val loadTimeMs: Long = 0L,
  val trackersBlocked: Int = 0,
)

/** Tracked file or adaptive-stream download record stored in Room. */
@Entity(tableName = "downloads")
data class DownloadItemEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val url: String,
  val fileName: String,
  val localPath: String = "",
  val mimeType: String = "application/octet-stream",
  val downloadedBytes: Long = 0L,
  val totalBytes: Long = -1L,
  val status: String = DownloadStatus.QUEUED.name,
  val createdAt: Long = System.currentTimeMillis(),
)

/** Per-origin site permission decision (Camera, Microphone, Location) for standard tabs. */
@Entity(
  tableName = "site_permissions",
  primaryKeys = ["host", "permissionType"],
)
data class SitePermissionEntity(
  val host: String,
  val permissionType: String,
  val granted: Boolean,
  val updatedAt: Long = System.currentTimeMillis(),
)

/** Per-host browser behavior overrides (JavaScript and Desktop Mode). Null inherits global default. */
@Entity(tableName = "site_settings")
data class SiteSettingEntity(
  @PrimaryKey val host: String,
  val jsEnabled: Boolean? = null,
  val desktopMode: Boolean? = null,
  val updatedAt: Long = System.currentTimeMillis(),
)
