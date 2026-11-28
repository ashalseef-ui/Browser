package com.example.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.BrowserSettings
import com.example.ui.theme.LocalVoltColors
import com.example.vpn.DohProvider
import com.example.vpn.ProxyProtocolFilter
import com.example.vpn.PublicRelayNode
import com.example.vpn.VpnTunnelState
import com.example.vpn.WireGuardMode
import java.nio.charset.StandardCharsets

/**
 * Network & Tunnel modal sheet coordinating WireGuard VPN, DE/IN-prioritized HTTP/SOCKS5 web
 * proxies, and Encrypted DNS (DoH) local VpnService protection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreePublicVpnSheet(
  state: VpnTunnelState,
  settings: BrowserSettings,
  onConnectWarp: () -> Unit,
  onConnectSavedCustomWg: () -> Unit,
  onConnectCustomWgConf: (confText: String, profileName: String) -> Unit,
  onDisconnectWireGuard: () -> Unit,
  onToggleAutoWireGuardOnLaunch: (Boolean) -> Unit,
  onToggleWireGuardKillSwitch: (Boolean) -> Unit,
  onResetKillSwitch: () -> Unit,
  onRefreshRelays: () -> Unit,
  onAutoConnectFastestRelay: () -> Unit,
  onRotateProxy: () -> Unit,
  onConnectRelay: (PublicRelayNode) -> Unit,
  onDisconnectRelay: () -> Unit,
  onToggleAutoProxyFailover: (Boolean) -> Unit,
  onToggleAutoProxyOnLaunch: (Boolean) -> Unit,
  onToggleAutoDnsVpnOnLaunch: (Boolean) -> Unit,
  onConfirmPublicProxyWarning: () -> Unit,
  onSelectProtocolFilter: (ProxyProtocolFilter) -> Unit,
  onSelectCountryFilter: (String) -> Unit,
  onConnectCustomProxy: (String, String) -> Unit,
  onToggleDnsVpn: (Boolean) -> Unit,
  onSelectDohProvider: (DohProvider) -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val voltColors = LocalVoltColors.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  var showWireGuardDetails by remember {
    mutableStateOf(
      state.isWireGuardActive ||
        state.isWireGuardConnecting ||
        state.isKillSwitchTripped ||
        state.warpConfigDecryptFailed ||
        state.customWgConfigDecryptFailed
    )
  }
  var showCustomWgEditor by remember { mutableStateOf(false) }
  var customWgConfText by remember { mutableStateOf(settings.savedCustomWgConf) }
  var customWgName by remember {
    mutableStateOf(settings.savedCustomWgName.ifBlank { "Custom WireGuard" })
  }

  var showProxyDetails by remember { mutableStateOf(state.isAutoProxyActive) }
  var showCustomProxyInput by remember { mutableStateOf(false) }
  var customProxyText by remember { mutableStateOf("") }
  var customProxyProtocol by remember { mutableStateOf("HTTP") }
  var pendingPublicProxyAction by remember { mutableStateOf<(() -> Unit)?>(null) }

  val runWithPublicProxyWarning: (() -> Unit) -> Unit = { action ->
    if (settings.hasSeenPublicProxyWarning) {
      action()
    } else {
      pendingPublicProxyAction = action
    }
  }

  var showDnsDetails by remember { mutableStateOf(state.isDnsVpnActive) }

  val confFilePickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
      if (uri == null) return@rememberLauncherForActivityResult
      val content =
        runCatching {
          context.contentResolver.openInputStream(uri)?.use { stream ->
            val bytes = stream.readBytes()
            if (bytes.size <= 65_536) String(bytes, StandardCharsets.UTF_8) else ""
          }.orEmpty()
        }.getOrDefault("")
      if (content.isNotBlank()) {
        val fileName =
          runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
              ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
              }
          }.getOrNull()?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "Imported WireGuard"
        customWgConfText = content
        customWgName = fileName
        showWireGuardDetails = true
        showCustomWgEditor = true
      }
    }

  val reachableRelays = state.availableRelays.filter { it.isReachable == true }
  val availableCountries =
    remember(reachableRelays) {
      val preferred = listOf("DE", "IN")
      reachableRelays
        .map { it.countryCode }
        .filter { it.isNotBlank() && it != "AUTO" && it != "CUSTOM" }
        .distinct()
        .sortedWith(
          compareBy<String> { code ->
            val idx = preferred.indexOf(code.uppercase())
            if (idx >= 0) idx else Int.MAX_VALUE
          }.thenBy { it }
        )
    }
  val filteredRelays =
    remember(state.availableRelays, state.selectedProtocolFilter, state.selectedCountryFilter) {
      val base = if (reachableRelays.isNotEmpty()) reachableRelays else state.availableRelays.take(12)
      base.filter { relay ->
        val protoMatch =
          when (state.selectedProtocolFilter) {
            ProxyProtocolFilter.ALL -> true
            ProxyProtocolFilter.HTTP -> relay.protocol.equals("HTTP", ignoreCase = true)
            ProxyProtocolFilter.SOCKS5 -> relay.protocol.equals("SOCKS5", ignoreCase = true)
          }
        val countryMatch =
          state.selectedCountryFilter == "ALL" ||
            relay.countryCode.equals(state.selectedCountryFilter, ignoreCase = true)
        protoMatch && countryMatch
      }
    }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    LazyColumn(
      modifier = Modifier.fillMaxWidth().heightIn(max = 710.dp),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        VpnStatusBanner(state = state)
      }

      item {
        VpnWireGuardSectionCard(
          state = state,
          settings = settings,
          showDetails = showWireGuardDetails,
          showCustomEditor = showCustomWgEditor,
          customConfText = customWgConfText,
          customName = customWgName,
          onToggleDetails = { showWireGuardDetails = !showWireGuardDetails },
          onToggleCustomEditor = { showCustomWgEditor = !showCustomWgEditor },
          onCustomConfChange = { customWgConfText = it },
          onCustomNameChange = { customWgName = it },
          onPickConfFile = { confFilePickerLauncher.launch(arrayOf("*/*")) },
          onConnectWarp = onConnectWarp,
          onConnectSavedCustomWg = onConnectSavedCustomWg,
          onConnectCustomWgConf = { conf, name ->
            onConnectCustomWgConf(conf, name)
            showCustomWgEditor = false
          },
          onDisconnectWireGuard = onDisconnectWireGuard,
          onToggleAutoWireGuardOnLaunch = onToggleAutoWireGuardOnLaunch,
          onToggleWireGuardKillSwitch = onToggleWireGuardKillSwitch,
          onResetKillSwitch = onResetKillSwitch,
        )
      }

      item {
        VpnProxySectionCard(
          state = state,
          settings = settings,
          showDetails = showProxyDetails,
          showCustomInput = showCustomProxyInput,
          customProxyText = customProxyText,
          customProxyProtocol = customProxyProtocol,
          availableCountries = availableCountries,
          onToggleDetails = { showProxyDetails = !showProxyDetails },
          onToggleCustomInput = { showCustomProxyInput = !showCustomProxyInput },
          onCustomProxyTextChange = { customProxyText = it },
          onCustomProxyProtocolChange = { customProxyProtocol = it },
          onConnectCustomProxy = { addr, proto ->
            onConnectCustomProxy(addr, proto)
            showCustomProxyInput = false
          },
          onRefreshRelays = onRefreshRelays,
          onAutoConnectFastestRelay = onAutoConnectFastestRelay,
          onRotateProxy = onRotateProxy,
          onDisconnectRelay = onDisconnectRelay,
          onToggleAutoProxyFailover = onToggleAutoProxyFailover,
          onToggleAutoProxyOnLaunch = onToggleAutoProxyOnLaunch,
          onSelectProtocolFilter = onSelectProtocolFilter,
          onSelectCountryFilter = onSelectCountryFilter,
          runWithPublicProxyWarning = runWithPublicProxyWarning,
        )
      }

      if (showProxyDetails && filteredRelays.isNotEmpty()) {
        proxyRelayItems(
          relays = filteredRelays,
          state = state,
          onConnectRelay = onConnectRelay,
          onDisconnectRelay = onDisconnectRelay,
          runWithPublicProxyWarning = runWithPublicProxyWarning,
        )
      }

      item {
        EncryptedDnsSectionCard(
          state = state,
          settings = settings,
          showDetails = showDnsDetails,
          onToggleDetails = { showDnsDetails = !showDnsDetails },
          onToggleDnsVpn = { enabled ->
            if (enabled) showDnsDetails = true
            onToggleDnsVpn(enabled)
          },
          onToggleAutoDnsVpnOnLaunch = onToggleAutoDnsVpnOnLaunch,
          onSelectDohProvider = onSelectDohProvider,
        )
      }

      item { Spacer(modifier = Modifier.height(16.dp)) }
    }
  }

  pendingPublicProxyAction?.let { actionToRun ->
    AlertDialog(
      onDismissRequest = { pendingPublicProxyAction = null },
      icon = {
        Icon(
          imageVector = Icons.Default.Warning,
          contentDescription = null,
          tint = voltColors.amberAccent,
        )
      },
      title = { Text("Public Proxy Security Notice") },
      text = {
        Text(
          text =
            "Free public proxies (ProxyScrape, GeoNode, and public lists) are run by unknown third parties. Plain HTTP traffic can be read or altered by the proxy operator.\n\nUse HTTPS websites or WireGuard VPN for sensitive browsing. Volt Browser will also warn before opening plain HTTP pages over a public relay.",
          style = MaterialTheme.typography.bodyMedium,
        )
      },
      confirmButton = {
        Button(
          onClick = {
            onConfirmPublicProxyWarning()
            val pending = actionToRun
            pendingPublicProxyAction = null
            pending()
          },
          modifier = Modifier.testTag("confirm_public_proxy_warning_button"),
        ) {
          Text("Understand & Connect")
        }
      },
      dismissButton = {
        TextButton(
          onClick = { pendingPublicProxyAction = null },
          modifier = Modifier.testTag("cancel_public_proxy_warning_button"),
        ) {
          Text("Cancel")
        }
      },
    )
  }
}

