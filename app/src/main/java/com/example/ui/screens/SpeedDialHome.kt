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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AdBlockerType
import com.example.data.AppThemeMode
import com.example.data.BookmarkEntity
import com.example.data.BrowserSettings
import com.example.ui.components.extractHost
import com.example.ui.components.formatBytes
import com.example.ui.theme.LocalVoltColors
import com.example.ui.theme.VoltCyan

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun SpeedDialHome(
  isIncognito: Boolean,
  settings: BrowserSettings,
  speedDialItems: List<BookmarkEntity>,
  onOpenUrl: (String) -> Unit,
  onPrewarmUrl: (String) -> Unit,
  onOpenSecuritySheet: () -> Unit,
  onAddSpeedDialClick: () -> Unit,
  onOpenOmniSearch: () -> Unit,
  onLongPressTile: (BookmarkEntity) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val voltColors = LocalVoltColors.current
  val haptics = LocalHapticFeedback.current
  val isZenLight = settings.themeMode == AppThemeMode.ZEN_LIGHT
  val heroShape = RoundedCornerShape(26.dp)
  val cardShape = RoundedCornerShape(20.dp)
  val pillShape = RoundedCornerShape(100.dp)

  val canvasBackdropBrush =
    if (isZenLight) {
      Brush.verticalGradient(
        colors = listOf(Color(0xFFBAE6FD).copy(alpha = 0.28f), Color.Transparent)
      )
    } else {
      Brush.verticalGradient(
        colors = listOf(VoltCyan.copy(alpha = 0.08f), Color.Transparent)
      )
    }

  Box(
    modifier = modifier.fillMaxSize().background(canvasBackdropBrush),
    contentAlignment = Alignment.TopCenter,
  ) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 860.dp).fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // Command Console Hero Card (Clean, non-duplicated status & metrics)
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = heroShape,
          border = BorderStroke(1.dp, voltColors.cardBorder),
          colors =
            CardDefaults.cardColors(
              containerColor =
                if (isZenLight) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.surfaceContainer
            ),
        ) {
          Box(modifier = Modifier.fillMaxWidth().heightIn(min = 168.dp)) {
            if (!isZenLight) {
              Image(
                painter = painterResource(id = R.drawable.img_speed_dial_hero),
                contentDescription = "Volt Browser high-speed banner",
                contentScale = ContentScale.Crop,
                alpha = 0.80f,
                modifier = Modifier.fillMaxSize(),
              )
              Box(
                modifier =
                  Modifier.fillMaxSize()
                    .background(
                      Brush.verticalGradient(
                        colors = listOf(Color(0x55090D14), Color(0xEE090D14))
                      )
                    )
              )
            } else {
              Box(
                modifier =
                  Modifier.fillMaxSize()
                    .background(
                      Brush.linearGradient(
                        colors = listOf(Color(0xFFFFFFFF), Color(0xFFEDF6FF))
                      )
                    )
              )
            }

            val heroPrimaryTextColor =
              if (voltColors.isLightTheme) MaterialTheme.colorScheme.onSurface else Color.White
            val heroSecondaryTextColor =
              if (voltColors.isLightTheme) MaterialTheme.colorScheme.onSurfaceVariant
              else Color(0xFFCBD5E1)

            val blockerTint =
              when (settings.adBlockerType) {
                AdBlockerType.OFF -> MaterialTheme.colorScheme.onSurfaceVariant
                AdBlockerType.STANDARD -> voltColors.emeraldShield
                AdBlockerType.ADGUARD -> voltColors.emeraldShield
                AdBlockerType.STRICT -> voltColors.cyanAccent
              }

            Column(
              modifier = Modifier.fillMaxSize().padding(14.dp),
              verticalArrangement = Arrangement.SpaceBetween,
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                  modifier = Modifier.weight(1f),
                ) {
                  Surface(
                    shape = CircleShape,
                    color =
                      if (isIncognito) voltColors.incognitoAccent.copy(alpha = 0.22f)
                      else voltColors.cyanAccent.copy(alpha = 0.18f),
                    border =
                      BorderStroke(
                        1.dp,
                        if (isIncognito) voltColors.incognitoAccent.copy(alpha = 0.5f)
                        else voltColors.cyanAccent.copy(alpha = 0.45f),
                      ),
                  ) {
                    Icon(
                      imageVector =
                        if (isIncognito) Icons.Outlined.VisibilityOff else Icons.Default.Bolt,
                      contentDescription = null,
                      tint = if (isIncognito) voltColors.incognitoAccent else voltColors.cyanAccent,
                      modifier = Modifier.padding(9.dp).size(19.dp),
                    )
                  }
                  Column {
                    Text(
                      text =
                        when {
                          isIncognito -> "VOLT PRIVATE"
                          isZenLight -> "Volt Zen"
                          else -> "VOLT ENGINE"
                        },
                      style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                      color = heroPrimaryTextColor,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                      text =
                        if (isIncognito) "Private Tab • Zero local history"
                        else "Zero-Trace • ${settings.searchEngine.displayName}",
                      style = MaterialTheme.typography.bodySmall,
                      color = heroSecondaryTextColor,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                }

                // Single concise Blocker status pill (taps straight to Blocker selector)
                Surface(
                  shape = pillShape,
                  color =
                    if (settings.adBlockerType != AdBlockerType.OFF) blockerTint.copy(alpha = 0.18f)
                    else voltColors.pillSurface,
                  border = BorderStroke(1.dp, blockerTint.copy(alpha = 0.55f)),
                  modifier =
                    Modifier.minimumInteractiveComponentSize()
                      .clip(pillShape)
                      .clickable(onClick = onOpenSecuritySheet)
                      .testTag("speed_dial_blocker_badge"),
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Default.Shield,
                      contentDescription = "Configure AdGuard blocker",
                      tint = blockerTint,
                      modifier = Modifier.size(14.dp),
                    )
                    Text(
                      text = settings.adBlockerType.badgeLabel,
                      style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                      color = heroPrimaryTextColor,
                    )
                  }
                }
              }

              // Real Telemetry Metrics Strip
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                TelemetryMetricPill(
                  icon = Icons.Default.Speed,
                  label = "AVG LOAD",
                  value = if (settings.avgLoadTimeMs > 0) "${settings.avgLoadTimeMs} ms" else "Instant",
                  tint = voltColors.cyanAccent,
                  modifier = Modifier.weight(1f),
                )
                TelemetryMetricPill(
                  icon = Icons.Default.Shield,
                  label = "ADS BLOCKED",
                  value = "${settings.cumulativeTrackersBlocked}",
                  tint = voltColors.emeraldShield,
                  modifier = Modifier.weight(1f),
                )
                TelemetryMetricPill(
                  icon = Icons.Default.Bolt,
                  label = "SAVED",
                  value = formatBytes(settings.cumulativeBytesSaved),
                  tint = voltColors.amberAccent,
                  modifier = Modifier.weight(1f),
                )
              }
            }
          }
        }
      }

      // Omni-Engine Multi-Search Spotlight Card
      item {
        Card(
          modifier =
            Modifier.fillMaxWidth()
              .clip(cardShape)
              .clickable(onClick = onOpenOmniSearch)
              .testTag("omni_search_home_banner"),
          shape = cardShape,
          colors =
            CardDefaults.cardColors(
              containerColor =
                if (isZenLight) MaterialTheme.colorScheme.surface
                else voltColors.cardSurface
            ),
          border = BorderStroke(1.dp, voltColors.cyanAccent.copy(alpha = 0.45f)),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Surface(
                shape = CircleShape,
                color = voltColors.cyanAccent.copy(alpha = 0.16f),
                modifier = Modifier.size(42.dp),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = voltColors.cyanAccent,
                    modifier = Modifier.size(21.dp),
                  )
                }
              }
              Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                  text = "Omni-Engine Multi-Search",
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                  color = voltColors.cyanAccent,
                )
                Text(
                  text = "Compare results across 5 independent search engines in parallel",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Launch Omni-Search",
                tint = voltColors.cyanAccent,
                modifier = Modifier.size(18.dp),
              )
            }

            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              listOf("SearXNG", "Mojeek", "Marginalia", "Brave", "DuckDuckGo").forEach { tag ->
                Surface(
                  shape = pillShape,
                  color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                  Text(
                    text = tag,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                  )
                }
              }
            }
          }
        }
      }

      // Clean Pinned Speed Dial Section (Pin Site only — no duplicate Media/VPN buttons)
      item {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "PINNED SITES",
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
              shape = pillShape,
              color = voltColors.cyanAccent.copy(alpha = 0.14f),
              border = BorderStroke(1.dp, voltColors.cyanAccent.copy(alpha = 0.45f)),
              modifier =
                Modifier.minimumInteractiveComponentSize()
                  .clip(pillShape)
                  .clickable(onClick = onAddSpeedDialClick)
                  .testTag("add_speed_dial_button"),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.Add,
                  contentDescription = null,
                  tint = voltColors.cyanAccent,
                  modifier = Modifier.size(14.dp),
                )
                Text(
                  text = "Pin Site",
                  style = MaterialTheme.typography.labelMedium,
                  color = voltColors.cyanAccent,
                )
              }
            }
          }

          if (speedDialItems.isEmpty()) {
            Surface(
              shape = cardShape,
              color = voltColors.cardSurface,
              border = BorderStroke(1.dp, voltColors.cardBorder),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text = "Zero-trace workspace ready. Tap Pin Site or type any URL in the omnibox below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
              )
            }
          } else {
            val rows = speedDialItems.chunked(2)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
              rows.forEach { pair ->
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  pair.forEach { item ->
                    val host = extractHost(item.url)
                    val monogram =
                      item.title.firstOrNull()?.uppercaseChar()?.toString()
                        ?: host.firstOrNull()?.uppercaseChar()?.toString()
                        ?: "V"
                    Surface(
                      shape = cardShape,
                      color = voltColors.cardSurface,
                      border = BorderStroke(1.dp, voltColors.cardBorder),
                      modifier =
                        Modifier.weight(1f)
                          .minimumInteractiveComponentSize()
                          .clip(cardShape)
                          .combinedClickable(
                            onClick = {
                              onPrewarmUrl(item.url)
                              onOpenUrl(item.url)
                            },
                            onLongClick = {
                              haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                              onLongPressTile(item)
                            },
                          )
                          .testTag("speed_dial_tile_${item.id}"),
                    ) {
                      Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                      ) {
                        Box(
                          modifier =
                            Modifier.size(34.dp)
                              .clip(CircleShape)
                              .background(voltColors.cyanAccent.copy(alpha = 0.16f)),
                          contentAlignment = Alignment.Center,
                        ) {
                          Text(
                            text = monogram,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = voltColors.cyanAccent,
                          )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                          Text(
                            text = item.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
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
                  if (pair.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun TelemetryMetricPill(
  icon: ImageVector,
  label: String,
  value: String,
  tint: Color,
  modifier: Modifier = Modifier,
) {
  val voltColors = LocalVoltColors.current
  val shape = RoundedCornerShape(14.dp)

  Surface(
    modifier = modifier,
    shape = shape,
    color = voltColors.pillSurface,
    border = BorderStroke(1.dp, voltColors.pillBorder),
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
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
        )
      }
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = value,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}
