package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.data.SocialAppShortcut
import com.example.ui.theme.LocalVoltColors
import kotlin.math.roundToInt

@Composable
fun SocialSidebarOverlay(
  isOpen: Boolean,
  socialApps: List<SocialAppShortcut>,
  hasSystemAutofill: Boolean,
  onToggleSidebar: () -> Unit,
  onDismiss: () -> Unit,
  onOpenInBrowser: (SocialAppShortcut) -> Unit,
  onLaunchInstalledApp: (SocialAppShortcut) -> Unit,
  onTriggerAutofill: () -> Unit,
  onOpenAutofillSettings: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val voltColors = LocalVoltColors.current
  val density = LocalDensity.current

  // Movable state: user can drag vertically & dock to Left or Right edge
  var isDockedRight by remember { mutableStateOf(false) }
  var verticalFraction by remember { mutableFloatStateOf(0.42f) }
  var isDragging by remember { mutableStateOf(false) }
  var dragOffsetX by remember { mutableFloatStateOf(0f) }
  var dragOffsetY by remember { mutableFloatStateOf(0f) }
  var handleWidthPx by remember { mutableStateOf(with(density) { 42.dp.roundToPx() }) }
  var handleHeightPx by remember { mutableStateOf(with(density) { 64.dp.roundToPx() }) }

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val maxWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
    val maxHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
    val minY = with(density) { 12.dp.toPx() }
    val maxY = (maxHeightPx - handleHeightPx - minY).coerceAtLeast(minY)

    val dockedX = if (isDockedRight) (maxWidthPx - handleWidthPx).coerceAtLeast(0f) else 0f
    val dockedY = (verticalFraction * maxY).coerceIn(minY, maxY)

    val targetOffset =
      if (isDragging) {
        IntOffset(
          x = dragOffsetX.coerceIn(0f, (maxWidthPx - handleWidthPx).coerceAtLeast(0f)).roundToInt(),
          y = dragOffsetY.coerceIn(minY, maxY).roundToInt(),
        )
      } else {
        IntOffset(dockedX.roundToInt(), dockedY.roundToInt())
      }

    val animatedOffset by animateIntOffsetAsState(targetValue = targetOffset, label = "social_handle_offset")

    // Movable Floating Handle Tab when sidebar is closed
    if (!isOpen) {
      val handleShape =
        if (isDockedRight) RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
        else RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)

      Surface(
        shape = handleShape,
        color = voltColors.cardSurface.copy(alpha = 0.95f),
        border = BorderStroke(1.dp, voltColors.cyanAccent.copy(alpha = 0.50f)),
        shadowElevation = 6.dp,
        modifier =
          Modifier.offset { animatedOffset }
            .onSizeChanged {
              handleWidthPx = it.width
              handleHeightPx = it.height
            }
            .minimumInteractiveComponentSize()
            .clip(handleShape)
            .pointerInput(maxWidthPx, maxHeightPx) {
              detectDragGestures(
                onDragStart = {
                  isDragging = true
                  dragOffsetX = dockedX
                  dragOffsetY = dockedY
                },
                onDrag = { change, dragAmount ->
                  change.consume()
                  dragOffsetX = (dragOffsetX + dragAmount.x).coerceIn(0f, (maxWidthPx - handleWidthPx).coerceAtLeast(0f))
                  dragOffsetY = (dragOffsetY + dragAmount.y).coerceIn(minY, maxY)
                },
                onDragEnd = {
                  val centerX = dragOffsetX + handleWidthPx / 2f
                  isDockedRight = centerX > maxWidthPx / 2f
                  verticalFraction = (dragOffsetY / maxY.coerceAtLeast(1f)).coerceIn(0.05f, 0.95f)
                  isDragging = false
                },
                onDragCancel = {
                  isDragging = false
                },
              )
            }
            .clickable(onClick = onToggleSidebar)
            .testTag("social_sidebar_toggle"),
      ) {
        Column(
          modifier = Modifier.padding(horizontal = 5.dp, vertical = 10.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
          Icon(
            imageVector = Icons.Default.DragIndicator,
            contentDescription = "Drag to move sidebar handle",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(12.dp),
          )
          Icon(
            imageVector = Icons.Default.Forum,
            contentDescription = "Open social media sidebar",
            tint = voltColors.cyanAccent,
            modifier = Modifier.size(16.dp),
          )
          Icon(
            imageVector =
              if (isDockedRight) Icons.AutoMirrored.Filled.KeyboardArrowLeft
              else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
          )
        }
      }
    }

    // Dim backdrop when sidebar is open
    AnimatedVisibility(
      visible = isOpen,
      enter = fadeIn(),
      exit = fadeOut(),
    ) {
      Box(
        modifier =
          Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.44f))
            .clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = null,
              onClick = onDismiss,
            )
      )
    }

    // Slide-in Movable Social Sidebar Panel (Docks Left OR Right matching handle)
    val drawerAlignment = if (isDockedRight) Alignment.CenterEnd else Alignment.CenterStart
    val drawerShape =
      if (isDockedRight) RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp)
      else RoundedCornerShape(topEnd = 22.dp, bottomEnd = 22.dp)

    AnimatedVisibility(
      visible = isOpen,
      enter = slideInHorizontally(initialOffsetX = { if (isDockedRight) it else -it }),
      exit = slideOutHorizontally(targetOffsetX = { if (isDockedRight) it else -it }),
      modifier = Modifier.align(drawerAlignment),
    ) {
      Surface(
        shape = drawerShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, voltColors.cardBorder),
        shadowElevation = 10.dp,
        modifier = Modifier.width(260.dp).fillMaxHeight().testTag("social_sidebar_panel"),
      ) {
        Column(
          modifier =
            Modifier.fillMaxSize()
              .verticalScroll(rememberScrollState())
              .padding(horizontal = 14.dp, vertical = 14.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          // Header with Dock-Side Swap & Close
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.weight(1f),
            ) {
              Box(
                modifier =
                  Modifier.size(32.dp)
                    .clip(CircleShape)
                    .background(voltColors.cyanAccent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Default.Forum,
                  contentDescription = null,
                  tint = voltColors.cyanAccent,
                  modifier = Modifier.size(17.dp),
                )
              }
              Column {
                Text(
                  text = "Social Apps",
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                  text = if (isDockedRight) "Docked Right • Drag tab" else "Docked Left • Drag tab",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
              IconButton(
                onClick = { isDockedRight = !isDockedRight },
                modifier = Modifier.size(36.dp).testTag("social_sidebar_swap_side_button"),
              ) {
                Icon(
                  imageVector = Icons.Default.SwapHoriz,
                  contentDescription = "Move sidebar to opposite side",
                  tint = voltColors.cyanAccent,
                  modifier = Modifier.size(19.dp),
                )
              }
              IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(36.dp).testTag("social_sidebar_close_button"),
              ) {
                Icon(
                  imageVector = Icons.Default.Close,
                  contentDescription = "Close social sidebar",
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(19.dp),
                )
              }
            }
          }

          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

          // Social App Cards (Instagram, Facebook)
          socialApps.forEach { app ->
            val isInstagram = app.id == "instagram"
            val accentBrush =
              if (isInstagram) {
                Brush.linearGradient(listOf(Color(0xFFF58529), Color(0xFFDD2A7B), Color(0xFF8134AF)))
              } else {
                Brush.linearGradient(listOf(Color(0xFF1877F2), Color(0xFF0B5ED7)))
              }
            val accentColor = if (isInstagram) Color(0xFFDD2A7B) else Color(0xFF1877F2)

            Surface(
              shape = RoundedCornerShape(16.dp),
              color = voltColors.cardSurface,
              border = BorderStroke(1.dp, voltColors.cardBorder),
              modifier = Modifier.fillMaxWidth().testTag("social_card_${app.id}"),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  Box(
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(accentBrush),
                    contentAlignment = Alignment.Center,
                  ) {
                    Icon(
                      imageVector = if (isInstagram) Icons.Default.CameraAlt else Icons.Default.Public,
                      contentDescription = null,
                      tint = Color.White,
                      modifier = Modifier.size(19.dp),
                    )
                  }
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = app.name,
                      style = MaterialTheme.typography.titleSmall,
                      color = MaterialTheme.colorScheme.onSurface,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                      text = if (app.isInstalled) "Installed on device" else "Web app ready",
                      style = MaterialTheme.typography.labelSmall,
                      color =
                        if (app.isInstalled) voltColors.emeraldShield
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }

                Button(
                  onClick = { onOpenInBrowser(app) },
                  modifier = Modifier.fillMaxWidth().height(42.dp).testTag("social_open_${app.id}_button"),
                  contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                  colors =
                    ButtonDefaults.buttonColors(
                      containerColor = accentColor,
                      contentColor = Color.White,
                    ),
                ) {
                  Text("Open ${app.name} Web", style = MaterialTheme.typography.labelMedium)
                }

                if (app.isInstalled) {
                  OutlinedButton(
                    onClick = { onLaunchInstalledApp(app) },
                    modifier =
                      Modifier.fillMaxWidth().height(40.dp).testTag("social_app_${app.id}_button"),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                  ) {
                    Icon(
                      imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                      contentDescription = null,
                      modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open Installed App", style = MaterialTheme.typography.labelSmall)
                  }
                }
              }
            }
          }

          // Device Login / Password Manager Autofill Box
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = voltColors.cardSurface,
            border = BorderStroke(1.dp, voltColors.cyanAccent.copy(alpha = 0.38f)),
            modifier = Modifier.fillMaxWidth().testTag("social_autofill_card"),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.Key,
                  contentDescription = null,
                  tint = voltColors.cyanAccent,
                  modifier = Modifier.size(18.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "Device Login Autofill",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                  Text(
                    text =
                      if (hasSystemAutofill) "Password manager active"
                      else "Enable system password manager",
                    style = MaterialTheme.typography.labelSmall,
                    color =
                      if (hasSystemAutofill) voltColors.emeraldShield
                      else voltColors.amberAccent,
                  )
                }
              }

              Text(
                text =
                  "Android keeps app logins private inside each app. Use Autofill on a social login page to fill saved credentials from your device Password Manager.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )

              Button(
                onClick = onTriggerAutofill,
                modifier = Modifier.fillMaxWidth().height(44.dp).testTag("social_autofill_button"),
                colors =
                  ButtonDefaults.buttonColors(
                    containerColor = voltColors.cyanAccent,
                    contentColor = Color(0xFF002026),
                  ),
              ) {
                Icon(
                  imageVector = Icons.Default.Password,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Autofill Login", style = MaterialTheme.typography.labelMedium)
              }

              OutlinedButton(
                onClick = onOpenAutofillSettings,
                modifier = Modifier.fillMaxWidth().height(40.dp).testTag("social_autofill_settings_button"),
              ) {
                Icon(
                  imageVector = Icons.Default.Settings,
                  contentDescription = null,
                  modifier = Modifier.size(15.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Device Autofill Settings", style = MaterialTheme.typography.labelSmall)
              }
            }
          }
        }
      }
    }
  }
}
