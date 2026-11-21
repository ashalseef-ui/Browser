package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

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

@Entity(tableName = "history_entries")
data class HistoryEntryEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val url: String,
  val title: String,
  val visitedAt: Long = System.currentTimeMillis(),
  val loadTimeMs: Long = 0L,
  val trackersBlocked: Int = 0,
)

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
