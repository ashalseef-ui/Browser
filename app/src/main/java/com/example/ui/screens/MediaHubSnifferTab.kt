package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.example.R
import com.example.data.DetectedMediaItem
import com.example.data.MediaKind
import com.example.ui.components.formatBytes
import com.example.ui.components.formatDurationMs
import com.example.ui.theme.LocalVoltColors

/**
 * Sniffer tab inside Media Studio rendering playlist resolution input, per-host media compatibility
 * bypass toggle, and detected playable/downloadable video items (> 1 MB).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MediaHubSnifferTab(
  playlistInput: String,
  onPlaylistInputChange: (String) -> Unit,
  onResolvePlaylistUrl: (String) -> Unit,
  currentHost: String,
  isSiteBypassEnabled: Boolean,
  onToggleSiteBypass: () -> Unit,
  isScanningMedia: Boolean,
  detectedMedia: List<DetectedMediaItem>,
  clipboard: ClipboardManager,
  onCopyUrlToast: () -> Unit,
  onPlayDetected: (DetectedMediaItem) -> Unit,
  onDownloadDetected: (DetectedMediaItem) -> Unit,
  onOpenExternalDetected: (DetectedMediaItem) -> Unit,
  onLongPressDetectedMedia: (DetectedMediaItem) -> Unit,
) {
  val voltColors = LocalVoltColors.current
  val haptics = LocalHapticFeedback.current

  LazyColumn(
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(
          value = playlistInput,
          onValueChange = onPlaylistInputChange,
          label = { Text("Resolve .m3u8, .mpd, .m3u, .pls, .xspf or .mp4 URL") },
          singleLine = true,
          modifier = Modifier.weight(1f).testTag("playlist_resolver_input"),
        )
        Button(
          onClick = {
            onResolvePlaylistUrl(playlistInput)
            onPlaylistInputChange("")
          },
          enabled = playlistInput.trim().startsWith("http", ignoreCase = true),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.height(52.dp).testTag("playlist_resolve_button"),
          colors =
            ButtonDefaults.buttonColors(
              containerColor = voltColors.brandAccent,
              contentColor = voltColors.onBrandAccent,
            ),
        ) {
          Text("Resolve")
        }
      }
    }

    if (currentHost.isNotBlank()) {
      item {
        val bypassTint = if (isSiteBypassEnabled) voltColors.amberAccent else voltColors.brandAccent
        Surface(
          shape = RoundedCornerShape(16.dp),
          color =
            if (isSiteBypassEnabled) voltColors.amberAccent.copy(alpha = 0.14f)
            else voltColors.cardSurface,
          border = BorderStroke(1.dp, bypassTint.copy(alpha = 0.45f)),
          modifier =
            Modifier.fillMaxWidth()
              .minimumInteractiveComponentSize()
              .clip(RoundedCornerShape(16.dp))
              .clickable(onClick = onToggleSiteBypass)
              .testTag("media_hub_site_bypass_chip"),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(
              imageVector = Icons.Default.Shield,
              contentDescription = null,
              tint = bypassTint,
              modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "$currentHost • Media Compatibility Bypass",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text =
                  if (isSiteBypassEnabled) "Data Saver paused & protected HTML5 players enabled"
                  else "Tap if strict site optimization stops video playback",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Surface(
              shape = RoundedCornerShape(100.dp),
              color = bypassTint.copy(alpha = 0.2f),
            ) {
              Text(
                text = if (isSiteBypassEnabled) "ACTIVE" else "ENABLE",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = bypassTint,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              )
            }
          }
        }
      }
    }

    if (isScanningMedia) {
      item {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = voltColors.brandAccent.copy(alpha = 0.12f),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            CircularProgressIndicator(
              modifier = Modifier.size(16.dp),
              strokeWidth = 2.dp,
              color = voltColors.brandAccent,
            )
            Text(
              text = "Resolving video links & checking sizes (> 1 MB only)...",
              style = MaterialTheme.typography.labelMedium,
              color = voltColors.brandAccent,
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
          shape = RoundedCornerShape(18.dp),
          color = voltColors.cardSurface,
          border = BorderStroke(1.dp, voltColors.cardBorder),
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(18.dp))
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
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.Top,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Box(
                modifier =
                  Modifier.size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                      if (item.parentPlaylistUrl != null) voltColors.brandAccent.copy(alpha = 0.16f)
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
                    if (item.parentPlaylistUrl != null) voltColors.brandAccent
                    else voltColors.amberAccent,
                  modifier = Modifier.size(20.dp),
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
                shape = RoundedCornerShape(10.dp),
                color = voltColors.cardElevatedSurface,
                modifier =
                  Modifier.minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(10.dp))
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

            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Surface(
                shape = RoundedCornerShape(8.dp),
                color = voltColors.brandAccent.copy(alpha = 0.16f),
              ) {
                Text(
                  text = kindBadge.uppercase(),
                  style = MaterialTheme.typography.labelSmall,
                  color = voltColors.brandAccent,
                  modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                )
              }
              Surface(
                shape = RoundedCornerShape(8.dp),
                color =
                  if (item.contentSizeBytes != null) voltColors.emeraldShield.copy(alpha = 0.14f)
                  else voltColors.cardElevatedSurface,
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
                  shape = RoundedCornerShape(8.dp),
                  color = voltColors.cardElevatedSurface,
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

            if (isPlayable) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                CompactMediaPillButton(
                  icon = Icons.Default.PlayArrow,
                  label = "Play",
                  containerColor = voltColors.brandAccent,
                  contentColor = voltColors.onBrandAccent,
                  onClick = { onPlayDetected(item) },
                  modifier = Modifier.weight(1f).testTag("sniffer_play_button"),
                )
                CompactMediaPillButton(
                  icon = Icons.Default.Download,
                  label = "Download",
                  containerColor = voltColors.emeraldShield.copy(alpha = 0.18f),
                  contentColor = voltColors.emeraldShield,
                  onClick = { onDownloadDetected(item) },
                  modifier = Modifier.weight(1f).testTag("sniffer_download_button"),
                )
                CompactMediaPillButton(
                  icon = Icons.AutoMirrored.Filled.OpenInNew,
                  label = "External",
                  containerColor = voltColors.cardElevatedSurface,
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

@Composable
internal fun CompactMediaPillButton(
  icon: ImageVector,
  label: String,
  containerColor: Color,
  contentColor: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = containerColor,
    modifier = modifier.minimumInteractiveComponentSize().heightIn(min = 42.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
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
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        color = contentColor,
        maxLines = 1,
      )
    }
  }
}

private fun formatCompactMediaUrl(url: String): String {
  val parsed = runCatching { url.toUri() }.getOrNull() ?: return url
  val host = parsed.host.orEmpty()
  val segment = parsed.lastPathSegment?.takeIf { it.isNotBlank() } ?: parsed.path.orEmpty()
  return when {
    host.isNotBlank() && segment.isNotBlank() -> "$host • $segment"
    host.isNotBlank() -> host
    else -> url
  }
}

@Composable
internal fun EmptyMediaState(title: String, subtitle: String) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(20.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Image(
      painter = painterResource(id = R.drawable.img_media_vault_empty),
      contentDescription = "Empty media studio illustration",
      contentScale = ContentScale.Crop,
      modifier = Modifier.size(width = 196.dp, height = 146.dp).clip(RoundedCornerShape(22.dp)),
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
