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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VpnKey
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.BrowserSettings
import com.example.ui.theme.LocalVoltColors
import com.example.vpn.DohProvider
import com.example.vpn.ProxyProtocolFilter
import com.example.vpn.PublicRelayNode
import com.example.vpn.VpnTunnelState
import com.example.vpn.WireGuardManager
import com.example.vpn.WireGuardMode
import java.nio.charset.StandardCharsets

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
  onRefreshRelays: () -> Unit,
  onAutoConnectFastestRelay: () -> Unit,
  onRotateProxy: () -> Unit,
  onConnectRelay: (PublicRelayNode) -> Unit,
  onDisconnectRelay: () -> Unit,
  onToggleAutoProxyFailover: (Boolean) -> Unit,
  onToggleAutoProxyOnLaunch: (Boolean) -> Unit,
  onToggleAutoDnsVpnOnLaunch: (Boolean) -> Unit,
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
    mutableStateOf(state.isWireGuardActive || state.isWireGuardConnecting)
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
      modifier = Modifier.fillMaxWidth().heightIn(max = 700.dp),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // Top Status Summary Box
      item {
        Surface(
          shape = RoundedCornerShape(18.dp),
          color = voltColors.cardSurface,
          border =
            BorderStroke(
              1.dp,
              if (state.isAnyProtectionActive) voltColors.emeraldShield.copy(alpha = 0.6f)
              else voltColors.cardBorder,
            ),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Box(
                modifier =
                  Modifier.size(38.dp)
                    .clip(CircleShape)
                    .background(
                      if (state.isAnyProtectionActive) voltColors.emeraldShield.copy(alpha = 0.18f)
                      else voltColors.cyanAccent.copy(alpha = 0.14f)
                    ),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = if (state.isWireGuardActive) Icons.Default.Shield else Icons.Default.Public,
                  contentDescription = null,
                  tint =
                    if (state.isAnyProtectionActive) voltColors.emeraldShield
                    else voltColors.cyanAccent,
                )
              }
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "VPN, Proxy & DNS Control",
                  style = MaterialTheme.typography.titleMedium,
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
                  if (state.isAnyProtectionActive) voltColors.emeraldShield.copy(alpha = 0.16f)
                  else MaterialTheme.colorScheme.surfaceContainerHigh,
              ) {
                Text(
                  text =
                    when {
                      state.isWireGuardActive && state.wireGuardMode == WireGuardMode.WARP_FREE -> "WARP ON"
                      state.isWireGuardActive -> "WG ON"
                      state.isAutoProxyActive -> "PROXY ON"
                      state.isDnsVpnActive -> "DoH ON"
                      else -> "ALL OFF"
                    },
                  style = MaterialTheme.typography.labelSmall,
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

      // BLOCK 1: WireGuard VPN Box (WARP + Custom .conf) with On/Off Toggle
      item {
        Surface(
          shape = RoundedCornerShape(18.dp),
          color = voltColors.cardSurface,
          border =
            BorderStroke(
              1.dp,
              if (state.isWireGuardActive) voltColors.emeraldShield.copy(alpha = 0.65f)
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
                  Modifier.size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                      if (state.isWireGuardActive) voltColors.emeraldShield.copy(alpha = 0.18f)
                      else voltColors.cyanAccent.copy(alpha = 0.14f)
                    ),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Default.Shield,
                  contentDescription = null,
                  tint = if (state.isWireGuardActive) voltColors.emeraldShield else voltColors.cyanAccent,
                  modifier = Modifier.size(19.dp),
                )
              }
              Column(
                modifier =
                  Modifier.weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showWireGuardDetails = !showWireGuardDetails },
              ) {
                Text(
                  text = "WireGuard VPN",
                  style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                  text =
                    when {
                      state.isWireGuardConnecting -> "Establishing encrypted tunnel..."
                      state.isWireGuardActive ->
                        "${state.wireGuardProfileName} • ↓ ${formatTransferBytes(state.wireGuardRxBytes)}  ↑ ${formatTransferBytes(state.wireGuardTxBytes)}"
                      else -> "Cloudflare WARP & Custom .conf (Fastest)"
                    },
                  style = MaterialTheme.typography.labelSmall,
                  color =
                    if (state.isWireGuardActive) voltColors.emeraldShield
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              IconButton(
                onClick = { showWireGuardDetails = !showWireGuardDetails },
                modifier = Modifier.size(34.dp),
              ) {
                Icon(
                  imageVector = if (showWireGuardDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                  contentDescription = "Expand WireGuard VPN options",
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Switch(
                checked = state.isWireGuardActive || state.isWireGuardConnecting,
                enabled = !state.isWireGuardConnecting,
                onCheckedChange = { enabled ->
                  if (enabled) {
                    showWireGuardDetails = true
                    if (
                      settings.preferredWireGuardMode == WireGuardMode.CUSTOM_CONF &&
                        state.hasSavedCustomWgConf
                    ) {
                      onConnectSavedCustomWg()
                    } else {
                      onConnectWarp()
                    }
                  } else {
                    onDisconnectWireGuard()
                  }
                },
                modifier = Modifier.testTag("wireguard_toggle_switch"),
              )
            }

            AnimatedVisibility(visible = showWireGuardDetails) {
              Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Button(
                    onClick = onConnectWarp,
                    enabled = !state.isWireGuardConnecting,
                    modifier = Modifier.weight(1f).height(44.dp).testTag("connect_warp_button"),
                    colors =
                      ButtonDefaults.buttonColors(
                        containerColor = voltColors.cyanAccent,
                        contentColor = Color(0xFF002026),
                      ),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                  ) {
                    if (state.isWireGuardConnecting) {
                      CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF002026),
                      )
                    } else {
                      Icon(imageVector = Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                      text =
                        if (state.isWireGuardActive && state.wireGuardMode == WireGuardMode.WARP_FREE)
                          "WARP Active"
                        else "Cloudflare WARP",
                      style = MaterialTheme.typography.labelMedium,
                      maxLines = 1,
                    )
                  }

                  OutlinedButton(
                    onClick = { showCustomWgEditor = !showCustomWgEditor },
                    modifier = Modifier.weight(1f).height(44.dp).testTag("toggle_custom_wg_editor_button"),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                  ) {
                    Icon(imageVector = Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Custom .conf", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                  }
                }

                if (state.hasSavedCustomWgConf && (!state.isWireGuardActive || state.wireGuardMode != WireGuardMode.CUSTOM_CONF)) {
                  OutlinedButton(
                    onClick = onConnectSavedCustomWg,
                    enabled = !state.isWireGuardConnecting,
                    modifier = Modifier.fillMaxWidth().height(42.dp).testTag("connect_saved_custom_wg_button"),
                  ) {
                    Text("Connect Saved: ${settings.savedCustomWgName.ifBlank { "Custom WireGuard" }}")
                  }
                }

                AnimatedVisibility(visible = showCustomWgEditor) {
                  Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      OutlinedTextField(
                        value = customWgName,
                        onValueChange = { customWgName = it },
                        label = { Text("Profile Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                      )
                      OutlinedButton(
                        onClick = { confFilePickerLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.height(52.dp).testTag("import_wg_conf_file_button"),
                      ) {
                        Icon(imageVector = Icons.Default.FileOpen, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(".conf")
                      }
                    }
                    OutlinedTextField(
                      value = customWgConfText,
                      onValueChange = { customWgConfText = it },
                      label = { Text("[Interface] ... [Peer] WireGuard Config") },
                      minLines = 3,
                      maxLines = 6,
                      modifier = Modifier.fillMaxWidth().testTag("custom_wg_conf_input"),
                    )
                    Button(
                      onClick = {
                        onConnectCustomWgConf(customWgConfText, customWgName.ifBlank { "Custom WireGuard" })
                        showCustomWgEditor = false
                      },
                      enabled =
                        WireGuardManager.hasValidWireGuardSections(customWgConfText) &&
                          !state.isWireGuardConnecting,
                      modifier = Modifier.fillMaxWidth().height(44.dp).testTag("connect_custom_wg_button"),
                    ) {
                      Text("Connect Custom WireGuard")
                    }
                  }
                }

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Text(
                    text = "Auto-connect WireGuard on startup",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                  )
                  Switch(
                    checked = settings.autoWireGuardOnLaunchEnabled,
                    onCheckedChange = onToggleAutoWireGuardOnLaunch,
                    modifier = Modifier.testTag("auto_wireguard_startup_switch"),
                  )
                }
              }
            }
          }
        }
      }

      // BLOCK 2: HTTP / SOCKS5 Proxy Box (DE & IN Preferred + Private Auth) with On/Off Toggle
      item {
        Surface(
          shape = RoundedCornerShape(18.dp),
          color = voltColors.cardSurface,
          border =
            BorderStroke(
              1.dp,
              if (state.isAutoProxyActive) voltColors.emeraldShield.copy(alpha = 0.65f)
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
                  Modifier.size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                      if (state.isAutoProxyActive) voltColors.emeraldShield.copy(alpha = 0.18f)
                      else voltColors.cyanAccent.copy(alpha = 0.14f)
                    ),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Default.Public,
                  contentDescription = null,
                  tint = if (state.isAutoProxyActive) voltColors.emeraldShield else voltColors.cyanAccent,
                  modifier = Modifier.size(19.dp),
                )
              }
              Column(
                modifier =
                  Modifier.weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showProxyDetails = !showProxyDetails },
              ) {
                Text(
                  text = "Web Proxy (DE & IN First)",
                  style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                  text =
                    when {
                      state.isFetchingRelays || state.isConnectingRelay -> "Scanning DE/IN & global proxies..."
                      state.activeRelay != null ->
                        "${state.activeRelay.country} • ${state.activeRelay.ip}:${state.activeRelay.port} (${state.activeRelay.protocol})"
                      else -> "9 public feeds + Private Auth (user:pass@ip:port)"
                    },
                  style = MaterialTheme.typography.labelSmall,
                  color =
                    if (state.isAutoProxyActive) voltColors.emeraldShield
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              IconButton(
                onClick = { showProxyDetails = !showProxyDetails },
                modifier = Modifier.size(34.dp),
              ) {
                Icon(
                  imageVector = if (showProxyDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                  contentDescription = "Expand web proxy options",
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Switch(
                checked = state.isAutoProxyActive || state.isConnectingRelay,
                enabled = !state.isFetchingRelays && !state.isConnectingRelay,
                onCheckedChange = { enabled ->
                  if (enabled) {
                    showProxyDetails = true
                    onAutoConnectFastestRelay()
                  } else {
                    onDisconnectRelay()
                  }
                },
                modifier = Modifier.testTag("proxy_toggle_switch"),
              )
            }

            AnimatedVisibility(visible = showProxyDetails) {
              Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Button(
                    onClick = if (state.activeRelay != null) onRotateProxy else onAutoConnectFastestRelay,
                    enabled = !state.isFetchingRelays && !state.isConnectingRelay,
                    modifier = Modifier.weight(1f).height(44.dp).testTag("auto_connect_relay_button"),
                    colors =
                      ButtonDefaults.buttonColors(
                        containerColor = voltColors.cyanAccent,
                        contentColor = Color(0xFF002026),
                      ),
                  ) {
                    if (state.isFetchingRelays || state.isConnectingRelay) {
                      CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF002026),
                      )
                    } else {
                      Icon(
                        imageVector = if (state.activeRelay != null) Icons.Default.SwapHoriz else Icons.Default.Bolt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                      )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                      text = if (state.activeRelay != null) "Next Fastest" else "Scan DE & IN",
                      style = MaterialTheme.typography.labelMedium,
                    )
                  }
                  OutlinedButton(
                    onClick = { showCustomProxyInput = !showCustomProxyInput },
                    modifier = Modifier.height(44.dp).testTag("toggle_custom_proxy_button"),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                  ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Private", style = MaterialTheme.typography.labelMedium)
                  }
                  IconButton(
                    onClick = onRefreshRelays,
                    enabled = !state.isFetchingRelays,
                    modifier = Modifier.testTag("refresh_relays_button"),
                  ) {
                    Icon(
                      imageVector = Icons.Default.Refresh,
                      contentDescription = "Refresh free proxies",
                      tint = voltColors.cyanAccent,
                    )
                  }
                }

                AnimatedVisibility(visible = showCustomProxyInput) {
                  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                      value = customProxyText,
                      onValueChange = { customProxyText = it },
                      label = { Text("ip:port or user:pass@ip:port") },
                      singleLine = true,
                      modifier = Modifier.fillMaxWidth().testTag("custom_proxy_input"),
                    )
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      listOf("HTTP", "SOCKS5").forEach { proto ->
                        FilterChipPill(
                          label = proto,
                          selected = customProxyProtocol == proto,
                          accentColor = voltColors.cyanAccent,
                          onClick = { customProxyProtocol = proto },
                        )
                      }
                      Spacer(modifier = Modifier.weight(1f))
                      Button(
                        onClick = {
                          onConnectCustomProxy(customProxyText, customProxyProtocol)
                          showCustomProxyInput = false
                        },
                        enabled = customProxyText.contains(':'),
                        modifier = Modifier.height(44.dp).testTag("connect_custom_proxy_button"),
                      ) {
                        Text("Connect")
                      }
                    }
                  }
                }

                // Protocol & Country Filters
                Row(
                  modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  ProxyProtocolFilter.entries.forEach { filter ->
                    FilterChipPill(
                      label = filter.label,
                      selected = state.selectedProtocolFilter == filter,
                      accentColor = voltColors.cyanAccent,
                      onClick = { onSelectProtocolFilter(filter) },
                    )
                  }
                  if (availableCountries.isNotEmpty()) {
                    FilterChipPill(
                      label = "All",
                      selected = state.selectedCountryFilter == "ALL",
                      accentColor = voltColors.emeraldShield,
                      onClick = { onSelectCountryFilter("ALL") },
                    )
                    availableCountries.forEach { code ->
                      FilterChipPill(
                        label = code,
                        selected = state.selectedCountryFilter == code,
                        accentColor = voltColors.emeraldShield,
                        onClick = { onSelectCountryFilter(code) },
                      )
                    }
                  }
                }

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                  ) {
                    Icon(
                      imageVector = Icons.Default.Autorenew,
                      contentDescription = null,
                      tint = voltColors.cyanAccent,
                      modifier = Modifier.size(16.dp),
                    )
                    Text(
                      text = "Auto-failover on dead proxy",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  Switch(
                    checked = settings.autoProxyFailoverEnabled,
                    onCheckedChange = onToggleAutoProxyFailover,
                  )
                }

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Text(
                    text = "Auto-scan proxy on startup",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                  )
                  Switch(
                    checked = settings.autoProxyOnLaunchEnabled,
                    onCheckedChange = onToggleAutoProxyOnLaunch,
                    modifier = Modifier.testTag("auto_proxy_startup_switch"),
                  )
                }
              }
            }
          }
        }
      }

      if (showProxyDetails && filteredRelays.isNotEmpty()) {
        items(filteredRelays, key = { it.id }) { relay ->
          val isConnected = state.activeRelay?.id == relay.id
          Surface(
            shape = RoundedCornerShape(14.dp),
            color =
              if (isConnected) voltColors.emeraldShield.copy(alpha = 0.14f)
              else voltColors.cardSurface,
            border =
              BorderStroke(
                1.dp,
                if (isConnected) voltColors.emeraldShield else voltColors.cardBorder,
              ),
            modifier =
              Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(enabled = relay.isReachable == true && !state.isConnectingRelay) {
                  if (isConnected) onDisconnectRelay() else onConnectRelay(relay)
                },
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(
                imageVector = Icons.Default.Language,
                contentDescription = null,
                tint =
                  if (relay.isReachable == true) voltColors.emeraldShield
                  else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
              )
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "${relay.country} (${relay.countryCode})",
                  style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.onSurface,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = "${relay.ip}:${relay.port} • ${relay.protocol} • ${relay.source}",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              Text(
                text =
                  when {
                    isConnected -> "Connected"
                    relay.isReachable == true -> "${relay.latencyMs}ms"
                    else -> "Offline"
                  },
                style = MaterialTheme.typography.labelMedium,
                color =
                  when {
                    isConnected || relay.isReachable == true -> voltColors.emeraldShield
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                  },
              )
            }
          }
        }
      }

      // BLOCK 3: Encrypted DNS (DoH) VPN Box with On/Off Toggle
      item {
        Surface(
          shape = RoundedCornerShape(18.dp),
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
                  Modifier.size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                      if (state.isDnsVpnActive) voltColors.emeraldShield.copy(alpha = 0.18f)
                      else voltColors.cyanAccent.copy(alpha = 0.14f)
                    ),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Default.Lock,
                  contentDescription = null,
                  tint = if (state.isDnsVpnActive) voltColors.emeraldShield else voltColors.cyanAccent,
                  modifier = Modifier.size(19.dp),
                )
              }
              Column(
                modifier =
                  Modifier.weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showDnsDetails = !showDnsDetails },
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
                onClick = { showDnsDetails = !showDnsDetails },
                modifier = Modifier.size(34.dp),
              ) {
                Icon(
                  imageVector = if (showDnsDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                  contentDescription = "Expand DoH DNS resolvers",
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Switch(
                checked = state.isDnsVpnActive,
                onCheckedChange = { enabled ->
                  if (enabled) showDnsDetails = true
                  onToggleDnsVpn(enabled)
                },
                modifier = Modifier.testTag("dns_vpn_switch"),
              )
            }

            AnimatedVisibility(visible = showDnsDetails) {
              Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

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
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                          if (selected) voltColors.cyanAccent.copy(alpha = 0.14f)
                          else MaterialTheme.colorScheme.surfaceContainerLow
                        )
                        .border(
                          width = 1.dp,
                          color =
                            if (selected) voltColors.cyanAccent.copy(alpha = 0.55f)
                            else Color.Transparent,
                          shape = RoundedCornerShape(10.dp),
                        )
                        .clickable { onSelectDohProvider(provider) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
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
                        tint = voltColors.cyanAccent,
                        modifier = Modifier.size(16.dp),
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }

      item { Spacer(modifier = Modifier.height(16.dp)) }
    }
  }
}

private fun formatTransferBytes(bytes: Long): String =
  when {
    bytes >= 1_048_576L -> String.format("%.1f MB", bytes / 1_048_576.0)
    bytes >= 1024L -> String.format("%.0f KB", bytes / 1024.0)
    else -> "${bytes} B"
  }

@Composable
private fun FilterChipPill(
  label: String,
  selected: Boolean,
  accentColor: Color,
  onClick: () -> Unit,
) {
  val voltColors = LocalVoltColors.current
  Surface(
    shape = RoundedCornerShape(100.dp),
    color = if (selected) accentColor.copy(alpha = 0.18f) else voltColors.cardSurface,
    border =
      BorderStroke(
        width = 1.dp,
        color = if (selected) accentColor else voltColors.cardBorder,
      ),
    modifier = Modifier.clip(RoundedCornerShape(100.dp)).clickable(onClick = onClick),
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      color = if (selected) accentColor else MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
    )
  }
}
