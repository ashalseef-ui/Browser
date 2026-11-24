package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.BrowserTabState
import com.example.ui.components.extractHost
import com.example.ui.theme.LocalVoltColors
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun TabSwitcherOverlay(
  tabs: List<BrowserTabState>,
  activeTabId: String,
  onSelectTab: (String) -> Unit,
  onCloseTab: (String) -> Unit,
  onCloseAllTabs: () -> Unit = {},
  onNewTab: (Boolean) -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val voltColors = LocalVoltColors.current
  val haptics = LocalHapticFeedback.current
  val scope = rememberCoroutineScope()

  Surface(
    modifier = modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.background,
  ) {
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Open Tabs (${tabs.size})",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
          )
          Text(
            text = "Swipe any card sideways to close",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          if (tabs.size > 1) {
            Surface(
              shape = RoundedCornerShape(100.dp),
              color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
              modifier =
                Modifier.minimumInteractiveComponentSize()
                  .clip(RoundedCornerShape(100.dp))
                  .clickable {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onCloseAllTabs()
                  }
                  .testTag("close_all_tabs_button"),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.DeleteSweep,
                  contentDescription = "Close all tabs",
                  tint = MaterialTheme.colorScheme.error,
                  modifier = Modifier.size(16.dp),
                )
                Text(
                  text = "Close All",
                  style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                  color = MaterialTheme.colorScheme.error,
                )
              }
            }
          }
          IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_tab_switcher")) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Close tab switcher",
              tint = MaterialTheme.colorScheme.onBackground,
            )
          }
        }
      }

      LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 164.dp),
        modifier = Modifier.weight(1f).fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        items(tabs, key = { it.id }) { tab ->
          val isActive = tab.id == activeTabId
          val borderColor =
            when {
              isActive && tab.isIncognito -> voltColors.incognitoAccent
              isActive -> voltColors.cyanAccent
              else -> voltColors.cardBorder
            }

          val cardShape = RoundedCornerShape(18.dp)
          val swipeOffsetX = remember(tab.id) { Animatable(0f) }
          var armedCloseHaptic by remember(tab.id) { mutableStateOf(false) }

          Box(modifier = Modifier.fillMaxWidth().heightIn(min = 148.dp)) {
            if (abs(swipeOffsetX.value) > 16f) {
              Box(
                modifier =
                  Modifier.matchParentSize()
                    .clip(cardShape)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.16f))
                    .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f), cardShape)
                    .padding(horizontal = 18.dp),
                contentAlignment =
                  if (swipeOffsetX.value > 0f) Alignment.CenterStart else Alignment.CenterEnd,
              ) {
                Icon(
                  imageVector = Icons.Default.Close,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.error,
                  modifier = Modifier.size(22.dp),
                )
              }
            }

            Surface(
              modifier =
                Modifier.fillMaxWidth()
                  .heightIn(min = 148.dp)
                  .offset { IntOffset(swipeOffsetX.value.roundToInt(), 0) }
                  .graphicsLayer {
                    alpha = (1f - abs(swipeOffsetX.value) / 360f).coerceIn(0.28f, 1f)
                    rotationZ = (swipeOffsetX.value / 42f).coerceIn(-6f, 6f)
                  }
                  .clip(cardShape)
                  .border(if (isActive) 2.dp else 1.dp, borderColor, cardShape)
                  .pointerInput(tab.id) {
                    detectHorizontalDragGestures(
                      onDragStart = { armedCloseHaptic = false },
                      onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch {
                          val next = swipeOffsetX.value + dragAmount
                          swipeOffsetX.snapTo(next)
                          if (!armedCloseHaptic && abs(next) >= 110f) {
                            armedCloseHaptic = true
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                          }
                        }
                      },
                      onDragEnd = {
                        scope.launch {
                          if (abs(swipeOffsetX.value) >= 110f) {
                            val target = if (swipeOffsetX.value > 0f) 500f else -500f
                            swipeOffsetX.animateTo(target, spring())
                            onCloseTab(tab.id)
                          } else {
                            swipeOffsetX.animateTo(0f, spring())
                          }
                        }
                      },
                      onDragCancel = {
                        scope.launch { swipeOffsetX.animateTo(0f, spring()) }
                      },
                    )
                  }
                  .clickable { onSelectTab(tab.id) }
                  .testTag("tab_card_${tab.id}"),
              shape = cardShape,
              color = voltColors.cardSurface,
            ) {
              Column(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f),
                  ) {
                    Box(
                      modifier =
                        Modifier.size(24.dp)
                          .clip(CircleShape)
                          .background(
                            if (tab.isIncognito) voltColors.incognitoAccent.copy(alpha = 0.2f)
                            else voltColors.cyanAccent.copy(alpha = 0.16f)
                          ),
                      contentAlignment = Alignment.Center,
                    ) {
                      Icon(
                        imageVector =
                          if (tab.isIncognito) Icons.Outlined.VisibilityOff else Icons.Default.Bolt,
                        contentDescription = null,
                        tint =
                          if (tab.isIncognito) voltColors.incognitoAccent else voltColors.cyanAccent,
                        modifier = Modifier.size(14.dp),
                      )
                    }
                    Text(
                      text = if (tab.isIncognito) "PRIVATE" else extractHost(tab.url),
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }

                  IconButton(
                    onClick = { onCloseTab(tab.id) },
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("close_tab_${tab.id}"),
                  ) {
                    Icon(
                      imageVector = Icons.Default.Close,
                      contentDescription = stringResource(R.string.close_tab),
                      tint = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.size(16.dp),
                    )
                  }
                }

                Text(
                  text = tab.title.ifBlank { "Speed Dial" },
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.onSurface,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                )

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  if (tab.trackersBlockedOnPage > 0) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                      Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = voltColors.emeraldShield,
                        modifier = Modifier.size(12.dp),
                      )
                      Text(
                        text = "${tab.trackersBlockedOnPage} blocked",
                        style = MaterialTheme.typography.labelSmall,
                        color = voltColors.emeraldShield,
                      )
                    }
                  } else {
                    Text(
                      text = if (tab.isSpeedDial) "Speed Dial" else "Active",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }

                  tab.lastLoadTimeMs?.let { ms ->
                    Text(
                      text = "${ms}ms",
                      style = MaterialTheme.typography.labelSmall,
                      color = voltColors.cyanAccent,
                    )
                  }
                }
              }
            }
          }
        }
      }

      // Bottom Tab Creation Bar
      Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        OutlinedButton(
          onClick = { onNewTab(true) },
          modifier = Modifier.weight(1f).height(50.dp).testTag("new_private_tab_button"),
        ) {
          Icon(
            imageVector = Icons.Outlined.VisibilityOff,
            contentDescription = null,
            tint = voltColors.incognitoAccent,
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(stringResource(R.string.new_private_tab))
        }

        Button(
          onClick = { onNewTab(false) },
          modifier = Modifier.weight(1f).height(50.dp).testTag("new_tab_button"),
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
          Icon(imageVector = Icons.Default.Add, contentDescription = null)
          Spacer(modifier = Modifier.width(6.dp))
          Text(stringResource(R.string.new_tab))
        }
      }
    }
  }
}
