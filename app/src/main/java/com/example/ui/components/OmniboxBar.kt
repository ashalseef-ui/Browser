package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AdBlockerType
import com.example.data.BrowserTabState
import com.example.ui.theme.LocalVoltColors

@Composable
fun OmniboxDock(
  tab: BrowserTabState,
  tabCount: Int,
  adBlockerType: AdBlockerType,
  isVpnActive: Boolean,
  sessionDataUsedBytes: Long,
  showBottomNavControls: Boolean,
  onNavigate: (String) -> Unit,
  onQueryChange: (String) -> Unit,
  onBack: () -> Unit,
  onForward: () -> Unit,
  onReloadOrStop: () -> Unit,
  onHome: () -> Unit,
  onOpenSecuritySheet: () -> Unit,
  onOpenVpnSheet: () -> Unit,
  onOpenMediaHub: () -> Unit,
  onOpenTabs: () -> Unit,
  onOpenMenu: () -> Unit,
  onRequestExit: () -> Unit,
  onSwipeLeft: () -> Unit = {},
  onSwipeRight: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val voltColors = LocalVoltColors.current
  val focusManager = LocalFocusManager.current
  var isFocused by remember { mutableStateOf(false) }
  var inputText by remember(tab.id, tab.url, tab.isSpeedDial) {
    mutableStateOf(if (tab.isSpeedDial) "" else tab.url)
  }

  val latestOnSwipeLeft by rememberUpdatedState(onSwipeLeft)
  val latestOnSwipeRight by rememberUpdatedState(onSwipeRight)

  LaunchedEffect(tab.url, tab.isSpeedDial) {
    if (!isFocused) {
      inputText = if (tab.isSpeedDial) "" else tab.url
    }
  }

  Surface(
    modifier = modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 6.dp,
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      // Hairline loading indicator
      if (tab.isLoading) {
        LinearProgressIndicator(
          progress = { (tab.progress / 100f).coerceIn(0.05f, 1f) },
          modifier = Modifier.fillMaxWidth().height(2.5.dp),
          color = voltColors.cyanAccent,
          trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
      } else {
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
      }

      // Omnibox Address Pill + Security Shield & Media Hub
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        // Security / AdBlocker Shield Button
        val blockerActive = adBlockerType != AdBlockerType.OFF
        val shieldTint =
          when {
            tab.isIncognito -> voltColors.incognitoAccent
            !tab.isSpeedDial && !tab.isSecure -> voltColors.amberAccent
            adBlockerType == AdBlockerType.STRICT -> voltColors.cyanAccent
            blockerActive -> voltColors.emeraldShield
            else -> MaterialTheme.colorScheme.onSurfaceVariant
          }
        Box(
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .clip(RoundedCornerShape(100.dp))
              .background(
                when {
                  tab.isIncognito -> voltColors.incognitoAccent.copy(alpha = 0.16f)
                  blockerActive -> shieldTint.copy(alpha = 0.14f)
                  else -> MaterialTheme.colorScheme.surfaceContainerHigh
                }
              )
              .border(
                width = 1.dp,
                color = if (blockerActive) shieldTint.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(100.dp),
              )
              .clickable(onClick = onOpenSecuritySheet)
              .padding(horizontal = 8.dp, vertical = 6.dp)
              .testTag("security_shield_button"),
          contentAlignment = Alignment.Center,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(
              imageVector =
                when {
                  tab.isSpeedDial -> Icons.Default.Shield
                  tab.isSecure -> Icons.Default.Lock
                  else -> Icons.Default.Warning
                },
              contentDescription = stringResource(R.string.cd_security_shield),
              tint = shieldTint,
              modifier = Modifier.size(15.dp),
            )
            Text(
              text =
                if (tab.trackersBlockedOnPage > 0) "${adBlockerType.badgeLabel}·${tab.trackersBlockedOnPage}"
                else adBlockerType.badgeLabel,
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
              color = shieldTint,
              maxLines = 1,
            )
          }
        }

        // Address Input Pill (extended width)
        val omniboxShape = RoundedCornerShape(22.dp)
        val omniboxBorderColor =
          if (isFocused) voltColors.cyanAccent
          else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
        Row(
          modifier =
            Modifier.weight(1f)
              .height(44.dp)
              .clip(omniboxShape)
              .background(voltColors.omniboxSurface)
              .border(
                width = if (isFocused) 1.3.dp else 1.dp,
                color = omniboxBorderColor,
                shape = omniboxShape,
              )
              .padding(horizontal = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (!isFocused && tab.isSpeedDial) {
            Icon(
              imageVector = Icons.Default.Search,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
          }

          Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (inputText.isEmpty()) {
              Text(
                text = stringResource(R.string.omnibox_placeholder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            BasicTextField(
              value = inputText,
              onValueChange = { next ->
                inputText = next
                onQueryChange(next)
              },
              singleLine = true,
              textStyle =
                MaterialTheme.typography.bodyMedium.copy(
                  color = MaterialTheme.colorScheme.onSurface
                ),
              cursorBrush = SolidColor(voltColors.cyanAccent),
              keyboardOptions =
                KeyboardOptions(
                  keyboardType = KeyboardType.Uri,
                  imeAction = ImeAction.Go,
                ),
              keyboardActions =
                KeyboardActions(
                  onGo = {
                    isFocused = false
                    focusManager.clearFocus()
                    onNavigate(inputText)
                  }
                ),
              modifier =
                Modifier.fillMaxWidth()
                  .onFocusChanged { state ->
                    isFocused = state.isFocused
                    if (state.isFocused) {
                      if (!tab.isSpeedDial && inputText.isEmpty()) {
                        inputText = tab.url
                      }
                    }
                  }
                  .testTag("omnibox_input"),
            )
          }

          if (isFocused && inputText.isNotEmpty()) {
            IconButton(
              onClick = { inputText = "" },
              modifier = Modifier.size(32.dp).testTag("omnibox_clear_button"),
            ) {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Clear address",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
              )
            }
          } else if (!tab.isSpeedDial) {
            IconButton(
              onClick = onReloadOrStop,
              modifier = Modifier.size(32.dp).testTag("omnibox_reload_button"),
            ) {
              Icon(
                imageVector = if (tab.isLoading) Icons.Default.Close else Icons.Default.Refresh,
                contentDescription =
                  if (tab.isLoading) stringResource(R.string.cd_stop)
                  else stringResource(R.string.cd_reload),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
              )
            }
          }
        }

        // Video Sniffer / Media Hub Pill (compact)
        val hasSniffedMedia = tab.detectedMedia.isNotEmpty()
        Surface(
          shape = RoundedCornerShape(100.dp),
          color =
            if (hasSniffedMedia) voltColors.amberAccent.copy(alpha = 0.22f)
            else MaterialTheme.colorScheme.surfaceContainerHigh,
          border =
            if (hasSniffedMedia) androidx.compose.foundation.BorderStroke(1.dp, voltColors.amberAccent)
            else null,
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .clip(RoundedCornerShape(100.dp))
              .clickable(onClick = onOpenMediaHub)
              .testTag("media_detected_pill"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            Icon(
              imageVector = Icons.Default.PlayCircle,
              contentDescription = stringResource(R.string.cd_media_detected),
              tint =
                if (hasSniffedMedia) voltColors.amberAccent
                else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(18.dp),
            )
            if (hasSniffedMedia) {
              Text(
                text = "${tab.detectedMedia.size}",
                style = MaterialTheme.typography.labelMedium,
                color = voltColors.amberAccent,
              )
            }
          }
        }
      }

      // Thumb-first Bottom Control Row (8 equal-width slots: Back, Fwd, Home, Tabs, Proxy/VPN, Menu, Data Used, Exit)
      if (showBottomNavControls) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          IconButton(
            onClick = onBack,
            enabled = !tab.isSpeedDial,
            modifier = Modifier.weight(1f).testTag("nav_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.cd_back),
              tint =
                if (!tab.isSpeedDial) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
          }
          IconButton(
            onClick = onForward,
            enabled = tab.canGoForward || (tab.isSpeedDial && tab.url.isNotBlank()),
            modifier = Modifier.weight(1f).testTag("nav_forward_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowForward,
              contentDescription = stringResource(R.string.cd_forward),
              tint =
                if (tab.canGoForward || (tab.isSpeedDial && tab.url.isNotBlank()))
                  MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
          }
          IconButton(
            onClick = onHome,
            modifier = Modifier.weight(1f).testTag("nav_home_button"),
          ) {
            Icon(
              imageVector = Icons.Default.Home,
              contentDescription = stringResource(R.string.cd_home),
              tint =
                if (tab.isSpeedDial) voltColors.cyanAccent
                else MaterialTheme.colorScheme.onSurface,
            )
          }
          // Tab Counter Pill (tap opens tabs; swipe left/right switches tabs)
          Box(
            modifier =
              Modifier.weight(1f)
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(10.dp))
                .pointerInput(Unit) {
                  var horizontalSum = 0f
                  detectHorizontalDragGestures(
                    onDragStart = { horizontalSum = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                      change.consume()
                      horizontalSum += dragAmount
                    },
                    onDragEnd = {
                      if (horizontalSum > 36f) {
                        latestOnSwipeRight()
                      } else if (horizontalSum < -36f) {
                        latestOnSwipeLeft()
                      }
                    },
                  )
                }
                .clickable(onClick = onOpenTabs)
                .padding(vertical = 8.dp)
                .testTag("nav_tabs_button"),
            contentAlignment = Alignment.Center,
          ) {
            Box(
              modifier =
                Modifier.size(22.dp)
                  .border(
                    width = 1.8.dp,
                    color =
                      if (tab.isIncognito) voltColors.incognitoAccent
                      else MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(6.dp),
                  ),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = if (tabCount > 99) "∞" else "$tabCount",
                style = MaterialTheme.typography.labelSmall,
                color =
                  if (tab.isIncognito) voltColors.incognitoAccent
                  else MaterialTheme.colorScheme.onSurface,
              )
            }
          }
          // Proxy / VPN Icon positioned between Tabs and 3-dots menu
          IconButton(
            onClick = onOpenVpnSheet,
            modifier = Modifier.weight(1f).testTag("omnibox_vpn_button"),
          ) {
            Icon(
              imageVector = Icons.Default.Public,
              contentDescription = stringResource(R.string.cd_vpn_status),
              tint =
                if (isVpnActive) voltColors.emeraldShield
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          IconButton(
            onClick = onOpenMenu,
            modifier = Modifier.weight(1f).testTag("nav_menu_button"),
          ) {
            Icon(
              imageVector = Icons.Default.MoreVert,
              contentDescription = stringResource(R.string.cd_menu),
              tint = MaterialTheme.colorScheme.onSurface,
            )
          }
          // Numeric Data Used Counter between 3-dot Menu and Exit
          Box(
            modifier =
              Modifier.weight(1f)
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onOpenSecuritySheet)
                .padding(vertical = 4.dp)
                .testTag("nav_data_usage_counter"),
            contentAlignment = Alignment.Center,
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center,
            ) {
              Text(
                text = formatCompactDataCounter(sessionDataUsedBytes),
                style = MaterialTheme.typography.labelSmall,
                color = voltColors.cyanAccent,
                maxLines = 1,
                softWrap = false,
              )
              Text(
                text = "DATA",
                style = MaterialTheme.typography.labelSmall.copy(
                  fontSize = androidx.compose.ui.unit.TextUnit(8f, androidx.compose.ui.unit.TextUnitType.Sp),
                  lineHeight = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp),
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
              )
            }
          }
          IconButton(
            onClick = onRequestExit,
            modifier = Modifier.weight(1f).testTag("nav_exit_button"),
          ) {
            Icon(
              imageVector = Icons.Default.PowerSettingsNew,
              contentDescription = stringResource(R.string.cd_exit),
              tint = MaterialTheme.colorScheme.error,
            )
          }
        }
      }
    }
  }
}
