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
import androidx.compose.material.icons.filled.DataUsage
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.AppThemeMode
import com.example.data.BookmarkEntity
import com.example.data.BrowserSettings
import com.example.data.BrowserTabState
import com.example.data.SearchEngine
import com.example.ui.components.extractHost
import com.example.ui.components.formatBytes
import com.example.ui.components.formatCompactDataCounter
import com.example.ui.theme.AlabasterCanvas
import com.example.ui.theme.AlabasterCoral
import com.example.ui.theme.EmberCoral
import com.example.ui.theme.GraphiteCanvas
import com.example.ui.theme.LocalVoltColors
import com.example.ui.theme.badgeAccentColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityTelemetrySheet(
  tab: BrowserTabState,
  settings: BrowserSettings,
  sessionDataUsedBytes: Long = 0L,
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
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      // Connection Banner
      val isSecureState = tab.isSpeedDial || tab.isSecure
      val stateTint = if (isSecureState) voltColors.emeraldShield else voltColors.amberAccent
      Surface(
        shape = RoundedCornerShape(20.dp),
        color = stateTint.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, stateTint.copy(alpha = 0.35f)),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Box(
            modifier =
              Modifier.size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(stateTint.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = if (isSecureState) Icons.Default.Lock else Icons.Default.Warning,
              contentDescription = null,
              tint = stateTint,
              modifier = Modifier.size(22.dp),
            )
          }
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text =
                when {
                  tab.isSpeedDial -> "Zero-Trace Session Active"
                  tab.isSecure -> "Encrypted HTTPS Connection"
                  else -> "Unencrypted HTTP Page"
                },
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text =
                if (tab.isSpeedDial) "No browsing history • Ephemeral workspace"
                else "${extractHost(tab.url)} • ${if (tab.isIncognito) "Private tab" else "Standard tab"}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }

      // Live Telemetry Row (No dead blocker metrics!)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        TelemetryCard(
          label = "SESSION DATA",
          value = formatBytes(sessionDataUsedBytes),
          tint = voltColors.brandAccent,
          modifier = Modifier.weight(1f),
        )
        TelemetryCard(
          label = "SAVED TOTAL",
          value = formatBytes(settings.cumulativeBytesSaved),
          tint = voltColors.emeraldShield,
          modifier = Modifier.weight(1f),
        )
        TelemetryCard(
          label = "LOAD TIME",
          value = tab.lastLoadTimeMs?.let { "${it} ms" } ?: "—",
          tint = voltColors.amberAccent,
          modifier = Modifier.weight(1f),
        )
      }

      // Grouped Privacy & Render Toggles
      Surface(
        shape = RoundedCornerShape(20.dp),
        color = voltColors.cardSurface,
        border = BorderStroke(1.dp, voltColors.cardBorder),
      ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
          SettingToggleRow(
            icon = Icons.Default.Speed,
            title = "Lite Text-First Boost",
            subtitle = "Skip remote images & fonts for ultra-fast mobile loading",
            checked = settings.liteModeEnabled,
            onCheckedChange = onToggleLiteMode,
          )
          HorizontalDivider(color = voltColors.cardBorder.copy(alpha = 0.6f))
          SettingToggleRow(
            icon = Icons.Default.Lock,
            title = "HTTPS-Only Mode",
            subtitle = "Upgrade http:// connections to https:// and warn if unavailable",
            checked = settings.httpsOnlyEnabled,
            onCheckedChange = onToggleHttpsOnly,
            switchTag = "https_only_switch",
          )
          HorizontalDivider(color = voltColors.cardBorder.copy(alpha = 0.6f))
          SettingToggleRow(
            icon = Icons.Default.DarkMode,
            title = "Dark Web Rendering",
            subtitle = "Apply algorithmic dark theme to web pages",
            checked = settings.darkWebEnabled,
            onCheckedChange = onToggleDarkWeb,
          )
        }
      }

      // Per-Site Media Compatibility Bypass Card
      val siteHost = if (tab.isSpeedDial) "" else extractHost(tab.url)
      Surface(
        shape = RoundedCornerShape(18.dp),
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
              else voltColors.brandAccent,
          )
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Site Media Compatibility Bypass",
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text =
                when {
                  tab.isSpeedDial -> "Open any media page to toggle compatibility bypass"
                  tab.isMediaCompatBypassEnabled ->
                    "Active on $siteHost — Data Saver paused & protected HTML5 players allowed"
                  else ->
                    "Pause Data Saver & allow protected HTML5 media playback on $siteHost"
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
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("clear_site_permissions_button"),
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
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("wipe_session_button"),
      ) {
        Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Wipe Active Session Cookies & Cache")
      }

      Spacer(modifier = Modifier.height(10.dp))
    }
  }
}

