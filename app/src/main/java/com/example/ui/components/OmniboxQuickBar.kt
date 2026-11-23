package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPasteGo
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TravelExplore
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
import androidx.compose.ui.graphics.vector.ImageVector
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

private val QUICK_TLD_CHIPS = listOf(".com", ".org", ".net", ".io", "/")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OmniboxQuickBar(
  activeEngine: SearchEngine,
  previewEngine: SearchEngine?,
  isOmniBangActive: Boolean,
  isFocused: Boolean,
  inputText: String,
  currentTabUrl: String,
  isSpeedDial: Boolean,
  hasClipboardText: Boolean,
  onSelectEngine: (SearchEngine) -> Unit,
  onLongPressSetDefault: (SearchEngine) -> Unit,
  onLaunchOmniSearch: () -> Unit,
  onPasteAndGo: () -> Unit,
  onInjectText: (String) -> Unit,
  onCopyCurrentUrl: () -> Unit,
  onCloseTray: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val voltColors = LocalVoltColors.current
  val haptics = LocalHapticFeedback.current
  val highlightedEngine = previewEngine ?: activeEngine
  val engineListState = rememberLazyListState()

  LaunchedEffect(highlightedEngine) {
    val idx = SearchEngine.entries.indexOf(highlightedEngine)
    if (idx >= 0) {
      // +1 offsets the leading "All 5" Omni-Search pill
      engineListState.animateScrollToItem((idx + 1).coerceAtMost(SearchEngine.entries.size))
    }
  }

  val currentHost =
    if (!isSpeedDial && currentTabUrl.isNotBlank()) {
      extractHost(currentTabUrl).takeIf { it.isNotBlank() && it != "Speed Dial" }.orEmpty()
    } else {
      ""
    }
  val trimmedInput = inputText.trim()
  val showQuickTlds =
    isFocused &&
      trimmedInput.isNotEmpty() &&
      !trimmedInput.contains(' ') &&
      !trimmedInput.startsWith("!") &&
      !trimmedInput.startsWith("/")

  Surface(
    modifier = modifier.fillMaxWidth().testTag("omnibox_quick_bar"),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
  ) {
    androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxWidth()) {
      // Context-Aware Smart Tricks Row (Paste & Go, site:host, Copy/Edit URL, Quick TLDs, Bang Hint)
      if (isFocused) {
        LazyRow(
          modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
          contentPadding = PaddingValues(horizontal = 8.dp),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (previewEngine != null) {
            item("bang_active") {
              val accent = previewEngine.badgeAccentColor(voltColors.isLightTheme)
              QuickUtilityPill(
                icon = Icons.Default.TravelExplore,
                label = "Bang → ${previewEngine.shortLabel}",
                tint = accent,
                onClick = { onSelectEngine(previewEngine) },
                tag = "omnibox_bang_preview_pill",
              )
            }
          } else if (isOmniBangActive) {
            item("omni_bang_active") {
              QuickUtilityPill(
                icon = Icons.Default.Speed,
                label = "Multi-Search → 5 Engines",
                tint = voltColors.cyanAccent,
                onClick = onLaunchOmniSearch,
                tag = "omnibox_omni_bang_pill",
              )
            }
          }

          if (hasClipboardText) {
            item("paste_and_go") {
              QuickUtilityPill(
                icon = Icons.Default.ContentPasteGo,
                label = "Paste & Go",
                tint = voltColors.cyanAccent,
                onClick = onPasteAndGo,
                tag = "omnibox_paste_go_chip",
              )
            }
          }

          if (currentHost.isNotBlank() && !trimmedInput.contains("site:", ignoreCase = true)) {
            item("site_search") {
              QuickUtilityPill(
                icon = Icons.Default.Language,
                label = "site:$currentHost",
                tint = voltColors.emeraldShield,
                onClick = {
                  val baseQuery = trimmedInput.takeUnless { it == currentTabUrl.trim() }.orEmpty()
                  val next =
                    if (baseQuery.isNotBlank()) "site:$currentHost $baseQuery"
                    else "site:$currentHost "
                  onInjectText(next)
                },
                tag = "omnibox_site_search_chip",
              )
            }
          }

          if (!isSpeedDial && currentTabUrl.isNotBlank()) {
            if (trimmedInput != currentTabUrl.trim()) {
              item("edit_url") {
                QuickUtilityPill(
                  icon = Icons.Default.Edit,
                  label = "Edit URL",
                  tint = voltColors.amberAccent,
                  onClick = { onInjectText(currentTabUrl) },
                  tag = "omnibox_edit_url_chip",
                )
              }
            } else {
              item("copy_url") {
                QuickUtilityPill(
                  icon = Icons.Default.ContentCopy,
                  label = "Copy URL",
                  tint = voltColors.cyanAccent,
                  onClick = onCopyCurrentUrl,
                  tag = "omnibox_copy_url_chip",
                )
              }
            }
          }

          if (showQuickTlds) {
            items(QUICK_TLD_CHIPS, key = { it }) { tld ->
              Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier =
                  Modifier.minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                      haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                      onInjectText(trimmedInput + tld)
                    }
                    .testTag("omnibox_tld_${tld.trimStart('.')}"),
              ) {
                Box(
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                  contentAlignment = Alignment.Center,
                ) {
                  Text(
                    text = tld,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = voltColors.cyanAccent,
                  )
                }
              }
            }
          }

          if (trimmedInput.isEmpty()) {
            item("bang_tip") {
              Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
                modifier =
                  Modifier.minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onInjectText("!w ") },
              ) {
                Text(
                  text = "Tip: !g  !w  !b  !sx  !omni",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
              }
            }
          }
        }
      }

      // Firefox-style Horizontal Swipeable Search Engine Selector Bar
      Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        LazyRow(
          state = engineListState,
          modifier = Modifier.weight(1f),
          contentPadding = PaddingValues(horizontal = 8.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          item("omni_all_5") {
            Surface(
              shape = RoundedCornerShape(100.dp),
              color =
                if (isOmniBangActive) voltColors.cyanAccent.copy(alpha = 0.22f)
                else MaterialTheme.colorScheme.surfaceContainerHigh,
              border =
                BorderStroke(
                  width = 1.dp,
                  color =
                    if (isOmniBangActive) voltColors.cyanAccent
                    else voltColors.cyanAccent.copy(alpha = 0.45f),
                ),
              modifier =
                Modifier.minimumInteractiveComponentSize()
                  .clip(RoundedCornerShape(100.dp))
                  .clickable {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onLaunchOmniSearch()
                  }
                  .testTag("omnibox_omni_search_chip"),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.Speed,
                  contentDescription = null,
                  tint = voltColors.cyanAccent,
                  modifier = Modifier.size(15.dp),
                )
                Text(
                  text = if (trimmedInput.isNotBlank()) "Search All 5" else "All 5",
                  style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                  color = voltColors.cyanAccent,
                  maxLines = 1,
                )
              }
            }
          }

          items(SearchEngine.entries, key = { it.name }) { engine ->
            val selected = engine == highlightedEngine && !isOmniBangActive
            val accent = engine.badgeAccentColor(voltColors.isLightTheme)
            Surface(
              shape = RoundedCornerShape(100.dp),
              color =
                if (selected) accent.copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.surfaceContainerHigh,
              border =
                BorderStroke(
                  width = if (selected) 1.5.dp else 1.dp,
                  color = if (selected) accent else MaterialTheme.colorScheme.outlineVariant,
                ),
              modifier =
                Modifier.minimumInteractiveComponentSize()
                  .clip(RoundedCornerShape(100.dp))
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
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Box(
                  modifier =
                    Modifier.size(22.dp)
                      .clip(CircleShape)
                      .background(accent.copy(alpha = if (selected) 0.92f else 0.22f)),
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
                        else Color(0xFF090D14)
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
                    MaterialTheme.typography.labelMedium.copy(
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

      HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
  }
}

@Composable
private fun QuickUtilityPill(
  icon: ImageVector,
  label: String,
  tint: Color,
  onClick: () -> Unit,
  tag: String,
) {
  Surface(
    shape = RoundedCornerShape(10.dp),
    color = tint.copy(alpha = 0.14f),
    border = BorderStroke(1.dp, tint.copy(alpha = 0.42f)),
    modifier =
      Modifier.minimumInteractiveComponentSize()
        .clip(RoundedCornerShape(10.dp))
        .clickable(onClick = onClick)
        .testTag(tag),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = tint,
        modifier = Modifier.size(14.dp),
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        color = tint,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

private fun Color.luminanceApprox(): Float = 0.299f * red + 0.587f * green + 0.114f * blue
