package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.LocalVoltColors
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Interactive edge-swipe gesture overlay for smooth browser Back/Forward navigation.
 */
@Composable
fun EdgeGestureOverlay(
  enabled: Boolean,
  canGoBack: Boolean,
  canGoForward: Boolean,
  onSwipeBack: () -> Unit,
  onSwipeForward: () -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable BoxScope.() -> Unit,
) {
  val voltColors = LocalVoltColors.current
  val haptics = LocalHapticFeedback.current
  val density = LocalDensity.current
  val scope = rememberCoroutineScope()

  val triggerPx = with(density) { 64.dp.toPx() }
  val maxPullPx = with(density) { 96.dp.toPx() }

  val leftPull = remember { Animatable(0f) }
  val rightPull = remember { Animatable(0f) }
  var leftThresholdCrossed by remember { mutableStateOf(false) }
  var rightThresholdCrossed by remember { mutableStateOf(false) }
  var leftTouchY by remember { mutableFloatStateOf(0f) }
  var rightTouchY by remember { mutableFloatStateOf(0f) }

  val latestOnBack by rememberUpdatedState(onSwipeBack)
  val latestOnForward by rememberUpdatedState(onSwipeForward)
  val latestCanGoForward by rememberUpdatedState(canGoForward)

  Box(modifier = modifier.fillMaxSize()) {
    content()

    if (enabled) {
      Box(
        modifier =
          Modifier.align(Alignment.CenterStart)
            .width(22.dp)
            .fillMaxHeight()
            .pointerInput(Unit) {
              detectHorizontalDragGestures(
                onDragStart = { offset ->
                  leftTouchY = offset.y
                  leftThresholdCrossed = false
                  scope.launch { leftPull.snapTo(0f) }
                },
                onHorizontalDrag = { change, dragAmount ->
                  val next = (leftPull.value + dragAmount).coerceIn(0f, maxPullPx)
                  if (dragAmount > 0f || leftPull.value > 0f) {
                    change.consume()
                  }
                  scope.launch { leftPull.snapTo(next) }
                  val crossed = next >= triggerPx
                  if (crossed && !leftThresholdCrossed) {
                    leftThresholdCrossed = true
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                  } else if (!crossed && leftThresholdCrossed) {
                    leftThresholdCrossed = false
                  }
                },
                onDragEnd = {
                  val shouldTrigger = leftPull.value >= triggerPx
                  scope.launch {
                    leftPull.animateTo(
                      targetValue = 0f,
                      animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    )
                  }
                  if (shouldTrigger) {
                    latestOnBack()
                  }
                },
                onDragCancel = {
                  scope.launch {
                    leftPull.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                  }
                },
              )
            }
            .testTag("edge_back_gesture_zone")
      )

      Box(
        modifier =
          Modifier.align(Alignment.CenterEnd)
            .width(22.dp)
            .fillMaxHeight()
            .pointerInput(Unit) {
              detectHorizontalDragGestures(
                onDragStart = { offset ->
                  rightTouchY = offset.y
                  rightThresholdCrossed = false
                  scope.launch { rightPull.snapTo(0f) }
                },
                onHorizontalDrag = { change, dragAmount ->
                  if (!latestCanGoForward) return@detectHorizontalDragGestures
                  val next = (rightPull.value - dragAmount).coerceIn(0f, maxPullPx)
                  if (dragAmount < 0f || rightPull.value > 0f) {
                    change.consume()
                  }
                  scope.launch { rightPull.snapTo(next) }
                  val crossed = next >= triggerPx
                  if (crossed && !rightThresholdCrossed) {
                    rightThresholdCrossed = true
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                  } else if (!crossed && rightThresholdCrossed) {
                    rightThresholdCrossed = false
                  }
                },
                onDragEnd = {
                  val shouldTrigger = latestCanGoForward && rightPull.value >= triggerPx
                  scope.launch {
                    rightPull.animateTo(
                      targetValue = 0f,
                      animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    )
                  }
                  if (shouldTrigger) {
                    latestOnForward()
                  }
                },
                onDragCancel = {
                  scope.launch {
                    rightPull.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                  }
                },
              )
            }
            .testTag("edge_forward_gesture_zone")
      )

      if (leftPull.value > 4f) {
        val progress = (leftPull.value / triggerPx).coerceIn(0f, 1f)
        val armed = leftPull.value >= triggerPx
        val pillTint = if (armed) voltColors.emeraldShield else voltColors.brandAccent
        Box(
          modifier =
            Modifier.align(Alignment.CenterStart)
              .fillMaxHeight()
              .width(12.dp)
              .background(
                Brush.horizontalGradient(
                  colors = listOf(pillTint.copy(alpha = 0.25f * progress), Color.Transparent)
                )
              )
        )
        Surface(
          shape = CircleShape,
          color =
            if (armed) pillTint
            else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
          border = BorderStroke(1.5.dp, pillTint),
          shadowElevation = if (armed) 8.dp else 3.dp,
          modifier =
            Modifier.align(Alignment.CenterStart)
              .offset { IntOffset((leftPull.value * 0.65f - 18f).roundToInt(), 0) }
              .scale(0.82f + 0.24f * progress)
              .alpha(progress.coerceAtLeast(0.35f))
              .size(44.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = if (canGoBack) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Home,
              contentDescription = stringResource(R.string.cd_gesture_back),
              tint = if (armed) Color.White else pillTint,
              modifier = Modifier.size(20.dp),
            )
          }
        }
      }

      if (rightPull.value > 4f && canGoForward) {
        val progress = (rightPull.value / triggerPx).coerceIn(0f, 1f)
        val armed = rightPull.value >= triggerPx
        val pillTint = if (armed) voltColors.emeraldShield else voltColors.brandAccent
        Box(
          modifier =
            Modifier.align(Alignment.CenterEnd)
              .fillMaxHeight()
              .width(12.dp)
              .background(
                Brush.horizontalGradient(
                  colors = listOf(Color.Transparent, pillTint.copy(alpha = 0.25f * progress))
                )
              )
        )
        Surface(
          shape = CircleShape,
          color =
            if (armed) pillTint
            else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
          border = BorderStroke(1.5.dp, pillTint),
          shadowElevation = if (armed) 8.dp else 3.dp,
          modifier =
            Modifier.align(Alignment.CenterEnd)
              .offset { IntOffset((-rightPull.value * 0.65f + 18f).roundToInt(), 0) }
              .scale(0.82f + 0.24f * progress)
              .alpha(progress.coerceAtLeast(0.35f))
              .size(44.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowForward,
              contentDescription = stringResource(R.string.cd_gesture_forward),
              tint = if (armed) Color.White else pillTint,
              modifier = Modifier.size(20.dp),
            )
          }
        }
      }
    }
  }
}
