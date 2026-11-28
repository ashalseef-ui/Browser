package com.example.ui.screens

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.DownloadItemEntity
import com.example.data.DownloadStatus
import com.example.ui.components.formatBytes
import com.example.ui.theme.LocalVoltColors

/**
 * Downloads tab inside Media Studio showing active progress, HTTP Range resume/retry controls,
 * notification permission banner, MediaStore Downloads export, Save As, Open, and Share.
 */
@Composable
internal fun MediaHubDownloadsTab(
  downloads: List<DownloadItemEntity>,
  hasNotificationPermission: Boolean,
  onRequestNotifications: () -> Unit,
  onCancelDownload: (Long) -> Unit,
  onRetryDownload: (DownloadItemEntity) -> Unit,
  onPlayDownload: (DownloadItemEntity) -> Unit,
  onOpenExternalDownload: (DownloadItemEntity) -> Unit,
  onOpenDownload: (DownloadItemEntity) -> Unit,
  onSaveAsClick: (DownloadItemEntity) -> Unit,
  onExportDownload: (DownloadItemEntity) -> Unit,
  onShareDownload: (DownloadItemEntity) -> Unit,
  onDeleteDownload: (DownloadItemEntity) -> Unit,
) {
  val voltColors = LocalVoltColors.current

  LazyColumn(
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    item {
      DeviceStoragePermissionCard(
        hasNotificationPermission = hasNotificationPermission,
        onRequestNotifications = onRequestNotifications,
      )
    }

    if (downloads.isEmpty()) {
      item {
        EmptyMediaState(
          title = "No downloads yet",
          subtitle =
            "Downloads support PDFs, ZIPs, APKs, documents, images, audio, and videos. Files auto-save to Downloads/VoltBrowser.",
        )
      }
    } else {
      items(downloads, key = { it.id }) { dl ->
        val isMedia = isPlayableMediaDownload(dl.fileName, dl.mimeType)
        val status =
          runCatching { DownloadStatus.valueOf(dl.status) }
            .getOrDefault(DownloadStatus.COMPLETED)

        Surface(
          shape = RoundedCornerShape(18.dp),
          color = voltColors.cardSurface,
          border = BorderStroke(1.dp, voltColors.cardBorder),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Box(
                modifier =
                  Modifier.size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(voltColors.brandAccent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = iconForDownloadFile(dl.fileName, dl.mimeType),
                  contentDescription = null,
                  tint = voltColors.brandAccent,
                  modifier = Modifier.size(18.dp),
                )
              }
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = dl.fileName,
                  style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.onSurface,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text =
                    when (status) {
                      DownloadStatus.DOWNLOADING ->
                        if (dl.totalBytes > 0) {
                          val pct = ((dl.downloadedBytes * 100) / dl.totalBytes).coerceIn(0, 100)
                          "Downloading $pct% • ${formatBytes(dl.downloadedBytes)}"
                        } else {
                          "Downloading • ${formatBytes(dl.downloadedBytes)}"
                        }
                      DownloadStatus.FAILED ->
                        if (dl.downloadedBytes > 0L) {
                          "Interrupted (${formatBytes(dl.downloadedBytes)} saved) • Tap Resume"
                        } else {
                          "Failed • Tap retry"
                        }
                      DownloadStatus.CANCELLED -> "Cancelled"
                      else ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                          "${formatBytes(dl.downloadedBytes)} • Saved to Downloads/VoltBrowser"
                        } else {
                          "${formatBytes(dl.downloadedBytes)} • Tap Save As to export"
                        }
                    },
                  style = MaterialTheme.typography.labelSmall,
                  color =
                    when (status) {
                      DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
                      DownloadStatus.COMPLETED -> voltColors.emeraldShield
                      else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
              }
              if (status == DownloadStatus.DOWNLOADING) {
                IconButton(onClick = { onCancelDownload(dl.id) }) {
                  Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel download",
                    tint = MaterialTheme.colorScheme.error,
                  )
                }
              }
              if (status == DownloadStatus.FAILED || status == DownloadStatus.CANCELLED) {
                IconButton(onClick = { onRetryDownload(dl) }) {
                  Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription =
                      if (status == DownloadStatus.FAILED && dl.downloadedBytes > 0L) "Resume download"
                      else "Retry download",
                    tint = voltColors.amberAccent,
                  )
                }
              }
              if (status == DownloadStatus.COMPLETED && isMedia) {
                IconButton(onClick = { onPlayDownload(dl) }) {
                  Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play downloaded media",
                    tint = voltColors.brandAccent,
                  )
                }
              }
              IconButton(onClick = { onDeleteDownload(dl) }) {
                Icon(
                  imageVector = Icons.Default.DeleteOutline,
                  contentDescription = "Delete download",
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }

            if (status == DownloadStatus.COMPLETED) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                if (isMedia) {
                  OutlinedButton(
                    onClick = { onOpenExternalDownload(dl) },
                    modifier = Modifier.weight(1f).testTag("download_external_button"),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                  ) {
                    Icon(
                      imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                      contentDescription = null,
                      modifier = Modifier.size(15.dp),
                      tint = voltColors.amberAccent,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("External", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                  }
                } else {
                  OutlinedButton(
                    onClick = { onOpenDownload(dl) },
                    modifier = Modifier.weight(1f).testTag("download_open_button"),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                  ) {
                    Icon(
                      imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                      contentDescription = null,
                      modifier = Modifier.size(15.dp),
                      tint = voltColors.amberAccent,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Open", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                  }
                }
                OutlinedButton(
                  onClick = { onSaveAsClick(dl) },
                  modifier = Modifier.weight(1f).testTag("save_as_button"),
                  contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                ) {
                  Icon(
                    imageVector = Icons.Default.DriveFileMove,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = voltColors.brandAccent,
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Save As", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                  OutlinedButton(
                    onClick = { onExportDownload(dl) },
                    modifier = Modifier.weight(1f).testTag("export_downloads_button"),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Default.SaveAlt,
                      contentDescription = null,
                      modifier = Modifier.size(15.dp),
                      tint = voltColors.emeraldShield,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Folder", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                  }
                }
                OutlinedButton(
                  onClick = { onShareDownload(dl) },
                  contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                ) {
                  Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share file",
                    modifier = Modifier.size(15.dp),
                  )
                }
              }
            }

            if (status == DownloadStatus.DOWNLOADING) {
              if (dl.totalBytes > 0) {
                LinearProgressIndicator(
                  progress = {
                    (dl.downloadedBytes.toFloat() / dl.totalBytes.toFloat()).coerceIn(0f, 1f)
                  },
                  modifier = Modifier.fillMaxWidth().height(4.dp),
                  color = voltColors.brandAccent,
                )
              } else {
                LinearProgressIndicator(
                  modifier = Modifier.fillMaxWidth().height(4.dp),
                  color = voltColors.brandAccent,
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun DeviceStoragePermissionCard(
  hasNotificationPermission: Boolean,
  onRequestNotifications: () -> Unit,
) {
  val voltColors = LocalVoltColors.current
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = voltColors.cardSurface,
    border = BorderStroke(1.dp, voltColors.cardBorder),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Icon(
          imageVector = Icons.Default.Storage,
          contentDescription = null,
          tint = voltColors.emeraldShield,
          modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text =
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "Auto-Saving to Device Downloads/VoltBrowser"
              } else {
                "Device Folder Export Ready"
              },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text =
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "Scoped MediaStore & Save As active — no broad storage permissions required."
              } else {
                "Tap Save As on any completed download to pick a public device folder."
              },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
        OutlinedButton(
          onClick = onRequestNotifications,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth().testTag("allow_notifications_button"),
        ) {
          Icon(
            imageVector = Icons.Default.NotificationsActive,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = voltColors.amberAccent,
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text("Allow Download Notifications")
        }
      }
    }
  }
}

private fun isPlayableMediaDownload(fileName: String, mimeType: String): Boolean {
  val lowerMime = mimeType.lowercase()
  if (lowerMime.startsWith("video/") || lowerMime.startsWith("audio/")) return true
  val ext = fileName.substringAfterLast('.', "").lowercase()
  return ext in setOf("mp4", "ts", "webm", "mkv", "mov", "avi", "m4v", "mp3", "m4a", "aac", "ogg", "opus", "flac", "wav")
}

private fun iconForDownloadFile(fileName: String, mimeType: String): ImageVector {
  val lowerMime = mimeType.lowercase()
  val ext = fileName.substringAfterLast('.', "").lowercase()
  return when {
    lowerMime.startsWith("video/") || ext in setOf("mp4", "ts", "webm", "mkv", "mov", "avi", "m4v") ->
      Icons.Default.VideoFile
    lowerMime.startsWith("audio/") || ext in setOf("mp3", "m4a", "aac", "ogg", "opus", "flac", "wav") ->
      Icons.Default.AudioFile
    lowerMime.startsWith("image/") || ext in setOf("png", "jpg", "jpeg", "webp", "gif", "svg", "avif") ->
      Icons.Default.Image
    ext == "apk" || lowerMime == "application/vnd.android.package-archive" ->
      Icons.Default.Android
    ext in setOf("zip", "7z", "rar", "tar", "gz") ->
      Icons.Default.FolderZip
    ext in setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "json", "xml", "epub", "conf") ->
      Icons.Default.Description
    else -> Icons.Default.InsertDriveFile
  }
}
