package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.BrowserTabState
import com.example.ui.components.extractHost
import com.example.ui.theme.LocalVoltColors

@Composable
fun TabSwitcherOverlay(
  tabs: List<BrowserTabState>,
  activeTabId: String,
  onSelectTab: (String) -> Unit,
  onCloseTab: (String) -> Unit,
  onNewTab: (Boolean) -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val voltColors = LocalVoltColors.current

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
        Column {
          Text(
            text = "Open Tabs (${tabs.size})",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
          )
          Text(
            text = "Pre-warmed WebView pool",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_tab_switcher")) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close tab switcher",
            tint = MaterialTheme.colorScheme.onBackground,
          )
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
          Surface(
            modifier =
              Modifier.fillMaxWidth()
                .height(148.dp)
                .clip(cardShape)
                .border(if (isActive) 2.dp else 1.dp, borderColor, cardShape)
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
                  modifier = Modifier.size(28.dp).testTag("close_tab_${tab.id}"),
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
