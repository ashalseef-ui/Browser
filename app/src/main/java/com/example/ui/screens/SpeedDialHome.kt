package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.BookmarkEntity
import com.example.data.BrowserSettings
import com.example.data.DetectedMediaItem
import com.example.data.MediaKind
import com.example.ui.components.extractHost
import com.example.ui.components.formatBytes
import com.example.ui.theme.LocalVoltColors

private val openMediaShowcase =
  listOf(
    DetectedMediaItem(
      url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
      title = "Big Buck Bunny (1080p MP4)",
      kind = MediaKind.VIDEO,
      mimeType = "video/mp4",
      sourcePageUrl = "Blender Open Movie Project",
      durationSeconds = 596L,
    ),
    DetectedMediaItem(
      url = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
      title = "Adaptive HLS Test Stream",
      kind = MediaKind.STREAM_HLS,
      mimeType = "application/x-mpegURL",
      sourcePageUrl = "Mux Open HLS Stream",
    ),
    DetectedMediaItem(
      url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
      title = "Sintel Open Film (MP4)",
      kind = MediaKind.VIDEO,
      mimeType = "video/mp4",
      sourcePageUrl = "Blender Foundation",
      durationSeconds = 888L,
    ),
  )

@Composable
fun SpeedDialHome(
  isIncognito: Boolean,
  settings: BrowserSettings,
  isVpnActive: Boolean,
  speedDialItems: List<BookmarkEntity>,
  isWideScreen: Boolean,
  onOpenUrl: (String) -> Unit,
  onPrewarmUrl: (String) -> Unit,
  onPlayMedia: (DetectedMediaItem) -> Unit,
  onOpenMediaHub: () -> Unit,
  onOpenVpn: () -> Unit,
  onAddSpeedDialClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val voltColors = LocalVoltColors.current

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
      // Atmospheric Hero Card with live speed telemetry
      item {
        Card(
          modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)),
          shape = RoundedCornerShape(24.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
          Box(modifier = Modifier.fillMaxWidth().height(176.dp)) {
            Image(
              painter = painterResource(id = R.drawable.img_speed_dial_hero),
              contentDescription = "Volt Browser high-speed fiber optic banner",
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize(),
            )
            Box(
              modifier =
                Modifier.fillMaxSize()
                  .background(
                    Brush.verticalGradient(
                      colors =
                        listOf(
                          Color(0x66090D14),
                          Color(0xE6090D14),
                        )
                    )
                  )
            )
            Column(
              modifier = Modifier.fillMaxSize().padding(20.dp),
              verticalArrangement = Arrangement.SpaceBetween,
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Surface(
                    shape = CircleShape,
                    color =
                      if (isIncognito) voltColors.incognitoAccent.copy(alpha = 0.22f)
                      else voltColors.cyanAccent.copy(alpha = 0.2f),
                  ) {
                    Icon(
                      imageVector =
                        if (isIncognito) Icons.Outlined.VisibilityOff else Icons.Default.Bolt,
                      contentDescription = null,
                      tint = if (isIncognito) voltColors.incognitoAccent else voltColors.cyanAccent,
                      modifier = Modifier.padding(7.dp).size(18.dp),
                    )
                  }
                  Column {
                    Text(
                      text = if (isIncognito) "VOLT PRIVATE" else "VOLT ENGINE",
                      style = MaterialTheme.typography.titleLarge,
                      color = Color.White,
                    )
                    Text(
                      text =
                        if (isIncognito) "Private • Zero history & cache"
                        else "No history • No cache • ${settings.searchEngine.displayName}",
                      style = MaterialTheme.typography.bodySmall,
                      color = Color(0xFFCBD5E1),
                    )
                  }
                }

                Row(
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Surface(
                    shape = RoundedCornerShape(100.dp),
                    color =
                      if (isVpnActive) voltColors.emeraldShield.copy(alpha = 0.22f)
                      else Color.White.copy(alpha = 0.14f),
                    border =
                      androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isVpnActive) voltColors.emeraldShield.copy(alpha = 0.6f)
                        else voltColors.cyanAccent.copy(alpha = 0.45f),
                      ),
                    modifier =
                      Modifier.clip(RoundedCornerShape(100.dp))
                        .clickable(onClick = onOpenVpn)
                        .testTag("speed_dial_vpn_button"),
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                      Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = "Free Auto Proxy",
                        tint = if (isVpnActive) voltColors.emeraldShield else voltColors.cyanAccent,
                        modifier = Modifier.size(13.dp),
                      )
                      Text(
                        text = if (isVpnActive) "PROXY ON" else "AUTO PROXY",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                      )
                    }
                  }

                  Surface(
                    shape = RoundedCornerShape(100.dp),
                    color =
                      if (settings.turboShieldEnabled) voltColors.emeraldShield.copy(alpha = 0.18f)
                      else Color.White.copy(alpha = 0.12f),
                    border =
                      androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (settings.turboShieldEnabled) voltColors.emeraldShield.copy(alpha = 0.5f)
                        else Color.White.copy(alpha = 0.2f),
                      ),
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                      Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint =
                          if (settings.turboShieldEnabled) voltColors.emeraldShield
                          else Color(0xFF94A3B8),
                        modifier = Modifier.size(13.dp),
                      )
                      Text(
                        text = if (settings.turboShieldEnabled) "SHIELD" else "OFF",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                      )
                    }
                  }
                }
              }

              // Real Telemetry Metrics Strip
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                TelemetryMetricPill(
                  icon = Icons.Default.Speed,
                  label = "AVG LOAD",
                  value = if (settings.avgLoadTimeMs > 0) "${settings.avgLoadTimeMs} ms" else "Ready",
                  tint = voltColors.cyanAccent,
                  modifier = Modifier.weight(1f),
                )
                TelemetryMetricPill(
                  icon = Icons.Default.Shield,
                  label = "BLOCKED",
                  value = "${settings.cumulativeTrackersBlocked}",
                  tint = voltColors.emeraldShield,
                  modifier = Modifier.weight(1f),
                )
                TelemetryMetricPill(
                  icon = Icons.Default.Bolt,
                  label = "DATA SAVED",
                  value = formatBytes(settings.cumulativeBytesSaved),
                  tint = voltColors.amberAccent,
                  modifier = Modifier.weight(1f),
                )
              }
            }
          }
        }
      }

      // Speed Dial Header
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Speed Dial",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
          )
          Surface(
            shape = RoundedCornerShape(100.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier =
              Modifier.clip(RoundedCornerShape(100.dp))
                .clickable(onClick = onAddSpeedDialClick)
                .testTag("add_speed_dial_button"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Pin site to Speed Dial",
                tint = voltColors.cyanAccent,
                modifier = Modifier.size(16.dp),
              )
              Text(
                text = "Pin Site",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
          }
        }
      }

      // Speed Dial Grid (chunked rows inside LazyColumn)
      val columns = if (isWideScreen) 4 else 2
      val rows = speedDialItems.chunked(columns)
      items(rows) { rowItems ->
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          rowItems.forEach { bookmark ->
            SpeedDialTile(
              bookmark = bookmark,
              onClick = {
                onPrewarmUrl(bookmark.url)
                onOpenUrl(bookmark.url)
              },
              modifier = Modifier.weight(1f),
            )
          }
          repeat(columns - rowItems.size) {
            Spacer(modifier = Modifier.weight(1f))
          }
        }
      }

      // Native Media3 Cinema & Audio Hub Section
      item {
        Spacer(modifier = Modifier.height(4.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column {
            Text(
              text = "Cinema & Media Engine",
              style = MaterialTheme.typography.titleLarge,
              color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
              text = "Native Media3 HLS/MP4 playback, PiP & background audio",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Surface(
            shape = RoundedCornerShape(100.dp),
            color = voltColors.amberAccent.copy(alpha = 0.16f),
            modifier =
              Modifier.clip(RoundedCornerShape(100.dp))
                .clickable(onClick = onOpenMediaHub)
                .testTag("speed_dial_media_hub_button"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = "Open Media Hub",
                tint = voltColors.amberAccent,
                modifier = Modifier.size(16.dp),
              )
              Text(
                text = "Video Sniffer",
                style = MaterialTheme.typography.labelLarge,
                color = voltColors.amberAccent,
              )
            }
          }
        }
      }

      item {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          items(openMediaShowcase) { media ->
            MediaShowcaseCard(
              item = media,
              onClick = { onPlayMedia(media) },
            )
          }
        }
        Spacer(modifier = Modifier.height(16.dp))
      }
    }
  }
}

