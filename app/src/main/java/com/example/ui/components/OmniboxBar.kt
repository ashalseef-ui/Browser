package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.AdBlockerType
import com.example.data.BrowserTabState
import com.example.data.SearchEngine
import com.example.ui.theme.LocalVoltColors
import com.example.ui.theme.badgeAccentColor
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableFloatStateOf
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OmniboxDock(
  tab: BrowserTabState,
  tabCount: Int,
  previousTabTitle: String = "",
  nextTabTitle: String = "",
  searchEngine: SearchEngine,
  adBlockerType: AdBlockerType,
  isVpnActive: Boolean,
  sessionDataUsedBytes: Long,
  showBottomNavControls: Boolean,
  onNavigate: (String, SearchEngine?) -> Unit,
  onQueryChange: (String) -> Unit,
  onSelectSearchEngine: (SearchEngine) -> Unit,
  onCycleSearchEngine: (Int) -> Unit,
  onBack: () -> Unit,
  onForward: () -> Unit,
  onReloadOrStop: () -> Unit,
  onHardReload: () -> Unit,
  onHome: () -> Unit,
  onNewPrivateTab: () -> Unit,
  onOpenSecuritySheet: () -> Unit,
  onOpenVpnSheet: () -> Unit,
  onOpenMediaHub: () -> Unit,
  onOpenTabs: () -> Unit,
  onOpenMenu: () -> Unit,
  onRequestExit: () -> Unit,
  onShowToast: (String) -> Unit = {},
  onSwipeLeft: () -> Unit = {},
  onSwipeRight: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val voltColors = LocalVoltColors.current
  val focusManager = LocalFocusManager.current
  val haptics = LocalHapticFeedback.current

  var isFocused by remember { mutableStateOf(false) }
  var isEngineTrayOpen by remember { mutableStateOf(false) }
  var swipeDirection by remember { mutableIntStateOf(1) }
  var tabSwipeDragX by remember { mutableFloatStateOf(0f) }
  var inputText by remember(tab.id, tab.url, tab.isSpeedDial) {
    mutableStateOf(if (tab.isSpeedDial) "" else tab.url)
  }

  val latestOnSwipeLeft by rememberUpdatedState(onSwipeLeft)
  val latestOnSwipeRight by rememberUpdatedState(onSwipeRight)
  val latestOnCycleEngine by rememberUpdatedState(onCycleSearchEngine)
  val latestOnOpenTabs by rememberUpdatedState(onOpenTabs)
  val latestIsFocused by rememberUpdatedState(isFocused)

  LaunchedEffect(tab.url, tab.isSpeedDial) {
    if (!isFocused) {
      inputText = if (tab.isSpeedDial) "" else tab.url
    }
  }

  val showQuickBar = isFocused || isEngineTrayOpen

  Surface(
    modifier = modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 6.dp,
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      // Sleek Interactive Gesture Handle (Phone Bottom Dock: Swipe L/R = Tab Peek, Up = Tabs, Down = Collapse)
      if (showBottomNavControls) {
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .height(16.dp)
              .pointerInput(tabCount) {
                var sumX = 0f
                var sumY = 0f
                detectDragGestures(
                  onDragStart = {
                    sumX = 0f
                    sumY = 0f
                    tabSwipeDragX = 0f
                  },
                  onDrag = { change, dragAmount ->
                    change.consume()
                    sumX += dragAmount.x
                    sumY += dragAmount.y
                    if (tabCount > 1 && abs(sumX) > abs(sumY)) {
                      tabSwipeDragX = sumX
                    }
                  },
                  onDragEnd = {
                    if (abs(sumX) > abs(sumY)) {
                      if (tabCount > 1) {
                        if (sumX > 48f) {
                          haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                          latestOnSwipeRight()
                        } else if (sumX < -48f) {
                          haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                          latestOnSwipeLeft()
                        }
                      }
                    } else if (sumY < -28f) {
                      haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                      latestOnOpenTabs()
                    } else if (sumY > 28f) {
                      isEngineTrayOpen = false
                      focusManager.clearFocus()
                    }
                    tabSwipeDragX = 0f
                  },
                  onDragCancel = { tabSwipeDragX = 0f },
                )
              }
              .testTag("omnibox_gesture_handle"),
          contentAlignment = Alignment.Center,
        ) {
          Box(
            modifier =
              Modifier.width(38.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(
                  if (abs(tabSwipeDragX) >= 48f) voltColors.cyanAccent
                  else MaterialTheme.colorScheme.outlineVariant
                )
          )
        }
      }

      // Live Tab-Peek Gesture Banner when swiping horizontally across bottom handle / tab pill
      AnimatedVisibility(visible = showBottomNavControls && tabCount > 1 && abs(tabSwipeDragX) > 18f) {
        val isSwipingNext = tabSwipeDragX < 0f
        val peekTitle =
          if (isSwipingNext) nextTabTitle.ifBlank { "Next Tab" }
          else previousTabTitle.ifBlank { "Previous Tab" }
        val armed = abs(tabSwipeDragX) >= 48f
        Box(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
          contentAlignment = Alignment.Center,
        ) {
          Surface(
            shape = RoundedCornerShape(100.dp),
            color =
              if (armed) voltColors.cyanAccent.copy(alpha = 0.2f)
              else MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, if (armed) voltColors.cyanAccent else voltColors.cardBorder),
          ) {
            Text(
              text = if (isSwipingNext) "$peekTitle  →" else "←  $peekTitle",
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
              color = if (armed) voltColors.cyanAccent else MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
          }
        }
      }

      // On phone bottom dock, expand the Firefox-style engine selector right ABOVE the address bar
      if (showBottomNavControls) {
        AnimatedVisibility(visible = showQuickBar) {
          OmniboxQuickBar(
            activeEngine = searchEngine,
            isFocused = isFocused,
            onSelectEngine = { chosen ->
              val q = inputText.trim()
              onSelectSearchEngine(chosen)
              if (isFocused && q.isNotEmpty() && q != tab.url.trim()) {
                isFocused = false
                isEngineTrayOpen = false
                focusManager.clearFocus()
                onNavigate(q, chosen)
              } else {
                isEngineTrayOpen = false
              }
            },
            onLongPressSetDefault = { chosen ->
              onSelectSearchEngine(chosen)
              onShowToast("Default search: ${chosen.displayName}")
            },
            onCloseTray = { isEngineTrayOpen = false },
          )
        }
      }

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
        // Security / AdBlocker Shield Button (highlights BYP when Site Media Compatibility Bypass is active)
        val blockerActive = adBlockerType != AdBlockerType.OFF && !tab.isMediaCompatBypassEnabled
        val shieldTint =
          when {
            tab.isMediaCompatBypassEnabled -> voltColors.amberAccent
            tab.isIncognito -> voltColors.incognitoAccent
            !tab.isSpeedDial && !tab.isSecure -> voltColors.amberAccent
            adBlockerType == AdBlockerType.STRICT -> voltColors.cyanAccent
            blockerActive -> voltColors.emeraldShield
            else -> MaterialTheme.colorScheme.onSurfaceVariant
          }
        val shieldLabel =
          when {
            tab.isMediaCompatBypassEnabled -> "BYP"
            tab.trackersBlockedOnPage > 0 -> "${adBlockerType.badgeLabel}·${tab.trackersBlockedOnPage}"
            else -> adBlockerType.badgeLabel
          }
        Box(
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .clip(RoundedCornerShape(100.dp))
              .background(
                when {
                  tab.isMediaCompatBypassEnabled -> voltColors.amberAccent.copy(alpha = 0.16f)
                  tab.isIncognito -> voltColors.incognitoAccent.copy(alpha = 0.16f)
                  blockerActive -> shieldTint.copy(alpha = 0.14f)
                  else -> MaterialTheme.colorScheme.surfaceContainerHigh
                }
              )
              .border(
                width = 1.dp,
                color =
                  if (blockerActive || tab.isMediaCompatBypassEnabled) shieldTint.copy(alpha = 0.45f)
                  else MaterialTheme.colorScheme.outlineVariant,
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
                  tab.isMediaCompatBypassEnabled -> Icons.Default.Warning
                  tab.isSpeedDial -> Icons.Default.Shield
                  tab.isSecure -> Icons.Default.Lock
                  else -> Icons.Default.Warning
                },
              contentDescription = stringResource(R.string.cd_security_shield),
              tint = shieldTint,
              modifier = Modifier.size(15.dp),
            )
            Text(
              text = shieldLabel,
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
              color = shieldTint,
              maxLines = 1,
            )
          }
        }

        // Address Input Pill with Inline Swipeable Firefox-Style Search Engine Selector
        val omniboxShape = RoundedCornerShape(24.dp)
        val engineAccent = searchEngine.badgeAccentColor(voltColors.isLightTheme)
        val omniboxBorderColor =
          when {
            isFocused -> engineAccent.copy(alpha = 0.85f)
            isEngineTrayOpen -> voltColors.cyanAccent
            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
          }
        Row(
          modifier =
            Modifier.weight(1f)
              .heightIn(min = 48.dp)
              .clip(omniboxShape)
              .background(voltColors.omniboxSurface)
              .border(
                width = if (isFocused || isEngineTrayOpen) 1.3.dp else 1.dp,
                color = omniboxBorderColor,
                shape = omniboxShape,
              )
              .pointerInput(Unit) {
                var sumX = 0f
                var sumY = 0f
                detectDragGestures(
                  onDragStart = {
                    sumX = 0f
                    sumY = 0f
                  },
                  onDrag = { change, dragAmount ->
                    if (!latestIsFocused) {
                      change.consume()
                      sumX += dragAmount.x
                      sumY += dragAmount.y
                    }
                  },
                  onDragEnd = {
                    if (latestIsFocused) return@detectDragGestures
                    if (abs(sumX) > abs(sumY) && abs(sumX) > 42f) {
                      val step = if (sumX < 0f) 1 else -1
                      swipeDirection = step
                      haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                      latestOnCycleEngine(step)
                    } else if (sumY < -44f && abs(sumY) > abs(sumX)) {
                      haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                      latestOnOpenTabs()
                    }
                  },
                )
              }
              .padding(start = 4.dp, end = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          // Inline Search Engine Pill (Tap = open strip, Swipe L/R = cycle engine anytime!)
          Box(
            modifier =
              Modifier.minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(100.dp))
                .pointerInput(Unit) {
                  var dragSum = 0f
                  detectHorizontalDragGestures(
                    onDragStart = { dragSum = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                      change.consume()
                      dragSum += dragAmount
                    },
                    onDragEnd = {
                      if (abs(dragSum) > 26f) {
                        val step = if (dragSum < 0f) 1 else -1
                        swipeDirection = step
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        latestOnCycleEngine(step)
                      }
                    },
                  )
                }
                .combinedClickable(
                  onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    isEngineTrayOpen = !isEngineTrayOpen
                  },
                  onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    swipeDirection = 1
                    latestOnCycleEngine(1)
                  },
                )
                .padding(horizontal = 4.dp, vertical = 4.dp)
                .testTag("omnibox_search_engine_button"),
            contentAlignment = Alignment.Center,
          ) {
            AnimatedContent(
              targetState = searchEngine,
              transitionSpec = {
                val dir = swipeDirection
                (slideInHorizontally { width -> dir * width / 2 } + fadeIn()) togetherWith
                  (slideOutHorizontally { width -> -dir * width / 2 } + fadeOut())
              },
              label = "omnibox_engine_badge",
            ) { targetEngine ->
              val badgeText = targetEngine.badgeCode
              val badgeColor = targetEngine.badgeAccentColor(voltColors.isLightTheme)
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier =
                  Modifier.clip(RoundedCornerShape(100.dp))
                    .background(badgeColor.copy(alpha = 0.16f))
                    .border(1.dp, badgeColor.copy(alpha = 0.55f), RoundedCornerShape(100.dp))
                    .padding(start = 6.dp, top = 4.dp, end = 3.dp, bottom = 4.dp),
              ) {
                Box(
                  modifier = Modifier.size(6.dp).clip(CircleShape).background(badgeColor)
                )
                Text(
                  text = badgeText,
                  style =
                    MaterialTheme.typography.labelSmall.copy(
                      fontSize = 10.sp,
                      fontWeight = FontWeight.ExtraBold,
                      letterSpacing = (-0.2).sp,
                    ),
                  color = badgeColor,
                  maxLines = 1,
                  softWrap = false,
                )
                Icon(
                  imageVector =
                    if (isEngineTrayOpen) Icons.Default.KeyboardArrowUp
                    else Icons.Default.KeyboardArrowDown,
                  contentDescription =
                    stringResource(
                      R.string.cd_search_engine_selector,
                      targetEngine.displayName,
                    ),
                  tint = badgeColor,
                  modifier = Modifier.size(13.dp),
                )
              }
            }
          }

          Box(
            modifier = Modifier.weight(1f).padding(start = 2.dp),
            contentAlignment = Alignment.CenterStart,
          ) {
            if (inputText.isEmpty()) {
              Text(
                text = "Search ${searchEngine.shortLabel} or enter URL",
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
              cursorBrush = SolidColor(engineAccent),
              keyboardOptions =
                KeyboardOptions(
                  keyboardType = KeyboardType.Uri,
                  imeAction = ImeAction.Go,
                ),
              keyboardActions =
                KeyboardActions(
                  onGo = {
                    isFocused = false
                    isEngineTrayOpen = false
                    focusManager.clearFocus()
                    onNavigate(inputText, null)
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
              modifier = Modifier.minimumInteractiveComponentSize().testTag("omnibox_clear_button"),
            ) {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Clear address",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
              )
            }
            IconButton(
              onClick = {
                isFocused = false
                isEngineTrayOpen = false
                focusManager.clearFocus()
                onNavigate(inputText, null)
              },
              modifier = Modifier.minimumInteractiveComponentSize().testTag("omnibox_go_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = stringResource(R.string.cd_omnibox_go),
                tint = engineAccent,
                modifier = Modifier.size(18.dp),
              )
            }
          } else if (!tab.isSpeedDial) {
            Box(
              modifier =
                Modifier.minimumInteractiveComponentSize()
                  .clip(CircleShape)
                  .combinedClickable(
                    onClick = onReloadOrStop,
                    onLongClick = {
                      haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                      onHardReload()
                    },
                  )
                  .testTag("omnibox_reload_button"),
              contentAlignment = Alignment.Center,
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
            if (hasSniffedMedia) BorderStroke(1.dp, voltColors.amberAccent)
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

      // On wide-screen top dock, expand the engine selector right BELOW the address bar
      if (!showBottomNavControls) {
        AnimatedVisibility(visible = showQuickBar) {
          OmniboxQuickBar(
            activeEngine = searchEngine,
            isFocused = isFocused,
            onSelectEngine = { chosen ->
              val q = inputText.trim()
              onSelectSearchEngine(chosen)
              if (isFocused && q.isNotEmpty() && q != tab.url.trim()) {
                isFocused = false
                isEngineTrayOpen = false
                focusManager.clearFocus()
                onNavigate(q, chosen)
              } else {
                isEngineTrayOpen = false
              }
            },
            onLongPressSetDefault = { chosen ->
              onSelectSearchEngine(chosen)
              onShowToast("Default search: ${chosen.displayName}")
            },
            onCloseTray = { isEngineTrayOpen = false },
          )
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
          // Home (Tap = Speed Dial Home, Long-press = Instant New Private Tab)
          Box(
            modifier =
              Modifier.weight(1f)
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(10.dp))
                .combinedClickable(
                  onClick = onHome,
                  onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onNewPrivateTab()
                  },
                )
                .testTag("nav_home_button"),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Default.Home,
              contentDescription = stringResource(R.string.cd_home),
              tint =
                if (tab.isSpeedDial) voltColors.cyanAccent
                else MaterialTheme.colorScheme.onSurface,
            )
          }
          // Tab Counter Pill (tap opens tabs; swipe left/right switches tabs with live peek banner)
          Box(
            modifier =
              Modifier.weight(1f)
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(10.dp))
                .pointerInput(Unit) {
                  var horizontalSum = 0f
                  detectHorizontalDragGestures(
                    onDragStart = {
                      horizontalSum = 0f
                      tabSwipeDragX = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                      change.consume()
                      horizontalSum += dragAmount
                      tabSwipeDragX = horizontalSum
                    },
                    onDragEnd = {
                      if (horizontalSum > 48f) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        latestOnSwipeRight()
                      } else if (horizontalSum < -48f) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        latestOnSwipeLeft()
                      }
                      tabSwipeDragX = 0f
                    },
                    onDragCancel = { tabSwipeDragX = 0f },
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
                  fontSize = 10.sp,
                  lineHeight = 11.sp,
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
