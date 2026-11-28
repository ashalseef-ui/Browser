package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.DetectedMediaItem
import com.example.data.DownloadItemEntity
import com.example.ui.theme.LocalVoltColors

/**
 * Media Studio bottom sheet uniting Web Media Sniffer, Download Manager, Direct URL player,
 * and zero-permission local Photo Picker / Document Picker playback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaHubSheet(
  detectedMedia: List<DetectedMediaItem>,
  isScanningMedia: Boolean,
  downloads: List<DownloadItemEntity>,
  currentHost: String = "",
  isSiteBypassEnabled: Boolean = false,
  onToggleSiteBypass: () -> Unit = {},
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
      modifier = Modifier.fillMaxWidth().heightIn(min = 460.dp, max = 700.dp).padding(bottom = 16.dp)
    ) {
      // Header
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Media Studio",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = "Stream sniffer (> 1 MB) • Media3 Cinema & Downloads",
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
            color = voltColors.brandAccent.copy(alpha = 0.14f),
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
                tint = voltColors.brandAccent,
                modifier = Modifier.size(15.dp),
              )
              Text(
                text = "Scan",
                style = MaterialTheme.typography.labelMedium,
                color = voltColors.brandAccent,
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Segmented Pill Switcher
      Surface(
        shape = RoundedCornerShape(16.dp),
        color = voltColors.cardSurface,
        border = BorderStroke(1.dp, voltColors.cardBorder),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(4.dp),
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          listOf(
            "Sniffer (${detectedMedia.size})",
            "Downloads (${downloads.size})",
            "Player / URL",
          ).forEachIndexed { idx, label ->
            val selected = selectedTab == idx
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = if (selected) voltColors.brandAccent else Color.Transparent,
              modifier =
                Modifier.weight(1f)
                  .clip(RoundedCornerShape(12.dp))
                  .clickable { selectedTab = idx },
            ) {
              Box(
                modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = label,
                  style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                  ),
                  color =
                    if (selected) voltColors.onBrandAccent
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
        }
      }

      when (selectedTab) {
        0 -> {
          MediaHubSnifferTab(
            playlistInput = playlistInput,
            onPlaylistInputChange = { playlistInput = it },
            onResolvePlaylistUrl = onResolvePlaylistUrl,
            currentHost = currentHost,
            isSiteBypassEnabled = isSiteBypassEnabled,
            onToggleSiteBypass = onToggleSiteBypass,
            isScanningMedia = isScanningMedia,
            detectedMedia = detectedMedia,
            clipboard = clipboard,
            onCopyUrlToast = onCopyUrlToast,
            onPlayDetected = onPlayDetected,
            onDownloadDetected = { item ->
              onDownloadDetected(item)
              selectedTab = 1
            },
            onOpenExternalDetected = onOpenExternalDetected,
            onLongPressDetectedMedia = onLongPressDetectedMedia,
          )
        }

        1 -> {
          MediaHubDownloadsTab(
            downloads = downloads,
            hasNotificationPermission = hasNotificationPermission,
            onRequestNotifications = {
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
              }
            },
            onCancelDownload = onCancelDownload,
            onRetryDownload = onRetryDownload,
            onPlayDownload = onPlayDownload,
            onOpenExternalDownload = onOpenExternalDownload,
            onOpenDownload = onOpenDownload,
            onSaveAsClick = { dl ->
              pendingSaveAsItem = dl
              saveAsLauncher.launch(dl.fileName)
            },
            onExportDownload = onExportDownload,
            onShareDownload = onShareDownload,
            onDeleteDownload = onDeleteDownload,
          )
        }

        else -> {
          DirectUrlAndLocalMediaTab(
            directUrlInput = directUrlInput,
            onDirectUrlChange = { directUrlInput = it },
            onStreamDirectUrl = onStreamDirectUrl,
            onDownloadDirectUrl = { url ->
              onDownloadDirectUrl(url)
              selectedTab = 1
            },
            onOpenExternalDirectUrl = onOpenExternalDirectUrl,
            onPickVideo = {
              videoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
              )
            },
            onPickAudioOrMedia = { audioDocumentPicker.launch(arrayOf("audio/*", "video/*")) },
          )
        }
      }
    }
  }
}

@Composable
private fun DirectUrlAndLocalMediaTab(
  directUrlInput: String,
  onDirectUrlChange: (String) -> Unit,
  onStreamDirectUrl: (String) -> Unit,
  onDownloadDirectUrl: (String) -> Unit,
  onOpenExternalDirectUrl: (String) -> Unit,
  onPickVideo: () -> Unit,
  onPickAudioOrMedia: () -> Unit,
) {
  val voltColors = LocalVoltColors.current
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
        onValueChange = onDirectUrlChange,
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
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.weight(1f).height(46.dp),
          colors =
            ButtonDefaults.buttonColors(
              containerColor = voltColors.brandAccent,
              contentColor = voltColors.onBrandAccent,
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
            }
          },
          enabled =
            directUrlInput.trim().startsWith("http") ||
              directUrlInput.trim().startsWith("data:", ignoreCase = true),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.weight(1f).height(46.dp).testTag("direct_url_download_button"),
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
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(44.dp).testTag("direct_external_player_button"),
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
        onClick = onPickVideo,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("pick_local_video_button"),
        colors =
          ButtonDefaults.buttonColors(
            containerColor = voltColors.brandAccent,
            contentColor = voltColors.onBrandAccent,
          ),
      ) {
        Icon(imageVector = Icons.Default.VideoLibrary, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Pick Video from Gallery")
      }
      Spacer(modifier = Modifier.height(8.dp))
      OutlinedButton(
        onClick = onPickAudioOrMedia,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("pick_local_audio_button"),
      ) {
        Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Open Audio / Media File")
      }
    }
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