@Composable
private fun TelemetryMetricPill(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  value: String,
  tint: Color,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(14.dp),
    color = Color(0xAA111824),
    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
  ) {
    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(12.dp))
        Text(
          text = label,
          style = MaterialTheme.typography.labelSmall,
          color = Color(0xFF94A3B8),
          maxLines = 1,
        )
      }
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = value,
        style = MaterialTheme.typography.titleMedium,
        color = Color.White,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun SpeedDialTile(
  bookmark: BookmarkEntity,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val voltColors = LocalVoltColors.current
  val accentColor =
    runCatching { Color(android.graphics.Color.parseColor(bookmark.accentHex)) }
      .getOrDefault(voltColors.cyanAccent)
  val host = extractHost(bookmark.url)

  Surface(
    modifier =
      modifier
        .clip(RoundedCornerShape(18.dp))
        .clickable(onClick = onClick)
        .testTag("speed_dial_tile_${bookmark.title.lowercase().replace(" ", "_")}"),
    shape = RoundedCornerShape(18.dp),
    color = voltColors.cardSurface,
    border = androidx.compose.foundation.BorderStroke(1.dp, voltColors.cardBorder),
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Box(
        modifier =
          Modifier.size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(accentColor.copy(alpha = 0.16f))
            .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = bookmark.title.take(1).uppercase(),
          style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
          color = accentColor,
        )
      }
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = bookmark.title,
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
          Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = voltColors.emeraldShield,
            modifier = Modifier.size(10.dp),
          )
          Text(
            text = host,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

@Composable
private fun MediaShowcaseCard(
  item: DetectedMediaItem,
  onClick: () -> Unit,
) {
  val voltColors = LocalVoltColors.current
  val badgeLabel =
    when (item.kind) {
      MediaKind.STREAM_HLS -> "HLS ADAPTIVE"
      MediaKind.STREAM_DASH -> "DASH"
      MediaKind.PLAYLIST -> "PLAYLIST"
      MediaKind.AUDIO -> "AUDIO"
      MediaKind.VIDEO -> "1080P MP4"
    }

  Surface(
    modifier =
      Modifier.width(232.dp)
        .clip(RoundedCornerShape(18.dp))
        .clickable(onClick = onClick)
        .testTag("showcase_media_card"),
    shape = RoundedCornerShape(18.dp),
    color = voltColors.cardSurface,
    border = androidx.compose.foundation.BorderStroke(1.dp, voltColors.cardBorder),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = voltColors.amberAccent.copy(alpha = 0.16f),
        ) {
          Text(
            text = badgeLabel,
            style = MaterialTheme.typography.labelSmall,
            color = voltColors.amberAccent,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          )
        }
        Box(
          modifier =
            Modifier.size(34.dp)
              .clip(CircleShape)
              .background(voltColors.cyanAccent),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector =
              if (item.kind == MediaKind.AUDIO) Icons.Default.GraphicEq else Icons.Default.PlayArrow,
            contentDescription = "Play ${item.title}",
            tint = Color(0xFF002026),
            modifier = Modifier.size(20.dp),
          )
        }
      }
      Column {
        Text(
          text = item.title,
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Icon(
            imageVector = Icons.Default.VideoLibrary,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(12.dp),
          )
          Text(
            text = item.sourcePageUrl,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}
