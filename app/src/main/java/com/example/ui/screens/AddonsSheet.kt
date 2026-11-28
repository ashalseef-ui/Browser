package com.example.ui.screens

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.browser.FirefoxAddonManager
import com.example.data.AddonCatalogItem
import com.example.data.BrowserEngineMode
import com.example.data.FirefoxAddonInfo
import com.example.ui.AddonInstallPromptState
import com.example.ui.AddonPopupState
import com.example.ui.theme.LocalVoltColors
import org.mozilla.geckoview.GeckoView

/**
 * Modal sheet for switching Volt's rendering engine (Gecko vs WebView) and managing Firefox
 * `.xpi` WebExtensions (URL / AMO store install, local `.xpi` file import, enable/disable,
 * Private Browsing permissions, Action popups, and uninstall).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddonsSheet(
  engineMode: BrowserEngineMode,
  addons: List<FirefoxAddonInfo>,
  isInstalling: Boolean,
  onSelectEngineMode: (BrowserEngineMode) -> Unit,
  onInstallFromUrl: (String) -> Unit,
  onImportLocalXpiClick: () -> Unit,
  onOpenAmoStore: () -> Unit,
  onToggleAddonEnabled: (addonId: String, enabled: Boolean) -> Unit,
  onToggleAddonPrivate: (addonId: String, allowed: Boolean) -> Unit,
  onOpenAddonPopup: (addonId: String) -> Unit,
  onUninstallAddon: (addonId: String) -> Unit,
  onRefreshAddons: () -> Unit,
  onDismiss: () -> Unit,
) {
  val voltColors = LocalVoltColors.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val focusManager = LocalFocusManager.current
  var xpiUrlInput by remember { mutableStateOf("") }
  val installedIds = remember(addons) { addons.map { it.id }.toSet() }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    LazyColumn(
      modifier = Modifier.fillMaxWidth().heightIn(min = 420.dp, max = 700.dp),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
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
                Modifier.size(38.dp)
                  .clip(RoundedCornerShape(12.dp))
                  .background(voltColors.brandAccent.copy(alpha = 0.16f)),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Default.Extension,
                contentDescription = null,
                tint = voltColors.brandAccent,
              )
            }
            Column {
              Text(
                text = "Firefox Add-ons & Engine",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text =
                  if (engineMode == BrowserEngineMode.GECKO) {
                    "GeckoView engine active • .xpi WebExtensions supported"
                  } else {
                    "Switch to Gecko engine to run Firefox add-ons"
                  },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          TextButton(onClick = onRefreshAddons) {
            Text("Refresh")
          }
        }
      }

      // Engine Mode Selector
      item {
        Surface(
          shape = RoundedCornerShape(18.dp),
          color = voltColors.cardSurface,
          border = BorderStroke(1.dp, voltColors.cardBorder),
          modifier = Modifier.fillMaxWidth().testTag("engine_mode_card"),
        ) {
          Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text(
              text = "Rendering Engine",
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              BrowserEngineMode.entries.forEach { mode ->
                val selected = engineMode == mode
                val cardShape = RoundedCornerShape(14.dp)
                Surface(
                  shape = cardShape,
                  color =
                    if (selected) voltColors.brandAccent.copy(alpha = 0.16f)
                    else voltColors.omniboxSurface,
                  border =
                    BorderStroke(
                      width = if (selected) 1.5.dp else 1.dp,
                      color = if (selected) voltColors.brandAccent else voltColors.cardBorder,
                    ),
                  modifier =
                    Modifier.weight(1f)
                      .minimumInteractiveComponentSize()
                      .clip(cardShape)
                      .clickable { onSelectEngineMode(mode) }
                      .testTag("engine_mode_${mode.name.lowercase()}"),
                ) {
                  Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                  ) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.SpaceBetween,
                      modifier = Modifier.fillMaxWidth(),
                    ) {
                      Text(
                        text = mode.badgeLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (selected) voltColors.brandAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                      if (selected) {
                        Icon(
                          imageVector = Icons.Default.Check,
                          contentDescription = "Selected",
                          tint = voltColors.brandAccent,
                          modifier = Modifier.size(16.dp),
                        )
                      }
                    }
                    Text(
                      text = mode.displayName,
                      style = MaterialTheme.typography.labelLarge,
                      color = MaterialTheme.colorScheme.onSurface,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                }
              }
            }
            Text(
              text = engineMode.subtitle,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      // Install .xpi from URL / Local file / AMO Store
      item {
        Surface(
          shape = RoundedCornerShape(18.dp),
          color = voltColors.cardSurface,
          border = BorderStroke(1.dp, voltColors.cardBorder),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text(
              text = "Install Firefox Plugin (.xpi)",
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              OutlinedTextField(
                value = xpiUrlInput,
                onValueChange = { xpiUrlInput = it },
                placeholder = { Text("Paste .xpi URL or addons.mozilla.org link") },
                singleLine = true,
                keyboardOptions =
                  KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                  ),
                keyboardActions =
                  KeyboardActions(
                    onDone = {
                      if (xpiUrlInput.isNotBlank() && !isInstalling) {
                        focusManager.clearFocus()
                        onInstallFromUrl(xpiUrlInput)
                      }
                    }
                  ),
                modifier = Modifier.weight(1f).testTag("addon_url_input"),
              )
              Button(
                onClick = {
                  focusManager.clearFocus()
                  onInstallFromUrl(xpiUrlInput)
                },
                enabled = xpiUrlInput.isNotBlank() && !isInstalling,
                modifier = Modifier.height(52.dp).testTag("addon_install_url_button"),
              ) {
                if (isInstalling) {
                  CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                  Icon(Icons.Default.Download, contentDescription = "Install .xpi")
                }
              }
            }
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              OutlinedButton(
                onClick = onImportLocalXpiClick,
                enabled = !isInstalling,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("import_xpi_button"),
              ) {
                Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Import .xpi File", maxLines = 1, overflow = TextOverflow.Ellipsis)
              }
              OutlinedButton(
                onClick = {
                  onDismiss()
                  onOpenAmoStore()
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("browse_amo_button"),
              ) {
                Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Mozilla AMO", maxLines = 1, overflow = TextOverflow.Ellipsis)
              }
            }
          }
        }
      }

      // Installed Add-ons
      item {
        Text(
          text = "INSTALLED ADD-ONS (${addons.size})",
          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      if (addons.isEmpty()) {
        item {
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = voltColors.cardSurface,
            border = BorderStroke(1.dp, voltColors.cardBorder),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text =
                "No Firefox add-ons installed yet. Tap any curated extension below, import a local .xpi file, or browse addons.mozilla.org.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(14.dp),
            )
          }
        }
      } else {
        items(addons, key = { it.id }) { addon ->
          InstalledAddonCard(
            addon = addon,
            onToggleEnabled = { enabled -> onToggleAddonEnabled(addon.id, enabled) },
            onTogglePrivate = { allowed -> onToggleAddonPrivate(addon.id, allowed) },
            onOpenPopup = { onOpenAddonPopup(addon.id) },
            onUninstall = { onUninstallAddon(addon.id) },
          )
        }
      }

      // Curated Featured Catalog
      item {
        Text(
          text = "POPULAR FIREFOX ADD-ONS (1-TAP INSTALL)",
          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      items(FirefoxAddonManager.CURATED_ADDONS, key = { it.slug }) { catalogItem ->
        val isInstalled = installedIds.contains(catalogItem.id)
        CatalogAddonCard(
          item = catalogItem,
          isInstalled = isInstalled,
          isInstalling = isInstalling,
          onInstall = { onInstallFromUrl(catalogItem.xpiUrl) },
        )
      }

      item { Spacer(modifier = Modifier.height(12.dp)) }
    }
  }
}

@Composable
private fun InstalledAddonCard(
  addon: FirefoxAddonInfo,
  onToggleEnabled: (Boolean) -> Unit,
  onTogglePrivate: (Boolean) -> Unit,
  onOpenPopup: () -> Unit,
  onUninstall: () -> Unit,
) {
  val voltColors = LocalVoltColors.current
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = voltColors.cardSurface,
    border =
      BorderStroke(
        1.dp,
        if (addon.enabled) voltColors.emeraldShield.copy(alpha = 0.45f) else voltColors.cardBorder,
      ),
    modifier = Modifier.fillMaxWidth().testTag("addon_card_${addon.id}"),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Icon(
          imageVector = Icons.Default.Extension,
          contentDescription = null,
          tint = if (addon.enabled) voltColors.emeraldShield else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "${addon.name} ${if (addon.version.isNotBlank()) "v${addon.version}" else ""}".trim(),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          if (addon.description.isNotBlank()) {
            Text(
              text = addon.description,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
        Switch(
          checked = addon.enabled,
          onCheckedChange = onToggleEnabled,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Surface(
          shape = RoundedCornerShape(100.dp),
          color =
            if (addon.allowedInPrivateBrowsing) voltColors.incognitoAccent.copy(alpha = 0.16f)
            else voltColors.omniboxSurface,
          border =
            BorderStroke(
              1.dp,
              if (addon.allowedInPrivateBrowsing) voltColors.incognitoAccent.copy(alpha = 0.5f)
              else voltColors.cardBorder,
            ),
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .clip(RoundedCornerShape(100.dp))
              .clickable { onTogglePrivate(!addon.allowedInPrivateBrowsing) }
              .testTag("addon_private_toggle_${addon.id}"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
          ) {
            Icon(
              imageVector = Icons.Outlined.VisibilityOff,
              contentDescription = null,
              tint =
                if (addon.allowedInPrivateBrowsing) voltColors.incognitoAccent
                else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(14.dp),
            )
            Text(
              text = if (addon.allowedInPrivateBrowsing) "Allowed in Private" else "Private Off",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
        }

        Row(
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (addon.hasBrowserAction && addon.enabled) {
            OutlinedButton(
              onClick = onOpenPopup,
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
              modifier = Modifier.minimumInteractiveComponentSize().testTag("addon_open_popup_${addon.id}"),
            ) {
              Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Open UI", style = MaterialTheme.typography.labelSmall)
            }
          }
          IconButton(
            onClick = onUninstall,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("addon_uninstall_${addon.id}"),
          ) {
            Icon(
              imageVector = Icons.Default.DeleteOutline,
              contentDescription = "Uninstall ${addon.name}",
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.size(20.dp),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun CatalogAddonCard(
  item: AddonCatalogItem,
  isInstalled: Boolean,
  isInstalling: Boolean,
  onInstall: () -> Unit,
) {
  val voltColors = LocalVoltColors.current
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = voltColors.cardSurface,
    border = BorderStroke(1.dp, voltColors.cardBorder),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = item.name,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
          )
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = voltColors.brandAccent.copy(alpha = 0.14f),
          ) {
            Text(
              text = item.category,
              style = MaterialTheme.typography.labelSmall,
              color = voltColors.brandAccent,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
          }
        }
        Text(
          text = item.summary,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }

      if (isInstalled) {
        Surface(
          shape = RoundedCornerShape(100.dp),
          color = voltColors.emeraldShield.copy(alpha = 0.16f),
          border = BorderStroke(1.dp, voltColors.emeraldShield.copy(alpha = 0.45f)),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(
              imageVector = Icons.Default.Check,
              contentDescription = "Installed",
              tint = voltColors.emeraldShield,
              modifier = Modifier.size(14.dp),
            )
            Text(
              text = "Installed",
              style = MaterialTheme.typography.labelSmall,
              color = voltColors.emeraldShield,
            )
          }
        }
      } else {
        Button(
          onClick = onInstall,
          enabled = !isInstalling,
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
          modifier = Modifier.minimumInteractiveComponentSize().testTag("catalog_install_${item.slug}"),
        ) {
          Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Install", style = MaterialTheme.typography.labelMedium)
        }
      }
    }
  }
}

/** Confirmation dialog displaying Firefox WebExtension requested permissions prior to install. */
@Composable
fun AddonInstallPromptDialog(
  prompt: AddonInstallPromptState,
  onDecision: (allow: Boolean, allowPrivate: Boolean) -> Unit,
) {
  var allowPrivate by remember(prompt.extensionId) { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = { onDecision(false, false) },
    icon = {
      Icon(
        imageVector = Icons.Default.Shield,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
      )
    },
    title = {
      Text(
        text = "Add ${prompt.name}?",
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!prompt.version.isNullOrBlank()) {
          Text(
            text = "Version ${prompt.version} • ${prompt.extensionId}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        val permList = (prompt.permissions + prompt.origins).distinct()
        if (permList.isNotEmpty()) {
          Text(
            text = "This add-on requests permission to:",
            style = MaterialTheme.typography.labelLarge,
          )
          permList.take(8).forEach { perm ->
            Text(
              text = "• $perm",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
          }
          if (permList.size > 8) {
            Text(
              text = "+ ${permList.size - 8} more permissions",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        } else {
          Text(
            text = "This add-on requests no special browser permissions.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.fillMaxWidth().clickable { allowPrivate = !allowPrivate },
        ) {
          Checkbox(checked = allowPrivate, onCheckedChange = { allowPrivate = it })
          Text(
            text = "Allow extension to run in Private tabs",
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onDecision(true, allowPrivate) },
        modifier = Modifier.testTag("confirm_addon_install_button"),
      ) {
        Text("Add Extension")
      }
    },
    dismissButton = {
      TextButton(onClick = { onDecision(false, false) }) {
        Text("Cancel")
      }
    },
  )
}

/** Modal popup window rendering an active WebExtension `browser_action` or `page_action` UI. */
@Composable
fun AddonPopupDialog(
  popup: AddonPopupState,
  onDismiss: () -> Unit,
) {
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      shape = RoundedCornerShape(22.dp),
      color = MaterialTheme.colorScheme.surface,
      border = BorderStroke(1.dp, LocalVoltColors.current.cardBorder),
      modifier = Modifier.fillMaxWidth(0.94f).heightIn(min = 360.dp, max = 580.dp),
    ) {
      Column(modifier = Modifier.fillMaxWidth()) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Default.Extension,
              contentDescription = null,
              tint = LocalVoltColors.current.brandAccent,
              modifier = Modifier.size(18.dp),
            )
            Text(
              text = popup.addonName,
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Close add-on popup")
          }
        }
        AndroidView(
          factory = { ctx ->
            GeckoView(ctx).apply {
              setSession(popup.session)
            }
          },
          update = { gv ->
            if (gv.session !== popup.session) {
              gv.releaseSession()
              gv.setSession(popup.session)
            }
          },
          onRelease = { gv ->
            gv.releaseSession()
          },
          modifier = Modifier.fillMaxWidth().weight(1f),
        )
      }
    }
  }
}
