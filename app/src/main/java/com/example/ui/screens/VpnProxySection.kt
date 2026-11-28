package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import com.example.data.BrowserSettings
import com.example.ui.theme.LocalVoltColors
import com.example.vpn.ProxyProtocolFilter
import com.example.vpn.PublicRelayNode
import com.example.vpn.VpnTunnelState

/**
 * Expandable Web Proxy card supporting DE/IN-prioritized free relays, private authenticated proxy
 * entries, protocol/country filter chips, and auto-failover toggles.
 */
@Composable
internal fun VpnProxySectionCard(
  state: VpnTunnelState,
  settings: BrowserSettings,
  showDetails: Boolean,
  showCustomInput: Boolean,
  customProxyText: String,
  customProxyProtocol: String,
  availableCountries: List<String>,
  onToggleDetails: () -> Unit,
  onToggleCustomInput: () -> Unit,
  onCustomProxyTextChange: (String) -> Unit,
  onCustomProxyProtocolChange: (String) -> Unit,
  onConnectCustomProxy: (String, String) -> Unit,
  onRefreshRelays: () -> Unit,
  onAutoConnectFastestRelay: () -> Unit,
  onRotateProxy: () -> Unit,
  onDisconnectRelay: () -> Unit,
  onToggleAutoProxyFailover: (Boolean) -> Unit,
  onToggleAutoProxyOnLaunch: (Boolean) -> Unit,
  onSelectProtocolFilter: (ProxyProtocolFilter) -> Unit,
  onSelectCountryFilter: (String) -> Unit,
  runWithPublicProxyWarning: (() -> Unit) -> Unit,
) {
  val voltColors = LocalVoltColors.current

  Surface(
    shape = RoundedCornerShape(20.dp),
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
            Modifier.size(38.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(
                if (state.isAutoProxyActive) voltColors.emeraldShield.copy(alpha = 0.18f)
                else voltColors.brandAccent.copy(alpha = 0.14f)
              ),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Default.Public,
            contentDescription = null,
            tint = if (state.isAutoProxyActive) voltColors.emeraldShield else voltColors.brandAccent,
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
                else -> "Public feeds + Private Auth (user:pass@ip:port)"
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
          onClick = onToggleDetails,
          modifier = Modifier.minimumInteractiveComponentSize(),
        ) {
          Icon(
            imageVector = if (showDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = "Expand web proxy options",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = state.isAutoProxyActive || state.isConnectingRelay,
          enabled = !state.isFetchingRelays && !state.isConnectingRelay,
          onCheckedChange = { enabled ->
            if (enabled) {
              runWithPublicProxyWarning {
                if (!showDetails) onToggleDetails()
                onAutoConnectFastestRelay()
              }
            } else {
              onDisconnectRelay()
            }
          },
          modifier = Modifier.testTag("proxy_toggle_switch"),
        )
      }

      AnimatedVisibility(visible = showDetails) {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          HorizontalDivider(color = voltColors.cardBorder.copy(alpha = 0.7f))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Button(
              onClick = {
                runWithPublicProxyWarning {
                  if (state.activeRelay != null) onRotateProxy() else onAutoConnectFastestRelay()
                }
              },
              enabled = !state.isFetchingRelays && !state.isConnectingRelay,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.weight(1f).height(46.dp).testTag("auto_connect_relay_button"),
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = voltColors.brandAccent,
                  contentColor = voltColors.onBrandAccent,
                ),
            ) {
              if (state.isFetchingRelays || state.isConnectingRelay) {
                CircularProgressIndicator(
                  modifier = Modifier.size(16.dp),
                  strokeWidth = 2.dp,
                  color = voltColors.onBrandAccent,
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
              onClick = onToggleCustomInput,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.height(46.dp).testTag("toggle_custom_proxy_button"),
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
                tint = voltColors.brandAccent,
              )
            }
          }

          AnimatedVisibility(visible = showCustomInput) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedTextField(
                value = customProxyText,
                onValueChange = onCustomProxyTextChange,
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
                    accentColor = voltColors.brandAccent,
                    onClick = { onCustomProxyProtocolChange(proto) },
                  )
                }
                Spacer(modifier = Modifier.weight(1f))
                Button(
                  onClick = { onConnectCustomProxy(customProxyText, customProxyProtocol) },
                  enabled = customProxyText.contains(':'),
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.height(44.dp).testTag("connect_custom_proxy_button"),
                ) {
                  Text("Connect")
                }
              }
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            ProxyProtocolFilter.entries.forEach { filter ->
              FilterChipPill(
                label = filter.label,
                selected = state.selectedProtocolFilter == filter,
                accentColor = voltColors.brandAccent,
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
                tint = voltColors.brandAccent,
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

/** LazyList items rendering reachable proxy relay candidates inside the Network & Tunnel sheet. */
internal fun LazyListScope.proxyRelayItems(
  relays: List<PublicRelayNode>,
  state: VpnTunnelState,
  onConnectRelay: (PublicRelayNode) -> Unit,
  onDisconnectRelay: () -> Unit,
  runWithPublicProxyWarning: (() -> Unit) -> Unit,
) {
  items(relays, key = { it.id }) { relay ->
    val voltColors = LocalVoltColors.current
    val isConnected = state.activeRelay?.id == relay.id
    Surface(
      shape = RoundedCornerShape(16.dp),
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
          .clip(RoundedCornerShape(16.dp))
          .clickable(enabled = relay.isReachable == true && !state.isConnectingRelay) {
            if (isConnected) {
              onDisconnectRelay()
            } else if (relay.isPublicFreeRelay) {
              runWithPublicProxyWarning { onConnectRelay(relay) }
            } else {
              onConnectRelay(relay)
            }
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

@Composable
internal fun FilterChipPill(
  label: String,
  selected: Boolean,
  accentColor: Color,
  onClick: () -> Unit,
) {
  val voltColors = LocalVoltColors.current
  Surface(
    shape = RoundedCornerShape(100.dp),
    color = if (selected) accentColor.copy(alpha = 0.18f) else voltColors.cardElevatedSurface,
    border =
      BorderStroke(
        width = 1.dp,
        color = if (selected) accentColor else voltColors.cardBorder,
      ),
    modifier = Modifier.minimumInteractiveComponentSize().clip(RoundedCornerShape(100.dp)).clickable(onClick = onClick),
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      color = if (selected) accentColor else MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
    )
  }
}