@Composable
private fun VpnStatusBanner(state: VpnTunnelState) {
  val voltColors = LocalVoltColors.current
  val bannerTint =
    if (state.isAnyProtectionActive) voltColors.emeraldShield else voltColors.brandAccent
  Surface(
    shape = RoundedCornerShape(20.dp),
    color = bannerTint.copy(alpha = 0.10f),
    border = BorderStroke(1.dp, bannerTint.copy(alpha = 0.4f)),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Box(
          modifier =
            Modifier.size(40.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(bannerTint.copy(alpha = 0.18f)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = if (state.isWireGuardActive) Icons.Default.Shield else Icons.Default.Public,
            contentDescription = null,
            tint = bannerTint,
          )
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Network & Tunnel",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text =
              if (state.currentExitCountry.isNotBlank())
                "Exit: ${state.currentExitIp} (${state.currentExitCountry})"
              else "Exit: ${state.currentExitIp}",
            style = MaterialTheme.typography.bodySmall,
            color =
              if (state.isAnyProtectionActive) voltColors.emeraldShield
              else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Surface(
          shape = RoundedCornerShape(100.dp),
          color =
            if (state.isAnyProtectionActive) voltColors.emeraldShield.copy(alpha = 0.18f)
            else voltColors.cardSurface,
        ) {
          Text(
            text =
              when {
                state.isWireGuardActive && state.wireGuardMode == WireGuardMode.WARP_FREE -> "WARP ON"
                state.isWireGuardActive -> "WG ON"
                state.isAutoProxyActive -> "PROXY ON"
                state.isDnsVpnActive -> "DoH ON"
                else -> "DIRECT"
              },
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color =
              if (state.isAnyProtectionActive) voltColors.emeraldShield
              else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
          )
        }
      }
      state.statusMessage?.let { msg ->
        Text(
          text = msg,
          style = MaterialTheme.typography.labelSmall,
          color = voltColors.amberAccent,
        )
      }
    }
  }
}

