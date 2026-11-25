package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.AdBlockerType
import com.example.data.AppThemeMode
import com.example.data.BookmarkEntity
import com.example.data.BrowserSettings
import com.example.data.BrowserTabState
import com.example.data.SearchEngine
import com.example.ui.components.extractHost
import com.example.ui.components.formatBytes
import com.example.ui.theme.LocalVoltColors
import com.example.ui.theme.VoltCyan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityTelemetrySheet(
  tab: BrowserTabState,
  settings: BrowserSettings,
  sessionDataUsedBytes: Long = 0L,
  onSelectAdBlockerType: (AdBlockerType) -> Unit,
  onToggleLiteMode: (Boolean) -> Unit,
  onToggleHttpsOnly: (Boolean) -> Unit,
  onToggleDarkWeb: (Boolean) -> Unit,
  onToggleSiteMediaBypass: () -> Unit = {},
  onClearSitePermissions: () -> Unit = {},
  onWipeSessionNow: () -> Unit,
  onDismiss: () -> Unit,
) {
  val voltColors = LocalVoltColors.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Icon(
          imageVector =
            if (tab.isSpeedDial || tab.isSecure) Icons.Default.Lock else Icons.Default.Warning,
          contentDescription = null,
          tint =
            if (tab.isSpeedDial || tab.isSecure) voltColors.emeraldShield
            else voltColors.amberAccent,
          modifier = Modifier.size(28.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text =
              when {
                tab.isSpeedDial -> "Zero-Trace Shield Ready"
                tab.isSecure -> "Encrypted HTTPS Connection"
                else -> "Unencrypted HTTP Connection"
              },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text =
              if (tab.isSpeedDial) "No history • No disk cache"
              else "${extractHost(tab.url)} • Ephemeral session",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      // Live Page Telemetry Strip
      Surface(
        shape = RoundedCornerShape(16.dp),
        color = voltColors.cardSurface,
        border = BorderStroke(1.dp, voltColors.cardBorder),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(14.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column {
            Text(
              text = "BLOCKED",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = "${tab.trackersBlockedOnPage}",
              style = MaterialTheme.typography.titleMedium,
              color = voltColors.emeraldShield,
            )
          }
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = "DATA USED",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = formatBytes(sessionDataUsedBytes),
              style = MaterialTheme.typography.titleMedium,
              color = voltColors.cyanAccent,
            )
          }
          Column(horizontalAlignment = Alignment.End) {
            Text(
              text = "LOAD TIME",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = tab.lastLoadTimeMs?.let { "${it} ms" } ?: "—",
              style = MaterialTheme.typography.titleMedium,
              color = voltColors.amberAccent,
            )
          }
        }
      }

      // AdGuard & Blocker Type Selector Card
      Surface(
        shape = RoundedCornerShape(18.dp),
        color = voltColors.cardSurface,
        border = BorderStroke(1.dp, voltColors.emeraldShield.copy(alpha = 0.42f)),
        modifier = Modifier.fillMaxWidth().testTag("ad_blocker_type_card"),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Default.Shield,
              contentDescription = null,
              tint = voltColors.emeraldShield,
              modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "AdGuard & Blocker Engine",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text = settings.adBlockerType.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            AdBlockerType.entries.forEach { blocker ->
              val selected = settings.adBlockerType == blocker
              val accent =
                when (blocker) {
                  AdBlockerType.OFF -> MaterialTheme.colorScheme.onSurfaceVariant
                  AdBlockerType.STANDARD -> voltColors.emeraldShield
                  AdBlockerType.ADGUARD -> voltColors.emeraldShield
                  AdBlockerType.STRICT -> voltColors.cyanAccent
                  AdBlockerType.STEALTH -> voltColors.cyanAccent
                }
              Surface(
                shape = RoundedCornerShape(12.dp),
                color =
                  if (selected) accent.copy(alpha = 0.18f)
                  else MaterialTheme.colorScheme.surfaceContainerHigh,
                border =
                  BorderStroke(
                    width = if (selected) 1.5.dp else 1.dp,
                    color = if (selected) accent else MaterialTheme.colorScheme.outlineVariant,
                  ),
                modifier =
                  Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelectAdBlockerType(blocker) }
                    .testTag("blocker_type_${blocker.name.lowercase()}"),
              ) {
                Row(
                  modifier = Modifier.padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                      Text(
                        text = blocker.displayName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
                      )
                      Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = accent.copy(alpha = 0.2f),
                      ) {
                        Text(
                          text = blocker.badgeLabel,
                          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                          color = accent,
                          modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                      }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                      text = blocker.subtitle,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  if (selected) {
                    Icon(
                      imageVector = Icons.Default.Check,
                      contentDescription = null,
                      tint = accent,
                      modifier = Modifier.size(20.dp),
                    )
                  }
                }
              }
            }
          }
        }
      }

      SettingToggleRow(
        icon = Icons.Default.Speed,
        title = "Lite Text-First Boost",
        subtitle = "Skip remote images & fonts for ultra-fast mobile loading",
        checked = settings.liteModeEnabled,
        onCheckedChange = onToggleLiteMode,
      )
      SettingToggleRow(
        icon = Icons.Default.Lock,
        title = "HTTPS-Only Mode",
        subtitle = "Automatically upgrade http:// sites to https:// and warn if unavailable",
        checked = settings.httpsOnlyEnabled,
        onCheckedChange = onToggleHttpsOnly,
        switchTag = "https_only_switch",
      )
      SettingToggleRow(
        icon = Icons.Default.DarkMode,
        title = "Dark Web Rendering",
        subtitle = "Apply algorithmic dark theme to web pages",
        checked = settings.darkWebEnabled,
        onCheckedChange = onToggleDarkWeb,
      )

      // Per-Site Media Compatibility Bypass Card
      val siteHost = if (tab.isSpeedDial) "" else extractHost(tab.url)
      Surface(
        shape = RoundedCornerShape(16.dp),
        color =
          if (tab.isMediaCompatBypassEnabled) voltColors.amberAccent.copy(alpha = 0.12f)
          else voltColors.cardSurface,
        border =
          BorderStroke(
            1.dp,
            if (tab.isMediaCompatBypassEnabled) voltColors.amberAccent.copy(alpha = 0.55f)
            else voltColors.cardBorder,
          ),
        modifier = Modifier.fillMaxWidth().testTag("site_media_bypass_card"),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Icon(
            imageVector = Icons.Default.PlayCircle,
            contentDescription = null,
            tint =
              if (tab.isMediaCompatBypassEnabled) voltColors.amberAccent
              else voltColors.cyanAccent,
          )
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Site Media Compatibility Bypass",
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text =
                when {
                  tab.isSpeedDial -> "Open any media page to toggle per-site shield & player bypass"
                  tab.isMediaCompatBypassEnabled ->
                    "Active on $siteHost — blockers/Data Saver paused & protected HTML5 players allowed"
                  else ->
                    "Pause blockers & enable protected HTML5 media playback if $siteHost fails to play"
                },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Switch(
            checked = tab.isMediaCompatBypassEnabled,
            enabled = !tab.isSpeedDial,
            onCheckedChange = { onToggleSiteMediaBypass() },
            modifier = Modifier.testTag("site_media_bypass_switch"),
          )
        }
      }

      OutlinedButton(
        onClick = onClearSitePermissions,
        modifier = Modifier.fillMaxWidth().testTag("clear_site_permissions_button"),
      ) {
        Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Reset Saved Site Permissions (Camera, Mic, Location)")
      }

      OutlinedButton(
        onClick = {
          onWipeSessionNow()
          onDismiss()
        },
        modifier = Modifier.fillMaxWidth().testTag("wipe_session_button"),
      ) {
        Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Wipe Active Session Cookies & Cache")
      }

      Spacer(modifier = Modifier.height(12.dp))
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrarySettingsSheet(
  activeTab: BrowserTabState,
  isBookmarked: Boolean,
  isBrowserFullscreen: Boolean,
  settings: BrowserSettings,
  bookmarks: List<BookmarkEntity>,
  onToggleBookmark: () -> Unit,
  onSharePage: () -> Unit,
  onOpenFindInPage: () -> Unit,
  onToggleDesktopMode: (Boolean) -> Unit,
  onToggleBrowserFullscreen: () -> Unit,
  onOpenSecuritySheet: () -> Unit,
  onOpenUrl: (String) -> Unit,
  onDeleteBookmark: (Long) -> Unit,
  onToggleLiteMode: (Boolean) -> Unit,
  onSelectSearchEngine: (SearchEngine) -> Unit,
  onSelectThemeMode: (AppThemeMode) -> Unit,
  onDismiss: () -> Unit,
) {
  val voltColors = LocalVoltColors.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var selectedTab by remember { mutableIntStateOf(0) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().heightIn(min = 420.dp, max = 660.dp).padding(bottom = 16.dp)
    ) {
      // Standardized Page Commands Row (No duplicate Media Hub or Exit)
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        QuickActionChip(
          icon = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
          label = if (isBookmarked) "Saved" else "Bookmark",
          enabled = !activeTab.isSpeedDial,
          isActive = isBookmarked,
          onClick = {
            onToggleBookmark()
            onDismiss()
          },
          modifier = Modifier.weight(1f),
        )
        QuickActionChip(
          icon = Icons.Default.FindInPage,
          label = "Find",
          enabled = !activeTab.isSpeedDial,
          onClick = {
            onDismiss()
            onOpenFindInPage()
          },
          modifier = Modifier.weight(1f),
        )
        QuickActionChip(
          icon = Icons.Default.DesktopWindows,
          label = "Desktop",
          enabled = true,
          isActive = settings.desktopModeEnabled,
          onClick = {
            onToggleDesktopMode(!settings.desktopModeEnabled)
            onDismiss()
          },
          modifier = Modifier.weight(1f).testTag("menu_desktop_mode_button"),
        )
        QuickActionChip(
          icon = if (isBrowserFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
          label = if (isBrowserFullscreen) "Window" else "Fullscreen",
          enabled = true,
          isActive = isBrowserFullscreen,
          onClick = {
            onDismiss()
            onToggleBrowserFullscreen()
          },
          modifier = Modifier.weight(1f).testTag("menu_fullscreen_button"),
        )
        QuickActionChip(
          icon = Icons.Default.Share,
          label = "Share",
          enabled = !activeTab.isSpeedDial,
          onClick = {
            onDismiss()
            onSharePage()
          },
          modifier = Modifier.weight(1f),
        )
      }

      Spacer(modifier = Modifier.height(8.dp))

      TabRow(selectedTabIndex = selectedTab, containerColor = Color.Transparent) {
        Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Bookmarks") })
        Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Engine & Look") })
      }

      when (selectedTab) {
        0 -> {
          LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            if (bookmarks.isEmpty()) {
              item {
                Text(
                  text = "No bookmarks saved yet.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            items(bookmarks, key = { it.id }) { bm ->
              Surface(
                shape = RoundedCornerShape(14.dp),
                color = voltColors.cardSurface,
                border = BorderStroke(1.dp, voltColors.cardBorder),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable {
                  onOpenUrl(bm.url)
                  onDismiss()
                },
              ) {
                Row(
                  modifier = Modifier.padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = bm.title,
                      style = MaterialTheme.typography.titleSmall,
                      color = MaterialTheme.colorScheme.onSurface,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                      text = bm.url,
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                  IconButton(onClick = { onDeleteBookmark(bm.id) }) {
                    Icon(
                      imageVector = Icons.Default.DeleteOutline,
                      contentDescription = "Delete bookmark",
                      tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              }
            }
          }
        }

        else -> {
          LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            item {
              Surface(
                shape = RoundedCornerShape(14.dp),
                color = voltColors.emeraldShield.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, voltColors.emeraldShield.copy(alpha = 0.4f)),
              ) {
                Column(
                  modifier = Modifier.fillMaxWidth().padding(14.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Text(
                    text = "Zero-History & Zero-Cache Mode Enforced",
                    style = MaterialTheme.typography.titleSmall,
                    color = voltColors.emeraldShield,
                  )
                  Text(
                    text =
                      "Browsing history is never saved, WebView disk cache is disabled (LOAD_NO_CACHE), and session cookies/storage are wiped when tabs close.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }

            // Blocker quick link (no duplicate 4-way selector — points to Security Sheet)
            item {
              Surface(
                shape = RoundedCornerShape(14.dp),
                color = voltColors.cardSurface,
                border = BorderStroke(1.dp, voltColors.cardBorder),
                modifier =
                  Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                      onDismiss()
                      onOpenSecuritySheet()
                    },
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(14.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = "AdGuard & Blocker Profile",
                      style = MaterialTheme.typography.titleSmall,
                      color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                      text = "${settings.adBlockerType.displayName} • ${settings.cumulativeTrackersBlocked} blocked • ${formatBytes(settings.cumulativeBytesSaved)} saved",
                      style = MaterialTheme.typography.bodySmall,
                      color = voltColors.emeraldShield,
                    )
                  }
                  Text(
                    text = settings.adBlockerType.badgeLabel,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = voltColors.cyanAccent,
                  )
                }
              }
            }

            item {
              Surface(
                shape = RoundedCornerShape(14.dp),
                color = voltColors.cyanAccent.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, voltColors.cyanAccent.copy(alpha = 0.4f)),
              ) {
                Column(
                  modifier = Modifier.fillMaxWidth().padding(14.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = "Super Data Saver Mode",
                        style = MaterialTheme.typography.titleMedium,
                        color = voltColors.cyanAccent,
                      )
                      Text(
                        text = "Total Data Saved: ${formatBytes(settings.cumulativeBytesSaved)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                      )
                    }
                    Switch(
                      checked = settings.liteModeEnabled,
                      onCheckedChange = onToggleLiteMode,
                      modifier = Modifier.testTag("data_saver_switch"),
                    )
                  }
                  Text(
                    text = "Aggressively blocks remote images, webfonts, and heavy tracking payloads to save up to 70% mobile bandwidth.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }

            // Canonical App Theme Selector (Single Source — no duplicate on Speed Dial)
            item {
              Text(
                text = "App Theme & Look",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Spacer(modifier = Modifier.height(8.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                AppThemeMode.entries.forEach { mode ->
                  val selected = settings.themeMode == mode
                  val swatchColor =
                    if (mode == AppThemeMode.CYBER_OBSIDIAN) VoltCyan else Color(0xFF0284C7)
                  val cardShape = RoundedCornerShape(14.dp)
                  Surface(
                    shape = cardShape,
                    color =
                      if (selected) voltColors.cyanAccent.copy(alpha = 0.14f)
                      else voltColors.cardSurface,
                    border =
                      BorderStroke(
                        width = if (selected) 1.5.dp else 1.dp,
                        color = if (selected) voltColors.cyanAccent else voltColors.cardBorder,
                      ),
                    modifier =
                      Modifier.weight(1f)
                        .clip(cardShape)
                        .clickable { onSelectThemeMode(mode) }
                        .testTag("settings_theme_${mode.name.lowercase()}"),
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                      Box(
                        modifier =
                          Modifier.size(12.dp)
                            .clip(CircleShape)
                            .background(swatchColor)
                      )
                      Text(
                        text = mode.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                      )
                      if (selected) {
                        Icon(
                          imageVector = Icons.Default.Check,
                          contentDescription = "Selected",
                          tint = voltColors.cyanAccent,
                          modifier = Modifier.size(16.dp),
                        )
                      }
                    }
                  }
                }
              }
            }

            item {
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "Default Search Engine",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Spacer(modifier = Modifier.height(6.dp))
              SearchEngine.entries.forEach { engine ->
                val selected = settings.searchEngine == engine
                val cardShape = RoundedCornerShape(14.dp)
                Surface(
                  shape = cardShape,
                  color =
                    if (selected) voltColors.cyanAccent.copy(alpha = 0.14f)
                    else voltColors.cardSurface,
                  modifier =
                    Modifier.fillMaxWidth()
                      .padding(vertical = 3.dp)
                      .clip(cardShape)
                      .clickable { onSelectSearchEngine(engine) },
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Column {
                      Text(
                        text = engine.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                      )
                      Text(
                        text = engine.domain,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                    if (selected) {
                      Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = voltColors.cyanAccent,
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
  }
}

@Composable
private fun QuickActionChip(
  icon: ImageVector,
  label: String,
  enabled: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  isActive: Boolean = false,
  tint: Color? = null,
) {
  val voltColors = LocalVoltColors.current
  val activeTint = tint ?: voltColors.cyanAccent
  Surface(
    modifier =
      modifier
        .clip(RoundedCornerShape(14.dp))
        .clickable(enabled = enabled, onClick = onClick),
    shape = RoundedCornerShape(14.dp),
    color = if (isActive) activeTint.copy(alpha = 0.16f) else voltColors.cardSurface,
    border =
      if (isActive) BorderStroke(1.dp, activeTint.copy(alpha = 0.55f))
      else BorderStroke(1.dp, voltColors.cardBorder),
  ) {
    Column(
      modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint =
          if (enabled) activeTint
          else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.size(19.dp),
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color =
          if (enabled) MaterialTheme.colorScheme.onSurface
          else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun SettingToggleRow(
  icon: ImageVector,
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  switchTag: String? = null,
) {
  val voltColors = LocalVoltColors.current
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Icon(imageVector = icon, contentDescription = null, tint = voltColors.cyanAccent)
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      modifier = if (switchTag != null) Modifier.testTag(switchTag) else Modifier,
    )
  }
}

@Composable
fun AddSpeedDialDialog(
  onConfirm: (url: String, title: String) -> Unit,
  onDismiss: () -> Unit,
) {
  var title by remember { mutableStateOf("") }
  var url by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Pin to Speed Dial") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text("Site name") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("pin_title_input"),
        )
        OutlinedTextField(
          value = url,
          onValueChange = { url = it },
          label = { Text("Website URL (e.g. wikipedia.org)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("pin_url_input"),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirm(url, title) },
        enabled = url.isNotBlank(),
        modifier = Modifier.testTag("confirm_pin_button"),
      ) {
        Text("Pin Site")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@Composable
fun ExitConfirmDialog(
  isErasing: Boolean,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = { if (!isErasing) onDismiss() },
    icon = {
      Icon(
        imageVector = Icons.Default.PowerSettingsNew,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error,
      )
    },
    title = { Text("Exit & Erase Everything?") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text =
            "Closes all tabs, stops downloads, media playback, and proxy/VPN tunnels, and erases all browser caches, cookies, WebView storage, temporary files, and app data on this device.",
          style = MaterialTheme.typography.bodyMedium,
        )
        if (isErasing) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Text("Erasing caches & device traces...", style = MaterialTheme.typography.labelMedium)
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = onConfirm,
        enabled = !isErasing,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
          ),
        modifier = Modifier.testTag("confirm_exit_erase_button"),
      ) {
        Text(if (isErasing) "Erasing..." else "Exit & Erase")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss, enabled = !isErasing) {
        Text("Cancel")
      }
    },
  )
}