@Composable
private fun TelemetryCard(
  label: String,
  value: String,
  tint: Color,
  modifier: Modifier = Modifier,
) {
  val voltColors = LocalVoltColors.current
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(16.dp),
    color = voltColors.cardSurface,
    border = BorderStroke(1.dp, voltColors.cardBorder),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
      )
      Text(
        text = value,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = tint,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrarySettingsSheet(
  activeTab: BrowserTabState,
  isBookmarked: Boolean,
  isBrowserFullscreen: Boolean,
  sessionDataUsedBytes: Long,
  settings: BrowserSettings,
  bookmarks: List<BookmarkEntity>,
  onToggleBookmark: () -> Unit,
  onSharePage: () -> Unit,
  onOpenFindInPage: () -> Unit,
  onToggleDesktopMode: (Boolean) -> Unit,
  onToggleBrowserFullscreen: () -> Unit,
  onOpenSecuritySheet: () -> Unit,
  onRequestExit: () -> Unit,
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
      modifier = Modifier.fillMaxWidth().heightIn(min = 440.dp, max = 690.dp).padding(bottom = 16.dp)
    ) {
      // Top Control Bar: Session Data Badge + Exit & Erase Everything
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Surface(
          shape = RoundedCornerShape(100.dp),
          color = voltColors.cardSurface,
          border = BorderStroke(1.dp, voltColors.cardBorder),
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .clip(RoundedCornerShape(100.dp))
              .clickable {
                onDismiss()
                onOpenSecuritySheet()
              }
              .testTag("nav_data_usage_counter"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = Icons.Default.DataUsage,
              contentDescription = null,
              tint = voltColors.brandAccent,
              modifier = Modifier.size(16.dp),
            )
            Text(
              text = "Session ${formatCompactDataCounter(sessionDataUsedBytes)}",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
        }

        Surface(
          shape = RoundedCornerShape(100.dp),
          color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .clip(RoundedCornerShape(100.dp))
              .clickable {
                onDismiss()
                onRequestExit()
              }
              .testTag("nav_exit_button"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = Icons.Default.PowerSettingsNew,
              contentDescription = "Exit & Erase Everything",
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.size(16.dp),
            )
            Text(
              text = "Exit & Erase",
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
              color = MaterialTheme.colorScheme.error,
            )
          }
        }
      }

      // 2x3 Architectural Command Grid
      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          CommandGridTile(
            icon = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
            label = if (isBookmarked) "Bookmarked" else "Bookmark",
            enabled = !activeTab.isSpeedDial,
            isActive = isBookmarked,
            onClick = {
              onToggleBookmark()
              onDismiss()
            },
            modifier = Modifier.weight(1f),
          )
          CommandGridTile(
            icon = Icons.Default.FindInPage,
            label = "Find",
            enabled = !activeTab.isSpeedDial,
            onClick = {
              onDismiss()
              onOpenFindInPage()
            },
            modifier = Modifier.weight(1f),
          )
          CommandGridTile(
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
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          CommandGridTile(
            icon = if (isBrowserFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
            label = if (isBrowserFullscreen) "Exit Full" else "Fullscreen",
            enabled = true,
            isActive = isBrowserFullscreen,
            onClick = {
              onDismiss()
              onToggleBrowserFullscreen()
            },
            modifier = Modifier.weight(1f).testTag("menu_fullscreen_button"),
          )
          CommandGridTile(
            icon = Icons.Default.Share,
            label = "Share",
            enabled = !activeTab.isSpeedDial,
            onClick = {
              onDismiss()
              onSharePage()
            },
            modifier = Modifier.weight(1f),
          )
          CommandGridTile(
            icon = Icons.Default.Shield,
            label = "Privacy",
            enabled = true,
            onClick = {
              onDismiss()
              onOpenSecuritySheet()
            },
            modifier = Modifier.weight(1f),
          )
        }
      }

      Spacer(modifier = Modifier.height(6.dp))

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
          listOf("Bookmarks (${bookmarks.size})", "Engine & Theme").forEachIndexed { idx, label ->
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
                modifier = Modifier.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = label,
                  style = MaterialTheme.typography.labelLarge,
                  color =
                    if (selected) voltColors.onBrandAccent
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
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
                  text = "No bookmarks saved yet. Tap Bookmark while viewing any page.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            items(bookmarks, key = { it.id }) { bm ->
              Surface(
                shape = RoundedCornerShape(16.dp),
                color = voltColors.cardSurface,
                border = BorderStroke(1.dp, voltColors.cardBorder),
                modifier =
                  Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                      onOpenUrl(bm.url)
                      onDismiss()
                    },
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  Box(
                    modifier =
                      Modifier.size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(voltColors.brandAccent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                  ) {
                    Text(
                      text = bm.title.firstOrNull()?.uppercaseChar()?.toString() ?: "B",
                      style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                      color = voltColors.brandAccent,
                    )
                  }
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            item {
              Surface(
                shape = RoundedCornerShape(18.dp),
                color = voltColors.emeraldShield.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, voltColors.emeraldShield.copy(alpha = 0.35f)),
              ) {
                Column(
                  modifier = Modifier.fillMaxWidth().padding(14.dp),
                  verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Text(
                    text = "Zero-History & Ephemeral Storage",
                    style = MaterialTheme.typography.titleSmall,
                    color = voltColors.emeraldShield,
                  )
                  Text(
                    text =
                      "Browsing history is never saved, Private tabs stay strictly off-disk, and session cookies/storage are cleared on Exit & Erase.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }

            item {
              Surface(
                shape = RoundedCornerShape(18.dp),
                color = voltColors.brandAccent.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, voltColors.brandAccent.copy(alpha = 0.35f)),
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
                        color = voltColors.brandAccent,
                      )
                      Text(
                        text = "Saved so far: ${formatBytes(settings.cumulativeBytesSaved)}",
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
                    text = "Skips remote images and webfonts to dramatically reduce mobile data consumption.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }

            // Dual Theme Cards with Live Mini Swatch
            item {
              Text(
                text = "Appearance",
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
                  val bgSwatch =
                    if (mode == AppThemeMode.CYBER_OBSIDIAN) GraphiteCanvas else AlabasterCanvas
                  val accentSwatch =
                    if (mode == AppThemeMode.CYBER_OBSIDIAN) EmberCoral else AlabasterCoral
                  val cardShape = RoundedCornerShape(16.dp)

                  Surface(
                    shape = cardShape,
                    color =
                      if (selected) voltColors.brandAccent.copy(alpha = 0.14f)
                      else voltColors.cardSurface,
                    border =
                      BorderStroke(
                        width = if (selected) 1.5.dp else 1.dp,
                        color = if (selected) voltColors.brandAccent else voltColors.cardBorder,
                      ),
                    modifier =
                      Modifier.weight(1f)
                        .clip(cardShape)
                        .clickable { onSelectThemeMode(mode) }
                        .testTag("settings_theme_${mode.name.lowercase()}"),
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                      Box(
                        modifier =
                          Modifier.size(22.dp)
                            .clip(CircleShape)
                            .background(bgSwatch),
                        contentAlignment = Alignment.Center,
                      ) {
                        Box(
                          modifier =
                            Modifier.size(10.dp)
                              .clip(CircleShape)
                              .background(accentSwatch)
                        )
                      }
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
                          tint = voltColors.brandAccent,
                          modifier = Modifier.size(16.dp),
                        )
                      }
                    }
                  }
                }
              }
            }

            item {
              Text(
                text = "Default Search Engine",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Spacer(modifier = Modifier.height(6.dp))
              SearchEngine.entries.forEach { engine ->
                val selected = settings.searchEngine == engine
                val engineAccent = engine.badgeAccentColor(voltColors.isLightTheme)
                val cardShape = RoundedCornerShape(14.dp)
                Surface(
                  shape = cardShape,
                  color =
                    if (selected) voltColors.brandAccent.copy(alpha = 0.12f)
                    else voltColors.cardSurface,
                  border =
                    BorderStroke(
                      1.dp,
                      if (selected) voltColors.brandAccent.copy(alpha = 0.55f)
                      else voltColors.cardBorder,
                    ),
                  modifier =
                    Modifier.fillMaxWidth()
                      .padding(vertical = 3.dp)
                      .clip(cardShape)
                      .clickable { onSelectSearchEngine(engine) },
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Box(
                      modifier =
                        Modifier.size(28.dp)
                          .clip(RoundedCornerShape(8.dp))
                          .background(engineAccent.copy(alpha = 0.18f)),
                      contentAlignment = Alignment.Center,
                    ) {
                      Text(
                        text = engine.badgeCode,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = engineAccent,
                      )
                    }
                    Column(modifier = Modifier.weight(1f)) {
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
                        tint = voltColors.brandAccent,
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
private fun CommandGridTile(
  icon: ImageVector,
  label: String,
  enabled: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  isActive: Boolean = false,
) {
  val voltColors = LocalVoltColors.current
  val activeTint = voltColors.brandAccent
  Surface(
    modifier =
      modifier
        .minimumInteractiveComponentSize()
        .clip(RoundedCornerShape(16.dp))
        .clickable(enabled = enabled, onClick = onClick),
    shape = RoundedCornerShape(16.dp),
    color = if (isActive) activeTint.copy(alpha = 0.15f) else voltColors.cardSurface,
    border =
      BorderStroke(
        width = 1.dp,
        color = if (isActive) activeTint.copy(alpha = 0.55f) else voltColors.cardBorder,
      ),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint =
          when {
            !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            isActive -> activeTint
            else -> MaterialTheme.colorScheme.onSurface
          },
        modifier = Modifier.size(18.dp),
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color =
          if (enabled) MaterialTheme.colorScheme.onSurface
          else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
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
    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Icon(imageVector = icon, contentDescription = null, tint = voltColors.brandAccent)
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
    title = { Text("Pin to Workspace") },
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
