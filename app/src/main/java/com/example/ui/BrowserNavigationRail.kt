package com.example.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.data.BrowserTabState
import com.example.ui.theme.LocalVoltColors

/**
 * Adaptive side navigation rail displayed on Medium/Expanded screens (tablets & foldables).
 */
@Composable
internal fun BrowserNavigationRail(
  activeTab: BrowserTabState,
  tabCount: Int,
  activeSheet: BrowserSheet,
  isVpnActive: Boolean,
  bottomBarConsumesNavInset: Boolean,
  onHome: () -> Unit,
  onBack: () -> Unit,
  onForward: () -> Unit,
  onOpenTabs: () -> Unit,
  onOpenMediaHub: () -> Unit,
  onOpenVpnSheet: () -> Unit,
  onOpenMenu: () -> Unit,
  onRequestExit: () -> Unit,
) {
  val voltColors = LocalVoltColors.current

  NavigationRail(
    containerColor = voltColors.dockSurface,
    modifier =
      Modifier.statusBarsPadding().let {
        if (!bottomBarConsumesNavInset) it.navigationBarsPadding() else it
      },
  ) {
    Spacer(modifier = Modifier.weight(0.2f))
    NavigationRailItem(
      selected = activeTab.isSpeedDial,
      onClick = onHome,
      icon = { Icon(Icons.Default.Home, contentDescription = stringResource(R.string.cd_home)) },
      label = { Text("Start") },
    )
    NavigationRailItem(
      selected = false,
      onClick = onBack,
      enabled = !activeTab.isSpeedDial,
      icon = {
        Icon(
          Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = stringResource(R.string.cd_back),
        )
      },
      label = { Text("Back") },
    )
    NavigationRailItem(
      selected = false,
      onClick = onForward,
      enabled = activeTab.canGoForward,
      icon = {
        Icon(
          Icons.AutoMirrored.Filled.ArrowForward,
          contentDescription = stringResource(R.string.cd_forward),
        )
      },
      label = { Text("Fwd") },
    )
    NavigationRailItem(
      selected = activeSheet == BrowserSheet.TAB_SWITCHER,
      onClick = onOpenTabs,
      icon = {
        BadgedBox(badge = { Badge { Text("$tabCount") } }) {
          Icon(Icons.Default.Layers, contentDescription = stringResource(R.string.cd_tabs))
        }
      },
      label = { Text("Deck") },
    )
    NavigationRailItem(
      selected = activeSheet == BrowserSheet.MEDIA_HUB,
      onClick = onOpenMediaHub,
      icon = {
        BadgedBox(
          badge = {
            if (activeTab.detectedMedia.isNotEmpty()) {
              Badge { Text("${activeTab.detectedMedia.size}") }
            }
          }
        ) {
          Icon(
            Icons.Default.VideoLibrary,
            contentDescription = stringResource(R.string.cd_media_detected),
          )
        }
      },
      label = { Text("Media") },
    )
    NavigationRailItem(
      selected = activeSheet == BrowserSheet.VPN,
      onClick = onOpenVpnSheet,
      icon = {
        Icon(
          Icons.Default.Public,
          contentDescription = stringResource(R.string.cd_vpn_status),
          tint =
            if (isVpnActive) voltColors.emeraldShield
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      },
      label = { Text("Tunnel") },
    )
    NavigationRailItem(
      selected = activeSheet == BrowserSheet.LIBRARY_SETTINGS,
      onClick = onOpenMenu,
      icon = { Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.cd_menu)) },
      label = { Text("Menu") },
    )
    Spacer(modifier = Modifier.weight(1f))
    NavigationRailItem(
      selected = activeSheet == BrowserSheet.EXIT_CONFIRM,
      onClick = onRequestExit,
      icon = {
        Icon(
          Icons.Default.PowerSettingsNew,
          contentDescription = stringResource(R.string.cd_exit),
          tint = MaterialTheme.colorScheme.error,
        )
      },
      label = { Text("Exit", color = MaterialTheme.colorScheme.error) },
      modifier = Modifier.testTag("rail_exit_button"),
    )
  }
}
