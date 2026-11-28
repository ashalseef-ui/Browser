package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalVoltColors

/** Compact metric tile used in the Security & Privacy telemetry header. */
@Composable
internal fun TelemetryCard(
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

/** Standard icon + title + subtitle + Switch row shared across Settings and Site Controls. */
@Composable
internal fun SettingToggleRow(
  icon: ImageVector,
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  enabled: Boolean = true,
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
      enabled = enabled,
      onCheckedChange = onCheckedChange,
      modifier = if (switchTag != null) Modifier.testTag(switchTag) else Modifier,
    )
  }
}

/** Modal dialog for pinning a custom website tile to the Speed Dial workspace. */
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

/** Confirmation dialog executing a full device-trace and tunnel wipe before exiting. */
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
