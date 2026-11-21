package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.BookmarkEntity
import com.example.data.BrowserSettings
import com.example.data.BrowserTabState
import com.example.data.SearchEngine
import com.example.ui.components.extractHost
import com.example.ui.components.formatBytes
import com.example.ui.theme.LocalVoltColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityTelemetrySheet(
  tab: BrowserTabState,
  settings: BrowserSettings,
  onToggleTurboShield: (Boolean) -> Unit,
  onToggleLiteMode: (Boolean) -> Unit,
  onToggleDarkWeb: (Boolean) -> Unit,
  onToggleDesktopMode: (Boolean) -> Unit,
  onWipeSessionNow: () -> Unit,
  onRequestExit: () -> Unit,
  onDismiss: () -> Unit,
) {
  val voltColors = LocalVoltColors.current

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
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
                tab.isSpeedDial -> "Zero-Trace Engine Ready"
                tab.isSecure -> "Encrypted HTTPS Connection"
                else -> "Unencrypted HTTP Connection"
              },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text =
              if (tab.isSpeedDial) "No history • No disk cache"
              else "${extractHost(tab.url)} • No history/cache",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Surface(
        shape = RoundedCornerShape(16.dp),
        color = voltColors.cardSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, voltColors.cardBorder),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(14.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column {
            Text(
              text = "TRACKERS BLOCKED",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = "${tab.trackersBlockedOnPage} on page",
              style = MaterialTheme.typography.titleMedium,
              color = voltColors.emeraldShield,
            )
          }
          Column(horizontalAlignment = Alignment.End) {
            Text(
              text = "PAGE LOAD TIME",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = tab.lastLoadTimeMs?.let { "${it} ms" } ?: "—",
              style = MaterialTheme.typography.titleMedium,
              color = voltColors.cyanAccent,
            )
          }
        }
      }

      SettingToggleRow(
        icon = Icons.Default.Shield,
        title = "Turbo Shield",
        subtitle = "Block ad networks, tracking pixels & heavy scripts",
        checked = settings.turboShieldEnabled,
        onCheckedChange = onToggleTurboShield,
      )
      SettingToggleRow(
        icon = Icons.Default.Speed,
        title = "Lite Text-First Boost",
        subtitle = "Skip remote images for ultra-fast mobile loading",
        checked = settings.liteModeEnabled,
        onCheckedChange = onToggleLiteMode,
      )
      SettingToggleRow(
        icon = Icons.Default.DarkMode,
        title = "Dark Web Rendering",
        subtitle = "Apply algorithmic dark theme to web pages",
        checked = settings.darkWebEnabled,
        onCheckedChange = onToggleDarkWeb,
      )
      SettingToggleRow(
        icon = Icons.Default.DesktopWindows,
        title = "Desktop Site Mode",
        subtitle = "Request desktop layout & reload page",
        checked = settings.desktopModeEnabled,
        onCheckedChange = onToggleDesktopMode,
      )

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

      Button(
        onClick = onRequestExit,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
          ),
        modifier = Modifier.fillMaxWidth().testTag("security_exit_erase_button"),
      ) {
        Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Exit & Erase All Data")
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
  settings: BrowserSettings,
  bookmarks: List<BookmarkEntity>,
  onToggleBookmark: () -> Unit,
  onSharePage: () -> Unit,
  onOpenFindInPage: () -> Unit,
  onOpenMediaHub: () -> Unit,
  onOpenUrl: (String) -> Unit,
  onDeleteBookmark: (Long) -> Unit,
  onSelectSearchEngine: (SearchEngine) -> Unit,
  onWipeSessionNow: () -> Unit,
  onRequestExit: () -> Unit,
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
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        QuickActionChip(
          icon = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
          label = if (isBookmarked) "Saved" else "Bookmark",
          enabled = !activeTab.isSpeedDial,
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
          icon = Icons.Default.FolderOpen,
          label = "Media Hub",
          enabled = true,
          onClick = {
            onDismiss()
            onOpenMediaHub()
          },
          modifier = Modifier.weight(1f),
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
        QuickActionChip(
          icon = Icons.Default.PowerSettingsNew,
          label = "Exit",
          enabled = true,
          tint = MaterialTheme.colorScheme.error,
          onClick = onRequestExit,
          modifier = Modifier.weight(1f).testTag("menu_exit_erase_button"),
        )
      }

      Spacer(modifier = Modifier.height(8.dp))

      TabRow(selectedTabIndex = selectedTab, containerColor = Color.Transparent) {
        Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Bookmarks") })
        Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Zero-Trace Engine") })
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
                border =
                  androidx.compose.foundation.BorderStroke(
                    1.dp,
                    voltColors.emeraldShield.copy(alpha = 0.4f),
                  ),
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

            item {
              Text(
                text = "Default Search Engine",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Spacer(modifier = Modifier.height(6.dp))
              SearchEngine.entries.forEach { engine ->
                val selected = settings.searchEngine == engine
                Surface(
                  shape = RoundedCornerShape(14.dp),
                  color =
                    if (selected) voltColors.cyanAccent.copy(alpha = 0.14f)
                    else voltColors.cardSurface,
                  modifier =
                    Modifier.fillMaxWidth()
                      .padding(vertical = 3.dp)
                      .clip(RoundedCornerShape(14.dp))
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

            item {
              HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
              Spacer(modifier = Modifier.height(8.dp))
              OutlinedButton(
                onClick = {
                  onWipeSessionNow()
                  onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
              ) {
                Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Wipe Session Cookies & Cache")
              }
              Spacer(modifier = Modifier.height(8.dp))
              Button(
                onClick = onRequestExit,
                colors =
                  ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                  ),
                modifier = Modifier.fillMaxWidth().testTag("settings_exit_erase_button"),
              ) {
                Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Exit & Erase All Device Traces")
              }
              Spacer(modifier = Modifier.height(8.dp))
              Surface(
                shape = RoundedCornerShape(14.dp),
                color = voltColors.cardSurface,
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(14.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Text(
                    text = "Shield Savings",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Text(
                    text =
                      "${settings.cumulativeTrackersBlocked} blocked • ${formatBytes(settings.cumulativeBytesSaved)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = voltColors.emeraldShield,
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

@Composable
private fun QuickActionChip(
  icon: ImageVector,
  label: String,
  enabled: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
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
    color = voltColors.cardSurface,
  ) {
    Column(
      modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint =
          if (enabled) activeTint
          else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.size(20.dp),
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color =
          if (enabled) MaterialTheme.colorScheme.onSurface
          else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        maxLines = 1,
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
    Switch(checked = checked, onCheckedChange = onCheckedChange)
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
