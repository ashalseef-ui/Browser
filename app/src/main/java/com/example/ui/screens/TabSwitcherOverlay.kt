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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
  var filterMode by remember { mutableIntStateOf(0) } // 0 = All, 1 = Standard, 2 = Private

  val visibleTabs =
    remember(tabs, filterMode) {
      when (filterMode) {
        1 -> tabs.filter { !it.isIncognito }
        2 -> tabs.filter { it.isIncognito }
        else -> tabs
      }
    }

  Surface(
    modifier = modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.background,
  ) {
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
      // Header
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Tab Deck",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
          )
          Text(
            text = "${tabs.size} active • Swipe sideways to close",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          if (tabs.size > 1) {
            Surface(
              shape = RoundedCornerShape(100.dp),
              color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
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
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.DeleteSweep,
                  contentDescription = "Close all tabs",
                  tint = MaterialTheme.colorScheme.error,
                  modifier = Modifier.size(16.dp),
                )
                Text(
                  text = "Clear All",
                  style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                  color = MaterialTheme.colorScheme.error,
                )
              }
            }
          }
          Surface(
            shape = CircleShape,
            color = voltColors.cardSurface,
            border = BorderStroke(1.dp, voltColors.cardBorder),
          ) {
            IconButton(
              onClick = onDismiss,
              modifier = Modifier.testTag("close_tab_switcher"),
            ) {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close tab deck",
                tint = MaterialTheme.colorScheme.onSurface,
              )
            }
          }
        }
      }

      // Segmented Workspace Filter Pills
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        val standardCount = tabs.count { !it.isIncognito }
        val privateCount = tabs.count { it.isIncognito }
        listOf(
          Triple(0, "All (${tabs.size})", voltColors.brandAccent),
          Triple(1, "Standard ($standardCount)", MaterialTheme.colorScheme.onSurface),
          Triple(2, "Private ($privateCount)", voltColors.incognitoAccent),
        ).forEach { (idx, label, accent) ->
          val selected = filterMode == idx
          Surface(
            shape = RoundedCornerShape(100.dp),
            color = if (selected) accent.copy(alpha = 0.16f) else voltColors.cardSurface,
            border =
              BorderStroke(
                1.dp,
                if (selected) accent.copy(alpha = 0.65f) else voltColors.cardBorder,
              ),
            modifier =
              Modifier.clip(RoundedCornerShape(100.dp)).clickable { filterMode = idx },
          ) {
            Text(
              text = label,
              style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
              ),
              color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            )
          }
        }
      }

      if (visibleTabs.isEmpty()) {
        Box(
          modifier = Modifier.weight(1f).fillMaxWidth(),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = "No tabs in this category",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      } else {
        LazyVerticalGrid(
          columns = GridCells.Adaptive(minSize = 164.dp),
          modifier = Modifier.weight(1f).fillMaxWidth(),
          contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          items(visibleTabs, key = { it.id }) { tab ->
            val isActive = tab.id == activeTabId
            val accentColor =
              when {
                tab.isIncognito -> voltColors.incognitoAccent
                isActive -> voltColors.brandAccent
                else -> voltColors.cardBorder
              }
            val cardShape = RoundedCornerShape(22.dp)
            val swipeOffsetX = remember(tab.id) { Animatable(0f) }
            var armedCloseHaptic by remember(tab.id) { mutableStateOf(false) }

            Box(modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp)) {
              if (abs(swipeOffsetX.value) > 16f) {
                Box(
                  modifier =
                    Modifier.matchParentSize()
                      .clip(cardShape)
                      .background(MaterialTheme.colorScheme.error.copy(alpha = 0.16f))
                      .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f), cardShape)
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
                    .heightIn(min = 160.dp)
                    .offset { IntOffset(swipeOffsetX.value.roundToInt(), 0) }
                    .graphicsLayer {
                      alpha = (1f - abs(swipeOffsetX.value) / 360f).coerceIn(0.28f, 1f)
                      rotationZ = (swipeOffsetX.value / 42f).coerceIn(-6f, 6f)
                    }
                    .clip(cardShape)
                    .border(
                      width = if (isActive) 2.dp else 1.dp,
                      color = if (isActive) accentColor else voltColors.cardBorder,
                      shape = cardShape,
                    )
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
                Column(modifier = Modifier.fillMaxSize()) {
                  // Top Architectural Accent Strip
                  Box(
                    modifier =
                      Modifier.fillMaxWidth()
                        .height(5.dp)
                        .background(
                          if (isActive || tab.isIncognito) accentColor
                          else voltColors.cardElevatedSurface
                        )
                  )

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
                            Modifier.size(26.dp)
                              .clip(RoundedCornerShape(8.dp))
                              .background(
                                if (tab.isIncognito) voltColors.incognitoAccent.copy(alpha = 0.2f)
                                else voltColors.brandAccent.copy(alpha = 0.14f)
                              ),
                          contentAlignment = Alignment.Center,
                        ) {
                          Icon(
                            imageVector =
                              if (tab.isIncognito) Icons.Outlined.VisibilityOff else Icons.Default.Bolt,
                            contentDescription = null,
                            tint =
                              if (tab.isIncognito) voltColors.incognitoAccent
                              else voltColors.brandAccent,
                            modifier = Modifier.size(14.dp),
                          )
                        }
                        Text(
                          text = if (tab.isIncognito) "PRIVATE" else extractHost(tab.url),
                          style = MaterialTheme.typography.labelSmall,
                          color =
                            if (tab.isIncognito) voltColors.incognitoAccent
                            else MaterialTheme.colorScheme.onSurfaceVariant,
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
                      text = tab.title.ifBlank { "Start Page" },
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
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                      ) {
                        if (!tab.isSpeedDial) {
                          Icon(
                            imageVector = if (tab.isSecure) Icons.Default.Lock else Icons.Default.Warning,
                            contentDescription = null,
                            tint =
                              if (tab.isSecure) voltColors.emeraldShield
                              else voltColors.amberAccent,
                            modifier = Modifier.size(12.dp),
                          )
                        }
                        Text(
                          text =
                            when {
                              tab.isSpeedDial -> "Start Page"
                              tab.isSecure -> "HTTPS"
                              else -> "HTTP"
                            },
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                      }

                      tab.lastLoadTimeMs?.let { ms ->
                        Text(
                          text = "${ms}ms",
                          style = MaterialTheme.typography.labelSmall,
                          color = voltColors.brandAccent,
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }

      // Bottom Split Action Pill Bar
      Surface(
        color = voltColors.dockSurface,
        border = BorderStroke(1.dp, voltColors.cardBorder.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          OutlinedButton(
            onClick = { onNewTab(true) },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.weight(1f).height(52.dp).testTag("new_private_tab_button"),
          ) {
            Icon(
              imageVector = Icons.Outlined.VisibilityOff,
              contentDescription = null,
              tint = voltColors.incognitoAccent,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.new_private_tab), color = MaterialTheme.colorScheme.onSurface)
          }

          Button(
            onClick = { onNewTab(false) },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.weight(1f).height(52.dp).testTag("new_tab_button"),
            colors =
              ButtonDefaults.buttonColors(
                containerColor = voltColors.brandAccent,
                contentColor = voltColors.onBrandAccent,
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
}
