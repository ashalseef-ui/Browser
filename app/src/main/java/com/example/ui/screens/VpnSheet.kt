package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.BrowserSettings
import com.example.ui.theme.LocalVoltColors
import com.example.vpn.DohProvider
import com.example.vpn.PublicRelayNode
import com.example.vpn.VpnTunnelState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreePublicVpnSheet(
  state: VpnTunnelState,
  settings: BrowserSettings,
  onRefreshRelays: () -> Unit,
  onAutoConnectFastestRelay: () -> Unit,
  onRotateProxy: () -> Unit,
  onConnectRelay: (PublicRelayNode) -> Unit,
  onDisconnectRelay: () -> Unit,
  onToggleAutoProxyFailover: (Boolean) -> Unit,
  onToggleAutoProxyOnLaunch: (Boolean) -> Unit,
  onToggleDnsVpn: (Boolean) -> Unit,
  onSelectDohProvider: (DohProvider) -> Unit,
  onDismiss: () -> Unit,
) {
  val voltColors = LocalVoltColors.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var showAdvancedDnsVpn by remember { mutableStateOf(state.isDnsVpnActive) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    LazyColumn(
      modifier = Modifier.fillMaxWidth().heightIn(max = 680.dp),
      contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      // Header
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
            Box(
              modifier =
                Modifier.size(40.dp)
                  .clip(CircleShape)
                  .background(
                    if (state.isAutoProxyActive) voltColors.emeraldShield.copy(alpha = 0.18f)
                    else voltColors.cyanAccent.copy(alpha = 0.14f)
                  ),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Default.Public,
                contentDescription = null,
                tint =
                  if (state.isAutoProxyActive) voltColors.emeraldShield
                  else voltColors.cyanAccent,
              )
            }
            Column {
              Text(
                text = "Free Auto Proxy",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text =
                  if (state.isAutoProxyActive) "Browser & media routed via live proxy"
                  else "Zero-permission multi-source proxy auto-switch",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }

      // Live Exit IP & Proxy Controls Hero Card
      item {
        Surface(
          shape = RoundedCornerShape(18.dp),
          color = voltColors.cardSurface,
          border =
            androidx.compose.foundation.BorderStroke(
              1.dp,
              if (state.isAutoProxyActive) voltColors.emeraldShield.copy(alpha = 0.55f)
              else voltColors.cardBorder,
            ),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "CURRENT PUBLIC EXIT IP",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  text =
                    if (state.currentExitCountry.isNotBlank())
                      "${state.currentExitIp} (${state.currentExitCountry})"
                    else state.currentExitIp,
                  style = MaterialTheme.typography.titleMedium,
                  color =
                    if (state.activeRelay != null) voltColors.emeraldShield
                    else MaterialTheme.colorScheme.onSurface,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              if (state.activeRelay != null) {
                Surface(
                  shape = RoundedCornerShape(100.dp),
                  color = voltColors.emeraldShield.copy(alpha = 0.16f),
                ) {
                  Text(
                    text = "${state.activeRelay.protocol} ACTIVE",
                    style = MaterialTheme.typography.labelSmall,
                    color = voltColors.emeraldShield,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                  )
                }
              }
            }

            if (state.activeRelay != null) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Button(
                  onClick = onRotateProxy,
                  enabled = !state.isFetchingRelays && !state.isConnectingRelay,
                  modifier = Modifier.weight(1f).height(46.dp).testTag("rotate_proxy_button"),
                  colors =
                    ButtonDefaults.buttonColors(
                      containerColor = voltColors.cyanAccent,
                      contentColor = Color(0xFF002026),
                    ),
                ) {
                  Icon(imageVector = Icons.Default.SwapHoriz, contentDescription = null)
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Next Fastest")
                }
                OutlinedButton(
                  onClick = onDisconnectRelay,
                  modifier = Modifier.weight(1f).height(46.dp).testTag("disconnect_relay_button"),
                ) {
                  Text("Disconnect")
                }
              }
            } else {
              Button(
                onClick = onAutoConnectFastestRelay,
                enabled = !state.isFetchingRelays && !state.isConnectingRelay,
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("auto_connect_relay_button"),
                colors =
                  ButtonDefaults.buttonColors(
                    containerColor = voltColors.cyanAccent,
                    contentColor = Color(0xFF002026),
                  ),
              ) {
                if (state.isFetchingRelays || state.isConnectingRelay) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFF002026),
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("Finding Fastest Free Proxy...")
                } else {
                  Icon(imageVector = Icons.Default.Bolt, contentDescription = null)
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Auto-Connect Fastest Free Proxy")
                }
              }
            }

            state.statusMessage?.let { msg ->
              Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = voltColors.amberAccent,
              )
            }
          }
        }
      }

      // Smart Auto-Proxy Automation Toggles
      item {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = voltColors.cardSurface,
          border = androidx.compose.foundation.BorderStroke(1.dp, voltColors.cardBorder),
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
              Icon(
                imageVector = Icons.Default.Autorenew,
                contentDescription = null,
                tint = voltColors.cyanAccent,
              )
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Auto-Failover on Dead Proxy",
                  style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                  text =
                    if (state.autoFailoversCount > 0)
                      "Automatically switches if a free proxy drops (${state.autoFailoversCount} switched)"
                    else "Automatically switches to next fast proxy on timeout",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Switch(
                checked = settings.autoProxyFailoverEnabled,
                onCheckedChange = onToggleAutoProxyFailover,
              )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = null,
                tint = voltColors.emeraldShield,
              )
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Auto-Connect Proxy on Launch",
                  style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                  text = "Automatically pick fastest free proxy when Volt opens",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Switch(
                checked = settings.autoProxyOnLaunchEnabled,
                onCheckedChange = onToggleAutoProxyOnLaunch,
              )
            }
          }
        }
      }

      // Live Verified Public Proxy Directory
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Live Free Proxy Pool",
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = "Benchmarked HTTPS-capable HTTP & SOCKS5 relays",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          IconButton(
            onClick = onRefreshRelays,
            enabled = !state.isFetchingRelays,
            modifier = Modifier.testTag("refresh_relays_button"),
          ) {
            if (state.isFetchingRelays) {
              CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
              Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Scan and benchmark free proxies",
                tint = voltColors.cyanAccent,
              )
            }
          }
        }
      }

      if (state.availableRelays.isEmpty() && !state.isFetchingRelays) {
        item {
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = voltColors.cardSurface,
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(
                imageVector = Icons.Default.Public,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = "Tap Auto-Connect or Refresh to scan and benchmark up to 36 free HTTP & SOCKS5 proxies.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }

      val reachableRelays = state.availableRelays.filter { it.isReachable == true }
      val displayRelays =
        if (reachableRelays.isNotEmpty()) reachableRelays else state.availableRelays.take(10)

      items(displayRelays, key = { it.id }) { relay ->
        val isConnected = state.activeRelay?.id == relay.id
        Surface(
          shape = RoundedCornerShape(14.dp),
          color =
            if (isConnected) voltColors.emeraldShield.copy(alpha = 0.14f)
            else voltColors.cardSurface,
          border =
            androidx.compose.foundation.BorderStroke(
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

      // Collapsible Advanced System DNS Tunnel (Optional)
      item {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = voltColors.cardSurface,
          border = androidx.compose.foundation.BorderStroke(1.dp, voltColors.cardBorder),
        ) {
          Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .clip(RoundedCornerShape(10.dp))
                  .clickable { showAdvancedDnsVpn = !showAdvancedDnsVpn },
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.VpnKey,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(18.dp),
                )
                Column {
                  Text(
                    text = "Advanced: Encrypted DNS VPN (Optional)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                  Text(
                    text =
                      if (state.isDnsVpnActive) "Active • ${state.queriesEncrypted} queries encrypted"
                      else "Requires Android VPN system consent",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
              Icon(
                imageVector =
                  if (showAdvancedDnsVpn) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = "Toggle advanced DNS VPN options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }

            AnimatedVisibility(visible = showAdvancedDnsVpn) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Default.Lock,
                      contentDescription = null,
                      tint = voltColors.cyanAccent,
                      modifier = Modifier.size(18.dp),
                    )
                    Text(
                      text = "Enable DoH System Tunnel",
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.onSurface,
                    )
                  }
                  Switch(
                    checked = state.isDnsVpnActive,
                    onCheckedChange = onToggleDnsVpn,
                    modifier = Modifier.testTag("dns_vpn_switch"),
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
