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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.VisibilityOff
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
import androidx.compose.runtime.mutableFloatStateOf
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
import com.example.data.BrowserTabState
import com.example.data.SearchEngine
import com.example.ui.theme.LocalVoltColors
import com.example.ui.theme.badgeAccentColor
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OmniboxDock(
  tab: BrowserTabState,
  tabCount: Int,
  previousTabTitle: String = "",
  nextTabTitle: String = "",
  searchEngine: SearchEngine,
  isVpnActive: Boolean,
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

  val deckShape =
    if (showBottomNavControls) RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    else RoundedCornerShape(0.dp)

  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = deckShape,
    color = voltColors.dockSurface,
    border =
      if (showBottomNavControls) BorderStroke(1.dp, voltColors.cardBorder.copy(alpha = 0.7f))
      else null,
    shadowElevation = if (showBottomNavControls) 12.dp else 0.dp,
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      // Top drag pill handle for phone bottom deck
      if (showBottomNavControls) {
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .height(18.dp)
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
              Modifier.width(36.dp)
                .height(3.5.dp)
                .clip(CircleShape)
                .background(
                  if (abs(tabSwipeDragX) >= 48f) voltColors.brandAccent
                  else voltColors.cardBorder
                )
          )
        }
      }

      // Live Tab-Peek Gesture Banner when swiping horizontally across bottom deck / tabs counter
      AnimatedVisibility(visible = showBottomNavControls && tabCount > 1 && abs(tabSwipeDragX) > 18f) {
        val isSwipingNext = tabSwipeDragX < 0f
        val peekTitle =
          if (isSwipingNext) nextTabTitle.ifBlank { "Next Tab" }
          else previousTabTitle.ifBlank { "Previous Tab" }
        val armed = abs(tabSwipeDragX) >= 48f
        Box(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
          contentAlignment = Alignment.Center,
        ) {
          Surface(
            shape = RoundedCornerShape(100.dp),
            color =
              if (armed) voltColors.brandAccent.copy(alpha = 0.18f)
              else voltColors.cardSurface,
            border = BorderStroke(1.dp, if (armed) voltColors.brandAccent else voltColors.cardBorder),
          ) {
            Text(
              text = if (isSwipingNext) "$peekTitle  →" else "←  $peekTitle",
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
              color = if (armed) voltColors.brandAccent else MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
          }
        }
      }

      // Expandable Search Engine Selector Bar (above omnibox on phone)
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

      // Sleek loading progress bar
      if (tab.isLoading) {
        LinearProgressIndicator(
          progress = { (tab.progress / 100f).coerceIn(0.06f, 1f) },
          modifier = Modifier.fillMaxWidth().height(2.5.dp),
          color = voltColors.brandAccent,
          trackColor = voltColors.cardSurface,
        )
      }

      // Row 1: Unified Address & Mode Capsule + Media Studio Trigger
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        val capsuleShape = RoundedCornerShape(18.dp)
        val engineAccent = searchEngine.badgeAccentColor(voltColors.isLightTheme)
        val capsuleBorderColor =
          when {
            isFocused -> voltColors.brandAccent.copy(alpha = 0.85f)
            isEngineTrayOpen -> engineAccent
            tab.isIncognito -> voltColors.incognitoAccent.copy(alpha = 0.55f)
            else -> voltColors.cardBorder
          }

        Row(
          modifier =
            Modifier.weight(1f)
              .heightIn(min = 48.dp)
              .clip(capsuleShape)
              .background(voltColors.omniboxSurface)
              .border(
                width = if (isFocused || isEngineTrayOpen) 1.5.dp else 1.dp,
                color = capsuleBorderColor,
                shape = capsuleShape,
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
              .padding(horizontal = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          // Security / Session State Button (compact squircle inside capsule)
          val shieldTint =
            when {
              tab.isMediaCompatBypassEnabled -> voltColors.amberAccent
              tab.isIncognito -> voltColors.incognitoAccent
              !tab.isSpeedDial && !tab.isSecure -> voltColors.amberAccent
              else -> voltColors.emeraldShield
            }
          Box(
            modifier =
              Modifier.minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(14.dp))
                .background(shieldTint.copy(alpha = 0.14f))
                .clickable(onClick = onOpenSecuritySheet)
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .testTag("security_shield_button"),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector =
                when {
                  tab.isMediaCompatBypassEnabled -> Icons.Default.Warning
                  tab.isIncognito -> Icons.Outlined.VisibilityOff
                  tab.isSpeedDial -> Icons.Default.Shield
                  tab.isSecure -> Icons.Default.Lock
                  else -> Icons.Default.Warning
                },
              contentDescription = stringResource(R.string.cd_security_shield),
              tint = shieldTint,
              modifier = Modifier.size(16.dp),
            )
          }

          // Search Engine Pill (Tap = open tray, Swipe = cycle engine)
          Box(
            modifier =
              Modifier.minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(12.dp))
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
                .padding(horizontal = 3.dp, vertical = 4.dp)
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
              val badgeColor = targetEngine.badgeAccentColor(voltColors.isLightTheme)
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier =
                  Modifier.clip(RoundedCornerShape(10.dp))
                    .background(badgeColor.copy(alpha = 0.14f))
                    .padding(start = 6.dp, top = 5.dp, end = 3.dp, bottom = 5.dp),
              ) {
                Text(
                  text = targetEngine.badgeCode,
                  style =
                    MaterialTheme.typography.labelSmall.copy(
                      fontSize = 10.sp,
                      fontWeight = FontWeight.ExtraBold,
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
                    stringResource(R.string.cd_search_engine_selector, targetEngine.displayName),
                  tint = badgeColor,
                  modifier = Modifier.size(13.dp),
                )
              }
            }
          }

          // Input / Address display
          Box(
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            contentAlignment = Alignment.CenterStart,
          ) {
            if (inputText.isEmpty()) {
              Text(
                text = "Search ${searchEngine.shortLabel} or type URL",
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
              cursorBrush = SolidColor(voltColors.brandAccent),
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
                    if (state.isFocused && !tab.isSpeedDial && inputText.isEmpty()) {
                      inputText = tab.url
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
                tint = voltColors.brandAccent,
                modifier = Modifier.size(18.dp),
              )
            }
          } else if (!tab.isSpeedDial) {
            Box(
              modifier =
                Modifier.minimumInteractiveComponentSize()
                  .clip(RoundedCornerShape(12.dp))
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

        // Media Studio Action Pill (right of address capsule)
        val hasSniffedMedia = tab.detectedMedia.isNotEmpty()
        Surface(
          shape = RoundedCornerShape(16.dp),
          color =
            if (hasSniffedMedia) voltColors.brandAccent
            else voltColors.cardSurface,
          border =
            if (hasSniffedMedia) null
            else BorderStroke(1.dp, voltColors.cardBorder),
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .heightIn(min = 48.dp)
              .clip(RoundedCornerShape(16.dp))
              .clickable(onClick = onOpenMediaHub)
              .testTag("media_detected_pill"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = if (hasSniffedMedia) 12.dp else 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(
              imageVector = Icons.Default.PlayArrow,
              contentDescription = stringResource(R.string.cd_media_detected),
              tint =
                if (hasSniffedMedia) voltColors.onBrandAccent
                else MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.size(19.dp),
            )
            if (hasSniffedMedia) {
              Text(
                text = "${tab.detectedMedia.size}",
                style = MaterialTheme.typography.labelLarge,
                color = voltColors.onBrandAccent,
              )
            }
          }
        }
      }

      // On wide-screen top dock, expand engine tray below address bar
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
        HorizontalDivider(thickness = 0.5.dp, color = voltColors.cardBorder.copy(alpha = 0.6f))
      }

      // Row 2: Clean 6-Slot Ergonomic Action Bar (Back, Forward, Home, Tabs, Tunnel, Menu)
      if (showBottomNavControls) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
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
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
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
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            )
          }

          // Home (Tap = Start Page, Long-press = New Private Tab)
          Box(
            modifier =
              Modifier.weight(1f)
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(12.dp))
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
                if (tab.isSpeedDial) voltColors.brandAccent
                else MaterialTheme.colorScheme.onSurface,
            )
          }

          // Tabs Switcher Counter (Tap = open Tab Deck, Swipe L/R = switch tabs)
          Box(
            modifier =
              Modifier.weight(1f)
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(12.dp))
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
                Modifier.size(23.dp)
                  .border(
                    width = 1.8.dp,
                    color =
                      if (tab.isIncognito) voltColors.incognitoAccent
                      else MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(7.dp),
                  ),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = if (tabCount > 99) "∞" else "$tabCount",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color =
                  if (tab.isIncognito) voltColors.incognitoAccent
                  else MaterialTheme.colorScheme.onSurface,
              )
            }
          }

          // Tunnel / VPN / Proxy Control Button with Live Status Dot
          Box(
            modifier =
              Modifier.weight(1f)
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onOpenVpnSheet)
                .testTag("omnibox_vpn_button"),
            contentAlignment = Alignment.Center,
          ) {
            Box {
              Icon(
                imageVector = Icons.Default.Public,
                contentDescription = stringResource(R.string.cd_vpn_status),
                tint =
                  if (isVpnActive) voltColors.emeraldShield
                  else MaterialTheme.colorScheme.onSurface,
              )
              if (isVpnActive) {
                Box(
                  modifier =
                    Modifier.align(Alignment.TopEnd)
                      .offset(x = 3.dp, y = (-2).dp)
                      .size(8.dp)
                      .clip(CircleShape)
                      .background(voltColors.emeraldShield)
                )
              }
            }
          }

          // Command Menu (Bookmarks, Find, Desktop, Fullscreen, Share, Settings, Exit & Erase)
          IconButton(
            onClick = onOpenMenu,
            modifier = Modifier.weight(1f).testTag("nav_menu_button"),
          ) {
            Icon(
              imageVector = Icons.Default.Menu,
              contentDescription = stringResource(R.string.cd_menu),
              tint = MaterialTheme.colorScheme.onSurface,
            )
          }
        }
      }
    }
  }
}
