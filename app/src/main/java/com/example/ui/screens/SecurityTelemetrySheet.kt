package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.BrowserSettings
import com.example.data.BrowserTabState
import com.example.ui.components.extractHost
import com.example.ui.components.formatBytes
import com.example.ui.theme.LocalVoltColors

/**
 * Modal bottom sheet presenting active connection security telemetry, global privacy toggles,
 * per-host overrides (JavaScript, Desktop Mode, Media Bypass), and instant session wipes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityTelemetrySheet(
  tab: BrowserTabState,
  settings: BrowserSettings,
  sessionDataUsedBytes: Long = 0L,
  onToggleLiteMode: (Boolean) -> Unit,
  onToggleHttpsOnly: (Boolean) -> Unit,
  onToggleDarkWeb: (Boolean) -> Unit,
  onToggleSiteJs: () -> Unit = {},
  onToggleSiteDesktop: () -> Unit = {},
  onToggleSiteMediaBypass: () -> Unit = {},
  onResetSiteSettings: () -> Unit = {},
  onClearSitePermissions: () -> Unit = {},
  onWipeSessionNow: () -> Unit,
  onDismiss: () -> Unit,
) {
  val voltColors = LocalVoltColors.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val siteHost = if (tab.isSpeedDial) "" else extractHost(tab.url)
  val effectiveSiteDesktop = tab.siteDesktopOverride ?: settings.desktopModeEnabled

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
      // Connection & Privacy Recents Banner
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
                when {
                  tab.isSpeedDial && tab.isIncognito ->
                    "Private tab • Ephemeral workspace • Recents screen protected"
                  tab.isSpeedDial ->
                    "No browsing history • Ephemeral workspace"
                  tab.isIncognito ->
                    "$siteHost • Private tab (Recents capture blocked)"
                  else ->
                    "$siteHost • Standard tab"
                },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }

      // Live Telemetry Row
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

      // Global Privacy & Render Toggles
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

      // Per-Site Overrides Card (JavaScript, Desktop Mode)
      Surface(
        shape = RoundedCornerShape(20.dp),
        color = voltColors.cardSurface,
        border = BorderStroke(1.dp, voltColors.cardBorder),
      ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text =
                if (tab.isSpeedDial) "Per-Site Controls" else "Site Controls • $siteHost",
              style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
              color = voltColors.brandAccent,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f),
            )
            if (!tab.isSpeedDial && (!tab.siteJsEnabled || tab.siteDesktopOverride != null || tab.isMediaCompatBypassEnabled)) {
              TextButton(
                onClick = onResetSiteSettings,
                modifier = Modifier.testTag("reset_site_settings_button"),
              ) {
                Text("Reset site")
              }
            }
          }
          SettingToggleRow(
            icon = Icons.Default.Code,
            title = "JavaScript Execution",
            subtitle =
              when {
                tab.isSpeedDial -> "Open any website to allow or block scripts per host"
                tab.siteJsEnabled -> "Allowed for $siteHost"
                else -> "Blocked for $siteHost (strict static mode)"
              },
            checked = tab.siteJsEnabled,
            enabled = !tab.isSpeedDial,
            onCheckedChange = { onToggleSiteJs() },
            switchTag = "site_js_switch",
          )
          HorizontalDivider(color = voltColors.cardBorder.copy(alpha = 0.6f))
          SettingToggleRow(
            icon = Icons.Default.DesktopWindows,
            title = "Desktop Site for This Host",
            subtitle =
              when {
                tab.isSpeedDial -> "Open any website to pin desktop or mobile layout"
                tab.siteDesktopOverride != null ->
                  if (effectiveSiteDesktop) "Pinned Desktop layout for $siteHost"
                  else "Pinned Mobile layout for $siteHost"
                else ->
                  "Following global mode (${if (settings.desktopModeEnabled) "Desktop" else "Mobile"})"
              },
            checked = effectiveSiteDesktop,
            enabled = !tab.isSpeedDial,
            onCheckedChange = { onToggleSiteDesktop() },
            switchTag = "site_desktop_switch",
          )
        }
      }

      // Per-Site Media Compatibility Bypass Card
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
