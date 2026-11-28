package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.data.HttpsWarningState
import com.example.ui.components.extractHost
import com.example.ui.theme.LocalVoltColors

/** Full-pane interstitial warning for HTTPS-Only upgrade failures or plain HTTP over public relays. */
@Composable
internal fun HttpsWarningOverlay(
  warning: HttpsWarningState,
  onTryHttps: () -> Unit,
  onGoBack: () -> Unit,
  onContinueInsecure: () -> Unit,
) {
  val voltColors = LocalVoltColors.current

  Surface(
    color = MaterialTheme.colorScheme.background,
    modifier = Modifier.fillMaxSize().testTag("https_warning_overlay"),
  ) {
    Column(
      modifier =
        Modifier.fillMaxSize()
          .padding(horizontal = 24.dp, vertical = 32.dp),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Surface(
        shape = RoundedCornerShape(22.dp),
        color = voltColors.amberAccent.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, voltColors.amberAccent.copy(alpha = 0.45f)),
        modifier = Modifier.size(72.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = voltColors.amberAccent,
            modifier = Modifier.size(36.dp),
          )
        }
      }
      Spacer(modifier = Modifier.size(20.dp))
      Text(
        text =
          if (warning.isPublicProxyWarning) "Unencrypted HTTP Over Public Proxy"
          else "HTTPS Connection Unavailable",
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Spacer(modifier = Modifier.size(10.dp))
      Text(
        text =
          if (warning.isPublicProxyWarning) {
            "${extractHost(warning.fallbackHttpUrl)} uses plain HTTP. ${warning.reason}"
          } else {
            "HTTPS-Only Mode could not load ${extractHost(warning.fallbackHttpUrl)} over HTTPS (${warning.reason})."
          },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.size(24.dp))
      if (warning.isPublicProxyWarning) {
        Button(
          onClick = onTryHttps,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth().testTag("try_https_button"),
        ) {
          Text("Try HTTPS Instead")
        }
        Spacer(modifier = Modifier.size(10.dp))
      }
      OutlinedButton(
        onClick = onGoBack,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().testTag("https_warning_back_button"),
      ) {
        Text("Go Back")
      }
      Spacer(modifier = Modifier.size(8.dp))
      TextButton(
        onClick = onContinueInsecure,
        modifier = Modifier.testTag("continue_http_button"),
      ) {
        Text(
          text = "Continue Anyway (Insecure HTTP)",
          color = MaterialTheme.colorScheme.error,
        )
      }
    }
  }
}

/** Modal prompt asking whether to allow Camera, Microphone, or Location once, for the site, or deny. */
@Composable
internal fun SitePermissionPromptDialog(
  prompt: SitePermissionPromptState,
  onDecision: (granted: Boolean, rememberForSite: Boolean) -> Unit,
) {
  val voltColors = LocalVoltColors.current
  val permLabels = prompt.requestedTypes.joinToString(", ") { it.label }

  AlertDialog(
    onDismissRequest = { onDecision(false, false) },
    icon = {
      Icon(
        imageVector = Icons.Default.Security,
        contentDescription = null,
        tint = voltColors.brandAccent,
      )
    },
    title = { Text("Permission Request") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = "${prompt.host} wants to access: $permLabels",
          style = MaterialTheme.typography.bodyMedium,
        )
        Text(
          text =
            if (prompt.isIncognito) {
              "Private tab: permissions are never saved beyond this session."
            } else {
              "You can allow once, allow for ${prompt.host}, or deny."
            },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
    confirmButton = {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(
            onClick = { onDecision(true, false) },
            modifier = Modifier.testTag("site_perm_allow_once_button"),
          ) {
            Text("Allow once")
          }
          Button(
            onClick = { onDecision(true, true) },
            modifier = Modifier.testTag("site_perm_allow_site_button"),
          ) {
            Text(if (prompt.isIncognito) "Allow session" else "Allow site")
          }
        }
      }
    },
    dismissButton = {
      TextButton(
        onClick = { onDecision(false, false) },
        modifier = Modifier.testTag("site_perm_deny_button"),
      ) {
        Text("Deny")
      }
    },
  )
}

/** Floating find-in-page search bar positioned above the active WebView viewport. */
@Composable
internal fun FindInPageBar(
  onQueryChange: (String) -> Unit,
  onFindNext: (String) -> Unit,
  onFindPrev: (String) -> Unit,
  onClose: () -> Unit,
) {
  val voltColors = LocalVoltColors.current
  var query by remember { mutableStateOf("") }

  Surface(
    color = voltColors.dockSurface,
    border = BorderStroke(1.dp, voltColors.cardBorder.copy(alpha = 0.6f)),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = voltColors.omniboxSurface,
        border = BorderStroke(1.dp, voltColors.cardBorder),
        modifier = Modifier.weight(1f),
      ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
          if (query.isEmpty()) {
            Text(
              text = "Find in page...",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          BasicTextField(
            value = query,
            onValueChange = {
              query = it
              onQueryChange(it)
            },
            singleLine = true,
            textStyle =
              MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface
              ),
            cursorBrush = SolidColor(voltColors.brandAccent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onFindNext(query) }),
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
      IconButton(
        onClick = { onFindPrev(query) },
        modifier = Modifier.minimumInteractiveComponentSize(),
      ) {
        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous match")
      }
      IconButton(
        onClick = { onFindNext(query) },
        modifier = Modifier.minimumInteractiveComponentSize(),
      ) {
        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next match")
      }
      IconButton(
        onClick = onClose,
        modifier = Modifier.minimumInteractiveComponentSize(),
      ) {
        Icon(Icons.Default.Close, contentDescription = "Close find bar")
      }
    }
  }
}