@Composable
private fun EncryptedDnsSectionCard(
  state: VpnTunnelState,
  settings: BrowserSettings,
  showDetails: Boolean,
  onToggleDetails: () -> Unit,
  onToggleDnsVpn: (Boolean) -> Unit,
  onToggleAutoDnsVpnOnLaunch: (Boolean) -> Unit,
  onSelectDohProvider: (DohProvider) -> Unit,
) {
  val voltColors = LocalVoltColors.current
  Surface(
    shape = RoundedCornerShape(20.dp),
    color = voltColors.cardSurface,
    border =
      BorderStroke(
        1.dp,
        if (state.isDnsVpnActive) voltColors.emeraldShield.copy(alpha = 0.65f)
        else voltColors.cardBorder,
      ),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Box(
          modifier =
            Modifier.size(38.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(
                if (state.isDnsVpnActive) voltColors.emeraldShield.copy(alpha = 0.18f)
                else voltColors.brandAccent.copy(alpha = 0.14f)
              ),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = if (state.isDnsVpnActive) voltColors.emeraldShield else voltColors.brandAccent,
            modifier = Modifier.size(20.dp),
          )
        }
        Column(
          modifier =
            Modifier.weight(1f)
              .clip(RoundedCornerShape(8.dp))
              .clickable(onClick = onToggleDetails),
        ) {
          Text(
            text = "Encrypted DNS (DoH) VPN",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text =
              if (state.isDnsVpnActive)
                "${state.activeDohProvider.displayName} • ${state.queriesEncrypted} queries (${state.lastDnsLatencyMs}ms)"
              else "${state.activeDohProvider.displayName} • ${DohProvider.entries.size} resolvers",
            style = MaterialTheme.typography.labelSmall,
            color =
              if (state.isDnsVpnActive) voltColors.emeraldShield
              else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        IconButton(
          onClick = onToggleDetails,
          modifier = Modifier.minimumInteractiveComponentSize(),
        ) {
          Icon(
            imageVector = if (showDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = "Expand DoH DNS resolvers",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = state.isDnsVpnActive,
          onCheckedChange = onToggleDnsVpn,
          modifier = Modifier.testTag("dns_vpn_switch"),
        )
      }

      AnimatedVisibility(visible = showDetails) {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          HorizontalDivider(color = voltColors.cardBorder.copy(alpha = 0.7f))

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = "Auto-start DoH DNS on startup",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.weight(1f),
            )
            Switch(
              checked = settings.autoDnsVpnOnLaunchEnabled,
              onCheckedChange = onToggleAutoDnsVpnOnLaunch,
              modifier = Modifier.testTag("auto_vpn_startup_switch"),
            )
          }

          DohProvider.entries.forEach { provider ->
            val selected = state.activeDohProvider == provider
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .clip(RoundedCornerShape(12.dp))
                  .background(
                    if (selected) voltColors.brandAccent.copy(alpha = 0.14f)
                    else voltColors.cardElevatedSurface
                  )
                  .border(
                    width = 1.dp,
                    color =
                      if (selected) voltColors.brandAccent.copy(alpha = 0.55f)
                      else Color.Transparent,
                    shape = RoundedCornerShape(12.dp),
                  )
                  .clickable { onSelectDohProvider(provider) }
                  .padding(horizontal = 12.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = provider.displayName,
                  style = MaterialTheme.typography.labelLarge,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                  text = provider.subtitle,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              if (selected) {
                Icon(
                  imageVector = Icons.Default.CheckCircle,
                  contentDescription = "Selected resolver",
                  tint = voltColors.brandAccent,
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
