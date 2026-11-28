package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SearchEngine
import com.example.ui.theme.LocalVoltColors
import com.example.ui.theme.badgeAccentColor

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OmniboxQuickBar(
  activeEngine: SearchEngine,
  isFocused: Boolean,
  onSelectEngine: (SearchEngine) -> Unit,
  onLongPressSetDefault: (SearchEngine) -> Unit,
  onCloseTray: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val voltColors = LocalVoltColors.current
  val haptics = LocalHapticFeedback.current
  val engineListState = rememberLazyListState()

  LaunchedEffect(activeEngine) {
    val idx = SearchEngine.entries.indexOf(activeEngine)
    if (idx >= 0) {
      engineListState.animateScrollToItem(idx)
    }
  }

  Surface(
    modifier = modifier.fillMaxWidth().testTag("omnibox_quick_bar"),
    color = voltColors.dockSurface,
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        LazyRow(
          state = engineListState,
          modifier = Modifier.weight(1f),
          contentPadding = PaddingValues(horizontal = 12.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          items(SearchEngine.entries, key = { it.name }) { engine ->
            val selected = engine == activeEngine
            val accent = engine.badgeAccentColor(voltColors.isLightTheme)
            Surface(
              shape = RoundedCornerShape(14.dp),
              color =
                if (selected) accent.copy(alpha = 0.16f)
                else voltColors.cardSurface,
              border =
                BorderStroke(
                  width = if (selected) 1.5.dp else 1.dp,
                  color = if (selected) accent else voltColors.cardBorder,
                ),
              modifier =
                Modifier.minimumInteractiveComponentSize()
                  .clip(RoundedCornerShape(14.dp))
                  .combinedClickable(
                    onClick = {
                      haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                      onSelectEngine(engine)
                    },
                    onLongClick = {
                      haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                      onLongPressSetDefault(engine)
                    },
                  )
                  .testTag("omnibox_engine_chip_${engine.name.lowercase()}"),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Box(
                  modifier =
                    Modifier.size(22.dp)
                      .clip(CircleShape)
                      .background(accent.copy(alpha = if (selected) 0.94f else 0.18f)),
                  contentAlignment = Alignment.Center,
                ) {
                  Text(
                    text = engine.badgeCode,
                    style =
                      MaterialTheme.typography.labelSmall.copy(
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.2).sp,
                      ),
                    color =
                      if (selected) {
                        if (voltColors.isLightTheme || accent.luminanceApprox() < 0.55f) Color.White
                        else Color(0xFF141417)
                      } else {
                        accent
                      },
                    maxLines = 1,
                    softWrap = false,
                  )
                }
                Text(
                  text = engine.shortLabel,
                  style =
                    MaterialTheme.typography.labelLarge.copy(
                      fontSize = 12.sp,
                      fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    ),
                  color =
                    if (selected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
        }

        if (!isFocused) {
          IconButton(
            onClick = onCloseTray,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("omnibox_close_engine_tray"),
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Close search engine picker",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }

      HorizontalDivider(thickness = 0.5.dp, color = voltColors.cardBorder.copy(alpha = 0.6f))
    }
  }
}

private fun Color.luminanceApprox(): Float = 0.299f * red + 0.587f * green + 0.114f * blue
