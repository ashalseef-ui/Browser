package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.DetectedMediaItem
import com.example.data.DownloadItemEntity
import com.example.data.DownloadStatus
import com.example.data.MediaKind
import com.example.ui.components.formatBytes
import com.example.ui.components.formatDurationMs
import com.example.ui.theme.LocalVoltColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MediaHubSheet(
  detectedMedia: List<DetectedMediaItem>,
  isScanningMedia: Boolean,
  downloads: List<DownloadItemEntity>,
  onRescanPage: () -> Unit,
  onResolvePlaylistUrl: (String) -> Unit,
  onRequerySizes: () -> Unit,
  onPlayDetected: (DetectedMediaItem) -> Unit,
  onOpenExternalDetected: (DetectedMediaItem) -> Unit,
  onDownloadDetected: (DetectedMediaItem) -> Unit,
  onDownloadDirectUrl: (String) -> Unit,
  onStreamDirectUrl: (String) -> Unit,
  onOpenExternalDirectUrl: (String) -> Unit,
  onCancelDownload: (Long) -> Unit,
  onRetryDownload: (DownloadItemEntity) -> Unit,
  onPlayDownload: (DownloadItemEntity) -> Unit,
  onOpenExternalDownload: (DownloadItemEntity) -> Unit,
  onOpenDownload: (DownloadItemEntity) -> Unit,
  onExportDownload: (DownloadItemEntity) -> Unit,
  onSaveDownloadAs: (DownloadItemEntity, Uri) -> Unit,
  onShareDownload: (DownloadItemEntity) -> Unit,
  onDeleteDownload: (DownloadItemEntity) -> Unit,
  onPlayLocalUri: (Uri, String, Boolean) -> Unit,
  onLongPressDetectedMedia: (DetectedMediaItem) -> Unit = {},
  onCopyUrlToast: () -> Unit,
  onDismiss: () -> Unit,
) {
  val voltColors = LocalVoltColors.current
  val context = LocalContext.current
  val clipboard = LocalClipboardManager.current
  val haptics = LocalHapticFeedback.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var selectedTab by remember { mutableIntStateOf(0) }
  var directUrlInput by remember { mutableStateOf("") }
  var playlistInput by remember { mutableStateOf("") }
  var pendingSaveAsItem by remember { mutableStateOf<DownloadItemEntity?>(null) }

  var hasNotificationPermission by remember {
    mutableStateOf(
      Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
          PackageManager.PERMISSION_GRANTED
    )
  }

  val notificationPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasNotificationPermission = granted
    }

  val saveAsLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
      val item = pendingSaveAsItem
      pendingSaveAsItem = null
      if (uri != null && item != null) {
        onSaveDownloadAs(item, uri)
      }
    }

  val videoPicker =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
      if (uri != null) {
        val name = queryDisplayName(context, uri) ?: "Local Video"
        onPlayLocalUri(uri, name, true)
      }
    }

  val audioDocumentPicker =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
      if (uri != null) {
        val name = queryDisplayName(context, uri) ?: "Local Media"
        val mime = context.contentResolver.getType(uri).orEmpty()
        onPlayLocalUri(uri, name, mime.startsWith("video/"))
      }
    }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().heightIn(min = 440.dp, max = 690.dp).padding(bottom = 16.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Video Sniffer & Media Hub",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = "Downloadable videos > 1 MB • Media3 OkHttp & Transformer",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          if (detectedMedia.isNotEmpty()) {
            Surface(
              shape = RoundedCornerShape(100.dp),
              color = voltColors.emeraldShield.copy(alpha = 0.14f),
              modifier =
                Modifier.minimumInteractiveComponentSize()
                  .clip(RoundedCornerShape(100.dp))
                  .clickable(onClick = onRequerySizes)
                  .testTag("requery_sizes_button"),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.Refresh,
                  contentDescription = "Query file sizes",
                  tint = voltColors.emeraldShield,
                  modifier = Modifier.size(15.dp),
                )
                Text(
                  text = "Sizes",
                  style = MaterialTheme.typography.labelMedium,
                  color = voltColors.emeraldShield,
                )
              }
            }
          }
          Surface(
            shape = RoundedCornerShape(100.dp),
            color = voltColors.cyanAccent.copy(alpha = 0.14f),
            modifier =
              Modifier.minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(100.dp))
                .clickable(onClick = onRescanPage)
                .testTag("rescan_media_button"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Icon(
                imageVector = Icons.Default.Radar,
                contentDescription = "Scan page for videos",
                tint = voltColors.cyanAccent,
                modifier = Modifier.size(15.dp),
              )
              Text(
                text = "Scan",
                style = MaterialTheme.typography.labelMedium,
                color = voltColors.cyanAccent,
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      TabRow(selectedTabIndex = selectedTab, containerColor = Color.Transparent) {
        Tab(
          selected = selectedTab == 0,
          onClick = { selectedTab = 0 },
          text = { Text("Sniffer (${detectedMedia.size})") },
        )
        Tab(
          selected = selectedTab == 1,
          onClick = { selectedTab = 1 },
          text = { Text("Downloads (${downloads.size})") },
        )
        Tab(
          selected = selectedTab == 2,
          onClick = { selectedTab = 2 },
          text = { Text("Player / URL") },
        )
      }

      when (selectedTab) {
        0 -> {
          LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            // Direct Playlist / Video Resolver Bar
            item {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                OutlinedTextField(
                  value = playlistInput,
                  onValueChange = { playlistInput = it },
                  label = { Text("Resolve .m3u8, .mpd, .m3u, .pls, .xspf or .mp4 URL") },
                  singleLine = true,
                  modifier = Modifier.weight(1f).testTag("playlist_resolver_input"),
                )
                Button(
                  onClick = {
                    onResolvePlaylistUrl(playlistInput)
                    playlistInput = ""
                  },
                  enabled = playlistInput.trim().startsWith("http", ignoreCase = true),
                  modifier = Modifier.height(52.dp).testTag("playlist_resolve_button"),
                  colors =
                    ButtonDefaults.buttonColors(
                      containerColor = voltColors.cyanAccent,
                      contentColor = Color(0xFF002026),
                    ),
                ) {
                  Text("Resolve")
                }
              }
            }

            if (isScanningMedia) {
              item {
                Surface(
                  shape = RoundedCornerShape(12.dp),
                  color = voltColors.cyanAccent.copy(alpha = 0.12f),
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                  ) {
                    CircularProgressIndicator(
                      modifier = Modifier.size(16.dp),
                      strokeWidth = 2.dp,
                      color = voltColors.cyanAccent,
                    )
                    Text(
                      text = "Resolving video links & checking sizes (> 1 MB only)...",
                      style = MaterialTheme.typography.labelMedium,
                      color = voltColors.cyanAccent,
                    )
                  }
                }
              }
            }

            if (detectedMedia.isEmpty()) {
              item {
                EmptyMediaState(
                  title = if (isScanningMedia) "Checking videos (> 1 MB) on this page..." else "No downloadable videos > 1 MB found yet",
                  subtitle =
                    "Only verified downloadable videos larger than 1 MB appear here. Browse any video page or paste a .m3u8, .mpd, .m3u, .pls, or .mp4 URL above.",
                )
              }
            } else {
              items(detectedMedia, key = { it.url }) { item ->
                val kindBadge =
                  item.resolutionLabel ?: when (item.kind) {
                    MediaKind.STREAM_HLS -> "HLS"
                    MediaKind.STREAM_DASH -> "DASH"
                    MediaKind.PLAYLIST -> "PLAYLIST"
                    MediaKind.AUDIO -> "AUDIO"
                    MediaKind.VIDEO -> "VIDEO"
                  }

                val sizeText =
                  when {
                    item.isResolvingMetadata -> "Querying size..."
                    item.contentSizeBytes != null -> {
                      val formatted = formatBytes(item.contentSizeBytes)
                      if (item.isSizeEstimated) "~$formatted est." else formatted
                    }
                    else -> "Size unknown"
                  }

                val durationLabel =
                  item.durationSeconds?.takeIf { it > 0L }?.let { formatDurationMs(it * 1000L) }
                val segLabel =
                  item.segmentCount?.takeIf { it > 0 }?.let { "$it segments" }
                val isPlayable =
                  item.kind != MediaKind.PLAYLIST && !item.url.contains("#dash_")

                Surface(
                  shape = RoundedCornerShape(16.dp),
                  color = voltColors.cardSurface,
                  border = androidx.compose.foundation.BorderStroke(1.dp, voltColors.cardBorder),
                  modifier =
                    Modifier.fillMaxWidth()
                      .clip(RoundedCornerShape(16.dp))
                      .combinedClickable(
                        onClick = { if (isPlayable) onPlayDetected(item) },
                        onLongClick = {
                          haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                          onLongPressDetectedMedia(item)
                        },
                      ),
                ) {
                  Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    // Top Row: Type icon + readable 2-line Title & clean host/file line + discreet Copy button
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      verticalAlignment = Alignment.Top,
                      horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                      Box(
                        modifier =
                          Modifier.size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                              if (item.parentPlaylistUrl != null) voltColors.cyanAccent.copy(alpha = 0.16f)
                              else voltColors.amberAccent.copy(alpha = 0.16f)
                            ),
                        contentAlignment = Alignment.Center,
                      ) {
                        Icon(
                          imageVector =
                            when (item.kind) {
                              MediaKind.AUDIO -> Icons.Default.AudioFile
                              MediaKind.PLAYLIST -> Icons.Default.PlaylistPlay
                              else -> Icons.Default.VideoFile
                            },
                          contentDescription = null,
                          tint =
                            if (item.parentPlaylistUrl != null) voltColors.cyanAccent
                            else voltColors.amberAccent,
                          modifier = Modifier.size(19.dp),
                        )
                      }
                      Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                      ) {
                        Text(
                          text = item.title,
                          style = MaterialTheme.typography.titleSmall,
                          color = MaterialTheme.colorScheme.onSurface,
                          maxLines = 2,
                          overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                          text = formatCompactMediaUrl(item.url),
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis,
                        )
                      }
                      Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                        modifier =
                          Modifier.minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                              clipboard.setText(AnnotatedString(item.url))
                              onCopyUrlToast()
                            },
                      ) {
                        Box(contentAlignment = Alignment.Center) {
                          Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy media URL",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp),
                          )
                        }
                      }
                    }

                    // Middle Metadata Row: Clear, non-clipped chips for Format/Quality, Size, Duration/Segments
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                      Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = voltColors.cyanAccent.copy(alpha = 0.16f),
                      ) {
                        Text(
                          text = kindBadge.uppercase(),
                          style = MaterialTheme.typography.labelSmall,
                          color = voltColors.cyanAccent,
                          modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        )
                      }
                      Surface(
                        shape = RoundedCornerShape(6.dp),
                        color =
                          if (item.contentSizeBytes != null) voltColors.emeraldShield.copy(alpha = 0.14f)
                          else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                      ) {
                        Row(
                          modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                          verticalAlignment = Alignment.CenterVertically,
                          horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                          if (item.isResolvingMetadata) {
                            CircularProgressIndicator(
                              modifier = Modifier.size(10.dp),
                              strokeWidth = 1.5.dp,
                              color = voltColors.emeraldShield,
                            )
                          }
                          Text(
                            text = sizeText,
                            style = MaterialTheme.typography.labelSmall,
                            color =
                              if (item.contentSizeBytes != null) voltColors.emeraldShield
                              else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                          )
                        }
                      }
                      if (durationLabel != null) {
                        Surface(
                          shape = RoundedCornerShape(6.dp),
                          color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                        ) {
                          Text(
                            text = durationLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                          )
                        }
                      }
                      if (segLabel != null) {
                        Text(
                          text = segLabel,
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                          maxLines = 1,
                        )
                      }
                    }

                    // Bottom Action Row: Compact Play, Download, and External Player pills
                    if (isPlayable) {
                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                      ) {
                        CompactMediaPillButton(
                          icon = Icons.Default.PlayArrow,
                          label = "Play",
                          containerColor = voltColors.cyanAccent,
                          contentColor = Color(0xFF002026),
                          onClick = { onPlayDetected(item) },
                          modifier = Modifier.weight(1f).testTag("sniffer_play_button"),
                        )
                        CompactMediaPillButton(
                          icon = Icons.Default.Download,
                          label = "Download",
                          containerColor = voltColors.emeraldShield.copy(alpha = 0.18f),
                          contentColor = voltColors.emeraldShield,
                          onClick = {
                            onDownloadDetected(item)
                            selectedTab = 1
                          },
                          modifier = Modifier.weight(1f).testTag("sniffer_download_button"),
                        )
                        CompactMediaPillButton(
                          icon = Icons.AutoMirrored.Filled.OpenInNew,
                          label = "External",
                          containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                          contentColor = MaterialTheme.colorScheme.onSurface,
                          onClick = { onOpenExternalDetected(item) },
                          modifier = Modifier.weight(1f).testTag("sniffer_external_button"),
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        }

        1 -> {
          LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            item {
              DeviceStoragePermissionCard(
                hasNotificationPermission = hasNotificationPermission,
                onRequestNotifications = {
                  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                  }
                },
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
                  shape = RoundedCornerShape(16.dp),
                  color = voltColors.cardSurface,
                  border = androidx.compose.foundation.BorderStroke(1.dp, voltColors.cardBorder),
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
                          Modifier.size(34.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(voltColors.cyanAccent.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                      ) {
                        Icon(
                          imageVector = iconForDownloadFile(dl.fileName, dl.mimeType),
                          contentDescription = null,
                          tint = voltColors.cyanAccent,
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
                              DownloadStatus.FAILED -> "Failed • Tap retry"
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
                            contentDescription = "Retry download",
                            tint = voltColors.amberAccent,
                          )
                        }
                      }
                      if (status == DownloadStatus.COMPLETED && isMedia) {
                        IconButton(onClick = { onPlayDownload(dl) }) {
                          Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play downloaded media",
                            tint = voltColors.cyanAccent,
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
                          onClick = {
                            pendingSaveAsItem = dl
                            saveAsLauncher.launch(dl.fileName)
                          },
                          modifier = Modifier.weight(1f).testTag("save_as_button"),
                          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                          Icon(
                            imageVector = Icons.Default.DriveFileMove,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = voltColors.cyanAccent,
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
                          color = voltColors.cyanAccent,
                        )
                      } else {
                        LinearProgressIndicator(
                          modifier = Modifier.fillMaxWidth().height(4.dp),
                          color = voltColors.cyanAccent,
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        }

        else -> {
          LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            item {
              Text(
                text = "Direct File / Stream URL",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Spacer(modifier = Modifier.height(6.dp))
              OutlinedTextField(
                value = directUrlInput,
                onValueChange = { directUrlInput = it },
                label = { Text("Paste any file (.pdf, .zip, .apk) or media URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("direct_media_url_input"),
              )
              Spacer(modifier = Modifier.height(8.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Button(
                  onClick = {
                    val trimmed = directUrlInput.trim()
                    if (trimmed.startsWith("http")) onStreamDirectUrl(trimmed)
                  },
                  enabled = directUrlInput.trim().startsWith("http"),
                  modifier = Modifier.weight(1f).height(44.dp),
                  colors =
                    ButtonDefaults.buttonColors(
                      containerColor = voltColors.cyanAccent,
                      contentColor = Color(0xFF002026),
                    ),
                ) {
                  Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Stream")
                }
                OutlinedButton(
                  onClick = {
                    val trimmed = directUrlInput.trim()
                    if (trimmed.startsWith("http") || trimmed.startsWith("data:", ignoreCase = true)) {
                      onDownloadDirectUrl(trimmed)
                      selectedTab = 1
                    }
                  },
                  enabled =
                    directUrlInput.trim().startsWith("http") ||
                      directUrlInput.trim().startsWith("data:", ignoreCase = true),
                  modifier = Modifier.weight(1f).height(44.dp).testTag("direct_url_download_button"),
                ) {
                  Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Download")
                }
              }
              Spacer(modifier = Modifier.height(8.dp))
              OutlinedButton(
                onClick = {
                  val trimmed = directUrlInput.trim()
                  if (trimmed.startsWith("http")) onOpenExternalDirectUrl(trimmed)
                },
                enabled = directUrlInput.trim().startsWith("http"),
                modifier = Modifier.fillMaxWidth().height(42.dp).testTag("direct_external_player_button"),
              ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Play in External Player")
              }
            }

            item {
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = "Device Media (Zero-Permission Picker)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Spacer(modifier = Modifier.height(8.dp))
              Button(
                onClick = {
                  videoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                  )
                },
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("pick_local_video_button"),
                colors =
                  ButtonDefaults.buttonColors(
                    containerColor = voltColors.cyanAccent,
                    contentColor = Color(0xFF002026),
                  ),
              ) {
                Icon(imageVector = Icons.Default.VideoLibrary, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Pick Video from Gallery")
              }
              Spacer(modifier = Modifier.height(8.dp))
              OutlinedButton(
                onClick = { audioDocumentPicker.launch(arrayOf("audio/*", "video/*")) },
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("pick_local_audio_button"),
              ) {
                Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Audio / Media File")
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
    border = androidx.compose.foundation.BorderStroke(1.dp, voltColors.cardBorder),
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

@Composable
private fun CompactMediaPillButton(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  containerColor: Color,
  contentColor: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    shape = RoundedCornerShape(10.dp),
    color = containerColor,
    modifier = modifier.minimumInteractiveComponentSize().heightIn(min = 42.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = contentColor,
        modifier = Modifier.size(15.dp),
      )
      Spacer(modifier = Modifier.width(4.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = contentColor,
        maxLines = 1,
      )
    }
  }
}

private fun formatCompactMediaUrl(url: String): String {
  val parsed = runCatching { Uri.parse(url) }.getOrNull() ?: return url
  val host = parsed.host.orEmpty()
  val segment = parsed.lastPathSegment?.takeIf { it.isNotBlank() } ?: parsed.path.orEmpty()
  return when {
    host.isNotBlank() && segment.isNotBlank() -> "$host • $segment"
    host.isNotBlank() -> host
    else -> url
  }
}

@Composable
private fun EmptyMediaState(title: String, subtitle: String) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(20.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Image(
      painter = painterResource(id = R.drawable.img_media_vault_empty),
      contentDescription = "Empty media vault illustration",
      contentScale = ContentScale.Crop,
      modifier = Modifier.size(width = 180.dp, height = 135.dp).clip(RoundedCornerShape(18.dp)),
    )
    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
      text = subtitle,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

private fun isPlayableMediaDownload(fileName: String, mimeType: String): Boolean {
  val lowerMime = mimeType.lowercase()
  if (lowerMime.startsWith("video/") || lowerMime.startsWith("audio/")) return true
  val ext = fileName.substringAfterLast('.', "").lowercase()
  return ext in setOf("mp4", "ts", "webm", "mkv", "mov", "avi", "m4v", "mp3", "m4a", "aac", "ogg", "opus", "flac", "wav")
}

private fun iconForDownloadFile(fileName: String, mimeType: String): androidx.compose.ui.graphics.vector.ImageVector {
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

private fun queryDisplayName(context: android.content.Context, uri: Uri): String? {
  return runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
      ?.use { cursor ->
        if (cursor.moveToFirst()) {
          val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
          if (idx >= 0) cursor.getString(idx) else null
        } else null
      }
  }.getOrNull()
}
