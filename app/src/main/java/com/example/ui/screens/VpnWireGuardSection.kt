package com.example.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.BrowserSettings
import com.example.ui.theme.LocalVoltColors
import com.example.vpn.VpnTunnelState
import com.example.vpn.WireGuardManager
import com.example.vpn.WireGuardMode
import java.util.Locale

/**
 * Expandable WireGuard VPN module supporting Cloudflare WARP (Experimental), custom `.conf`
 * profiles, keystore failure recovery notices, kill-switch lockdown, and Android Always-on link.
 */
@Composable
internal fun VpnWireGuardSectionCard(
  state: VpnTunnelState,
  settings: BrowserSettings,
  showDetails: Boolean,
  showCustomEditor: Boolean,
  customConfText: String,
  customName: String,
  onToggleDetails: () -> Unit,
  onToggleCustomEditor: () -> Unit,
  onCustomConfChange: (String) -> Unit,
  onCustomNameChange: (String) -> Unit,
  onPickConfFile: () -> Unit,
  onConnectWarp: () -> Unit,
  onConnectSavedCustomWg: () -> Unit,
  onConnectCustomWgConf: (String, String) -> Unit,
  onDisconnectWireGuard: () -> Unit,
  onToggleAutoWireGuardOnLaunch: (Boolean) -> Unit,
  onToggleWireGuardKillSwitch: (Boolean) -> Unit,
  onResetKillSwitch: () -> Unit,
) {
  val context = LocalContext.current
  val voltColors = LocalVoltColors.current

  Surface(
    shape = RoundedCornerShape(20.dp),
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
            Modifier.size(38.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(
                if (state.isWireGuardActive) voltColors.emeraldShield.copy(alpha = 0.18f)
                else voltColors.brandAccent.copy(alpha = 0.14f)
              ),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Default.Shield,
            contentDescription = null,
            tint = if (state.isWireGuardActive) voltColors.emeraldShield else voltColors.brandAccent,
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
            text = "WireGuard VPN",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text =
              when {
                state.isKillSwitchTripped -> "Kill Switch Blocking Traffic"
                state.isWireGuardConnecting -> "Establishing encrypted tunnel..."
                state.isWireGuardActive ->
                  "${state.wireGuardProfileName} • ↓ ${formatTransferBytes(state.wireGuardRxBytes)}  ↑ ${formatTransferBytes(state.wireGuardTxBytes)}"
                else -> "WARP (Experimental) & Custom .conf"
              },
            style = MaterialTheme.typography.labelSmall,
            color =
              when {
                state.isKillSwitchTripped -> MaterialTheme.colorScheme.error
                state.isWireGuardActive -> voltColors.emeraldShield
                else -> MaterialTheme.colorScheme.onSurfaceVariant
              },
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
            contentDescription = "Expand WireGuard VPN options",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = state.isWireGuardActive || state.isWireGuardConnecting,
          enabled = !state.isWireGuardConnecting,
          onCheckedChange = { enabled ->
            if (enabled) {
              if (!showDetails) onToggleDetails()
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

      AnimatedVisibility(visible = showDetails) {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          HorizontalDivider(color = voltColors.cardBorder.copy(alpha = 0.7f))

          if (state.warpConfigDecryptFailed || state.customWgConfigDecryptFailed) {
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = voltColors.amberAccent.copy(alpha = 0.14f),
              border = BorderStroke(1.dp, voltColors.amberAccent.copy(alpha = 0.55f)),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  imageVector = Icons.Default.Warning,
                  contentDescription = null,
                  tint = voltColors.amberAccent,
                  modifier = Modifier.size(18.dp),
                )
                Text(
                  text =
                    "Saved WireGuard config could not be decrypted with the device keystore. Please re-import your .conf file or reconnect WARP.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurface,
                )
              }
            }
          }

          if (state.isKillSwitchTripped) {
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Text(
                  text = "VPN Dropped — Kill Switch Blocking All Browser Traffic",
                  style = MaterialTheme.typography.labelLarge,
                  color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                  text = "Reconnect WireGuard or disable the Kill Switch below to restore direct browsing.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onErrorContainer,
                )
                OutlinedButton(
                  onClick = onResetKillSwitch,
                  modifier = Modifier.fillMaxWidth().testTag("reset_kill_switch_button"),
                ) {
                  Text("Disable Kill Switch & Restore Direct Access")
                }
              }
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Button(
              onClick = onConnectWarp,
              enabled = !state.isWireGuardConnecting,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.weight(1f).height(46.dp).testTag("connect_warp_button"),
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = voltColors.brandAccent,
                  contentColor = voltColors.onBrandAccent,
                ),
              contentPadding = PaddingValues(horizontal = 10.dp),
            ) {
              if (state.isWireGuardConnecting) {
                CircularProgressIndicator(
                  modifier = Modifier.size(16.dp),
                  strokeWidth = 2.dp,
                  color = voltColors.onBrandAccent,
                )
              } else {
                Icon(imageVector = Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
              }
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text =
                  if (state.isWireGuardActive && state.wireGuardMode == WireGuardMode.WARP_FREE)
                    "WARP Active"
                  else "WARP (Exp.)",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
              )
            }

            OutlinedButton(
              onClick = onToggleCustomEditor,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.weight(1f).height(46.dp).testTag("toggle_custom_wg_editor_button"),
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
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.fillMaxWidth().height(44.dp).testTag("connect_saved_custom_wg_button"),
            ) {
              Text("Connect Saved: ${settings.savedCustomWgName.ifBlank { "Custom WireGuard" }}")
            }
          }

          AnimatedVisibility(visible = showCustomEditor) {
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
                  value = customName,
                  onValueChange = onCustomNameChange,
                  label = { Text("Profile Name") },
                  singleLine = true,
                  modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                  onClick = onPickConfFile,
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.height(52.dp).testTag("import_wg_conf_file_button"),
                ) {
                  Icon(imageVector = Icons.Default.FileOpen, contentDescription = null)
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(".conf")
                }
              }
              OutlinedTextField(
                value = customConfText,
                onValueChange = onCustomConfChange,
                label = { Text("[Interface] ... [Peer] WireGuard Config") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth().testTag("custom_wg_conf_input"),
              )
              Button(
                onClick = {
                  onConnectCustomWgConf(customConfText, customName.ifBlank { "Custom WireGuard" })
                },
                enabled =
                  WireGuardManager.hasValidWireGuardSections(customConfText) &&
                    !state.isWireGuardConnecting,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(46.dp).testTag("connect_custom_wg_button"),
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

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = "Block traffic if VPN drops",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.weight(1f),
            )
            Switch(
              checked = settings.wireGuardKillSwitchEnabled,
              onCheckedChange = onToggleWireGuardKillSwitch,
              modifier = Modifier.testTag("wg_kill_switch_switch"),
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = "True system-wide Always-on lockdown must be enabled in Android VPN Settings.",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.weight(1f),
            )
            OutlinedButton(
              onClick = {
                runCatching {
                  context.startActivity(
                    Intent(Settings.ACTION_VPN_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                  )
                }
              },
              shape = RoundedCornerShape(12.dp),
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
              modifier = Modifier.testTag("open_android_vpn_settings_button"),
            ) {
              Text("VPN Settings", style = MaterialTheme.typography.labelSmall)
            }
          }
        }
      }
    }
  }
}

private fun formatTransferBytes(bytes: Long): String =
  when {
    bytes >= 1_048_576L -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
    bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
  }
