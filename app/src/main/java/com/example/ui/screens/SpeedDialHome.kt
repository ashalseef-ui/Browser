package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AdBlockerType
import com.example.data.AppThemeMode
import com.example.data.BookmarkEntity
import com.example.data.BrowserSettings
import com.example.ui.components.extractHost
import com.example.ui.components.formatBytes
import com.example.ui.components.formatCompactDataCounter
import com.example.ui.theme.LocalVoltColors
import com.example.ui.theme.badgeAccentColor
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpeedDialHome(
  isIncognito: Boolean,
  settings: BrowserSettings,
  sessionDataUsedBytes: Long,
  isVpnActive: Boolean,
  speedDialItems: List<BookmarkEntity>,
  onOpenUrl: (String) -> Unit,
  onPrewarmUrl: (String) -> Unit,
  onNewPrivateTab: () -> Unit,
  onOpenSecuritySheet: () -> Unit,
  onOpenVpnSheet: () -> Unit,
  onOpenAddonsSheet: () -> Unit = {},
  onRequestExit: () -> Unit,
  onAddSpeedDialClick: () -> Unit,
  onLongPressTile: (BookmarkEntity) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val voltColors = LocalVoltColors.current
  val haptics = LocalHapticFeedback.current
  val focusManager = LocalFocusManager.current
  val isLight = settings.themeMode == AppThemeMode.ZEN_LIGHT
  var homeSearchQuery by remember { mutableStateOf("") }

  val backdropBrush =
    if (isLight) {
      Brush.verticalGradient(
        colors = listOf(Color(0xFFEBE4D7).copy(alpha = 0.55f), Color.Transparent)
      )
    } else {
      Brush.verticalGradient(
        colors = listOf(voltColors.brandAccent.copy(alpha = 0.08f), Color.Transparent)
      )
    }

  Box(
    modifier = modifier.fillMaxSize().background(backdropBrush),
    contentAlignment = Alignment.TopCenter,
  ) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 860.dp).fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // 1. Minimal Editorial Top Bar
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color =
                if (isIncognito) voltColors.incognitoAccent
                else voltColors.brandAccent,
              modifier = Modifier.size(36.dp),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector =
                    if (isIncognito) Icons.Outlined.VisibilityOff else Icons.Default.Bolt,
                  contentDescription = null,
                  tint = if (isIncognito) Color.White else voltColors.onBrandAccent,
                  modifier = Modifier.size(20.dp),
                )
              }
            }
            Column {
              Text(
                text = if (isIncognito) "VOLT PRIVATE" else "VOLT",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
              )
              Text(
                text =
                  if (isIncognito) "Ephemeral private session"
                  else "Zero-trace architecture",
                style = MaterialTheme.typography.labelSmall,
                color =
                  if (isIncognito) voltColors.incognitoAccent
                  else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            // Session Data Pill -> opens Security & Telemetry Sheet
            Surface(
              shape = RoundedCornerShape(100.dp),
              color = voltColors.cardSurface,
              border = BorderStroke(1.dp, voltColors.cardBorder),
              modifier =
                Modifier.minimumInteractiveComponentSize()
                  .clip(RoundedCornerShape(100.dp))
                  .clickable(onClick = onOpenSecuritySheet)
                  .testTag("home_telemetry_chip"),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.DataUsage,
                  contentDescription = null,
                  tint = voltColors.brandAccent,
                  modifier = Modifier.size(14.dp),
                )
                Text(
                  text = formatCompactDataCounter(sessionDataUsedBytes),
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurface,
                )
              }
            }

            // Quick Private Tab Pill
            if (!isIncognito) {
              Surface(
                shape = RoundedCornerShape(100.dp),
                color = voltColors.incognitoAccent.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, voltColors.incognitoAccent.copy(alpha = 0.4f)),
                modifier =
                  Modifier.minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(100.dp))
                    .clickable(onClick = onNewPrivateTab)
                    .testTag("home_new_private_tab_button"),
              ) {
                Box(
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    imageVector = Icons.Outlined.VisibilityOff,
                    contentDescription = stringResource(R.string.new_private_tab),
                    tint = voltColors.incognitoAccent,
                    modifier = Modifier.size(16.dp),
                  )
                }
              }
            }

            // Exit & Erase Button
            Surface(
              shape = RoundedCornerShape(100.dp),
              color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
              modifier =
                Modifier.minimumInteractiveComponentSize()
                  .clip(RoundedCornerShape(100.dp))
                  .clickable(onClick = onRequestExit)
                  .testTag("home_exit_button"),
            ) {
              Box(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Default.PowerSettingsNew,
                  contentDescription = stringResource(R.string.cd_exit),
                  tint = MaterialTheme.colorScheme.error,
                  modifier = Modifier.size(16.dp),
                )
              }
            }
          }
        }
      }

      // 2. Sculptural Atmospheric Banner + Direct Search Card
      item {
        val heroShape = RoundedCornerShape(26.dp)
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = heroShape,
          color = voltColors.cardSurface,
          border = BorderStroke(1.dp, voltColors.cardBorder),
        ) {
          Box(modifier = Modifier.fillMaxWidth().heightIn(min = 196.dp)) {
            if (!isLight) {
              Image(
                painter = painterResource(id = R.drawable.img_speed_dial_hero),
                contentDescription = "Volt Browser architectural prism banner",
                contentScale = ContentScale.Crop,
                alpha = 0.72f,
                modifier = Modifier.matchParentSize(),
              )
              Box(
                modifier =
                  Modifier.matchParentSize()
                    .background(
                      Brush.verticalGradient(
                        colors = listOf(Color(0x440D0D10), Color(0xEC0D0D10))
                      )
                    )
              )
            } else {
              Box(
                modifier =
                  Modifier.matchParentSize()
                    .background(
                      Brush.linearGradient(
                        colors = listOf(Color(0xFFFFFFFF), Color(0xFFF3EFE6))
                      )
                    )
              )
            }

            Column(
              modifier = Modifier.fillMaxWidth().padding(18.dp),
              verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text =
                      if (isIncognito) "Off-the-Record Workspace"
                      else "Browse Without Friction",
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (isLight) MaterialTheme.colorScheme.onSurface else Color.White,
                  )
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text =
                      if (isIncognito) "Cookies & disk cache stay wiped for this tab."
                      else "Active Engine: ${settings.searchEngine.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color =
                      if (isLight) MaterialTheme.colorScheme.onSurfaceVariant
                      else Color(0xFFD1D1DA),
                  )
                }

                Column(
                  horizontalAlignment = Alignment.End,
                  verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  // Tunnel Status Pill on Hero Card
                  Surface(
                    shape = RoundedCornerShape(100.dp),
                    color =
                      if (isVpnActive) voltColors.emeraldShield.copy(alpha = 0.18f)
                      else voltColors.pillSurface,
                    border =
                      BorderStroke(
                        1.dp,
                        if (isVpnActive) voltColors.emeraldShield.copy(alpha = 0.55f)
                        else voltColors.pillBorder,
                      ),
                    modifier =
                      Modifier.clip(RoundedCornerShape(100.dp))
                        .clickable(onClick = onOpenVpnSheet),
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                      Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = null,
                        tint =
                          if (isVpnActive) voltColors.emeraldShield
                          else if (isLight) MaterialTheme.colorScheme.onSurfaceVariant
                          else Color.White,
                        modifier = Modifier.size(13.dp),
                      )
                      Text(
                        text = if (isVpnActive) "Tunnel On" else "Direct",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color =
                          if (isVpnActive) voltColors.emeraldShield
                          else if (isLight) MaterialTheme.colorScheme.onSurface
                          else Color.White,
                      )
                    }
                  }

                  // Firefox Add-ons & Engine Pill
                  Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = voltColors.brandAccent.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, voltColors.brandAccent.copy(alpha = 0.45f)),
                    modifier =
                      Modifier.clip(RoundedCornerShape(100.dp))
                        .clickable(onClick = onOpenAddonsSheet)
                        .testTag("home_addons_button"),
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                      Icon(
                        imageVector = Icons.Default.Extension,
                        contentDescription = null,
                        tint = voltColors.brandAccent,
                        modifier = Modifier.size(13.dp),
                      )
                      Text(
                        text = "${settings.engineMode.badgeLabel} Add-ons",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isLight) MaterialTheme.colorScheme.onSurface else Color.White,
                      )
                    }
                  }
                }
              }

              // Inline Start-Page Search Bar
              val engineAccent = settings.searchEngine.badgeAccentColor(isLight)
              Surface(
                shape = RoundedCornerShape(18.dp),
                color =
                  if (isLight) voltColors.omniboxSurface
                  else Color(0xEB1B1B22),
                border = BorderStroke(1.dp, voltColors.cardBorder),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = engineAccent,
                    modifier = Modifier.size(20.dp),
                  )
                  Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (homeSearchQuery.isEmpty()) {
                      Text(
                        text = "Search ${settings.searchEngine.shortLabel} or enter URL...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                    }
                    BasicTextField(
                      value = homeSearchQuery,
                      onValueChange = { homeSearchQuery = it },
                      singleLine = true,
                      textStyle =
                        MaterialTheme.typography.bodyMedium.copy(
                          color = MaterialTheme.colorScheme.onSurface
                        ),
                      cursorBrush = SolidColor(voltColors.brandAccent),
                      keyboardOptions =
                        KeyboardOptions(
                          keyboardType = KeyboardType.Uri,
                          imeAction = ImeAction.Go,
                        ),
                      keyboardActions =
                        KeyboardActions(
                          onGo = {
                            val trimmed = homeSearchQuery.trim()
                            if (trimmed.isNotEmpty()) {
                              focusManager.clearFocus()
                              onOpenUrl(trimmed)
                              homeSearchQuery = ""
                            }
                          }
                        ),
                      modifier = Modifier.fillMaxWidth().testTag("home_search_input"),
                    )
                  }
                  if (homeSearchQuery.isNotBlank()) {
                    Surface(
                      shape = CircleShape,
                      color = voltColors.brandAccent,
                      modifier =
                        Modifier.size(34.dp)
                          .clip(CircleShape)
                          .clickable {
                            val trimmed = homeSearchQuery.trim()
                            if (trimmed.isNotEmpty()) {
                              focusManager.clearFocus()
                              onOpenUrl(trimmed)
                              homeSearchQuery = ""
                            }
                          },
                    ) {
                      Box(contentAlignment = Alignment.Center) {
                        Icon(
                          imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                          contentDescription = "Go",
                          tint = voltColors.onBrandAccent,
                          modifier = Modifier.size(18.dp),
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }

      // 3. Real Performance & Privacy Metrics Row
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          MetricTile(
            icon = Icons.Default.Speed,
            label = "AVG LOAD",
            value = if (settings.avgLoadTimeMs > 0) "${settings.avgLoadTimeMs} ms" else "Ready",
            tint = voltColors.brandAccent,
            modifier = Modifier.weight(1f),
          )
          MetricTile(
            icon = Icons.Default.Bolt,
            label = "DATA SAVED",
            value = formatBytes(settings.cumulativeBytesSaved),
            tint = voltColors.amberAccent,
            modifier = Modifier.weight(1f),
          )
          val shieldEnabled = settings.adBlockerType != AdBlockerType.OFF
          MetricTile(
            icon = Icons.Default.Shield,
            label = "AD SHIELD",
            value =
              if (shieldEnabled) {
                if (settings.cumulativeTrackersBlocked > 0) {
                  "${settings.adBlockerType.badgeLabel} • ${settings.cumulativeTrackersBlocked}"
                } else {
                  settings.adBlockerType.displayName
                }
              } else {
                "Off"
              },
            tint = if (shieldEnabled) voltColors.emeraldShield else voltColors.amberAccent,
            modifier = Modifier.weight(1f).clickable(onClick = onOpenSecuritySheet),
          )
        }
      }

      // 4. Pinned Sites 3-Column Squircle Grid
      item {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "PINNED WORKSPACE",
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
              shape = RoundedCornerShape(100.dp),
              color = voltColors.brandAccent.copy(alpha = 0.14f),
              modifier =
                Modifier.minimumInteractiveComponentSize()
                  .clip(RoundedCornerShape(100.dp))
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
                  contentDescription = null,
                  tint = voltColors.brandAccent,
                  modifier = Modifier.size(15.dp),
                )
                Text(
                  text = "Pin Site",
                  style = MaterialTheme.typography.labelMedium,
                  color = voltColors.brandAccent,
                )
              }
            }
          }

          if (speedDialItems.isEmpty()) {
            Surface(
              shape = RoundedCornerShape(20.dp),
              color = voltColors.cardSurface,
              border = BorderStroke(1.dp, voltColors.cardBorder),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text = "Your workspace is clean. Tap Pin Site or enter any address above.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(18.dp),
              )
            }
          } else {
            val rows = speedDialItems.chunked(3)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
              rows.forEach { triple ->
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  triple.forEach { item ->
                    val host = extractHost(item.url)
                    val monogram =
                      item.title.firstOrNull()?.uppercaseChar()?.toString()
                        ?: host.firstOrNull()?.uppercaseChar()?.toString()
                        ?: "V"
                    val tileAccent = tileColorForHost(host, voltColors.isLightTheme)
                    val tileShape = RoundedCornerShape(20.dp)

                    Surface(
                      shape = tileShape,
                      color = voltColors.cardSurface,
                      border = BorderStroke(1.dp, voltColors.cardBorder),
                      modifier =
                        Modifier.weight(1f)
                          .minimumInteractiveComponentSize()
                          .clip(tileShape)
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
                      Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                      ) {
                        Box(
                          modifier =
                            Modifier.size(42.dp)
                              .clip(RoundedCornerShape(14.dp))
                              .background(tileAccent.copy(alpha = 0.16f))
                              .border(
                                1.dp,
                                tileAccent.copy(alpha = 0.35f),
                                RoundedCornerShape(14.dp),
                              ),
                          contentAlignment = Alignment.Center,
                        ) {
                          Text(
                            text = monogram,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = tileAccent,
                          )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                          Text(
                            text = item.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                          )
                          Text(
                            text = host,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                          )
                        }
                      }
                    }
                  }
                  repeat(3 - triple.size) {
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
private fun MetricTile(
  icon: ImageVector,
  label: String,
  value: String,
  tint: Color,
  modifier: Modifier = Modifier,
) {
  val voltColors = LocalVoltColors.current
  Surface(
    modifier = modifier.clip(RoundedCornerShape(18.dp)),
    shape = RoundedCornerShape(18.dp),
    color = voltColors.cardSurface,
    border = BorderStroke(1.dp, voltColors.cardBorder),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
      ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(13.dp))
        Text(
          text = label,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Text(
        text = value,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

private fun tileColorForHost(host: String, isLight: Boolean): Color {
  val darkPalette =
    listOf(
      Color(0xFFFF6347),
      Color(0xFF60A5FA),
      Color(0xFF34D399),
      Color(0xFFFBBF24),
      Color(0xFFA78BFA),
      Color(0xFFF472B6),
    )
  val lightPalette =
    listOf(
      Color(0xFFDC3D1E),
      Color(0xFF2563EB),
      Color(0xFF059669),
      Color(0xFFD97706),
      Color(0xFF7C3AED),
      Color(0xFFDB2777),
    )
  val idx = abs(host.hashCode()) % darkPalette.size
  return if (isLight) lightPalette[idx] else darkPalette[idx]
}
