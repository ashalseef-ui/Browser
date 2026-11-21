package com.example.ui.screens

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.media.MediaPlayerUiState
import com.example.ui.components.formatDurationMs
import com.example.ui.theme.LocalVoltColors

private val speedOptions = listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f)

@OptIn(UnstableApi::class)
@Composable
fun MediaPlayerOverlay(
  state: MediaPlayerUiState,
  controller: MediaController?,
  isInPipMode: Boolean,
  onTogglePlayPause: () -> Unit,
  onSeekTo: (Long) -> Unit,
  onSeekBy: (Long) -> Unit,
  onSelectSpeed: (Float) -> Unit,
  onEnterPip: () -> Unit,
  onMinimize: () -> Unit,
  onDownload: () -> Unit,
  onOpenExternal: () -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val voltColors = LocalVoltColors.current
  var showControls by remember { mutableStateOf(true) }
  var isControlsLocked by remember { mutableStateOf(false) }
  var isMuted by remember { mutableStateOf(false) }
  var isLooping by remember { mutableStateOf(false) }
  var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

  Box(
    modifier =
      modifier
        .fillMaxSize()
        .background(Color.Black)
        .clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = null,
          enabled = !isInPipMode,
        ) {
          showControls = !showControls
        }
  ) {
    if (controller != null && state.hasVideoTrack) {
      var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
      DisposableEffect(controller) {
        onDispose { playerViewRef?.player = null }
      }
      AndroidView(
        factory = { ctx ->
          PlayerView(ctx).apply {
            layoutParams =
              ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
              )
            useController = false
            this.resizeMode = resizeMode
            player = controller
            keepScreenOn = true
            playerViewRef = this
          }
        },
        update = { view ->
          if (view.player !== controller) view.player = controller
          view.resizeMode = resizeMode
        },
        modifier = Modifier.fillMaxSize(),
      )
    } else {
      Box(
        modifier =
          Modifier.fillMaxSize()
            .background(
              Brush.radialGradient(
                colors =
                  listOf(
                    voltColors.cyanAccent.copy(alpha = 0.22f),
                    Color(0xFF090D14),
                  )
              )
            ),
        contentAlignment = Alignment.Center,
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          Surface(
            shape = CircleShape,
            color = voltColors.cyanAccent.copy(alpha = 0.16f),
            modifier = Modifier.size(112.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Default.GraphicEq,
                contentDescription = null,
                tint = voltColors.cyanAccent,
                modifier = Modifier.size(56.dp),
              )
            }
          }
          Text(
            text = state.activeMedia?.title ?: "Audio Stream",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 32.dp),
          )
        }
      }
    }

    if (state.isBuffering && !isInPipMode) {
      CircularProgressIndicator(
        color = voltColors.cyanAccent,
        modifier = Modifier.align(Alignment.Center),
      )
    }

    // Lock-only floating unlock pill when controls are locked
    if (isControlsLocked && showControls && !isInPipMode) {
      Surface(
        shape = CircleShape,
        color = Color(0xAA090D14),
        modifier =
          Modifier.align(Alignment.BottomStart)
            .navigationBarsPadding()
            .padding(20.dp)
            .clip(CircleShape)
            .clickable { isControlsLocked = false },
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(Icons.Default.Lock, contentDescription = "Unlock player", tint = voltColors.amberAccent, modifier = Modifier.size(18.dp))
          Text("Tap to Unlock", style = MaterialTheme.typography.labelMedium, color = Color.White)
        }
      }
    }

    AnimatedVisibility(
      visible = showControls && !isControlsLocked && !isInPipMode,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier.fillMaxSize(),
    ) {
      Box(
        modifier =
          Modifier.fillMaxSize()
            .background(Color(0x77000000))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
      ) {
        // Top HUD Bar
        Row(
          modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
          ) {
            IconButton(
              onClick = onMinimize,
              modifier = Modifier.testTag("player_minimize_button"),
            ) {
              Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(R.string.cd_background_audio),
                tint = Color.White,
              )
            }
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = state.activeMedia?.title ?: "Volt Cinema",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = state.activeMedia?.sourcePageUrl.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFCBD5E1),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }

          Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            IconButton(
              onClick = {
                isMuted = !isMuted
                controller?.volume = if (isMuted) 0f else 1f
              },
              modifier = Modifier.testTag("player_mute_button"),
            ) {
              Icon(
                imageVector =
                  if (isMuted) Icons.AutoMirrored.Filled.VolumeOff
                  else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = if (isMuted) "Unmute" else "Mute",
                tint = if (isMuted) voltColors.amberAccent else Color.White,
              )
            }
            IconButton(
              onClick = {
                isLooping = !isLooping
                controller?.repeatMode =
                  if (isLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
              },
              modifier = Modifier.testTag("player_loop_button"),
            ) {
              Icon(
                imageVector = if (isLooping) Icons.Default.RepeatOne else Icons.Default.Repeat,
                contentDescription = "Toggle loop",
                tint = if (isLooping) voltColors.cyanAccent else Color.White,
              )
            }
            IconButton(
              onClick = onOpenExternal,
              modifier = Modifier.testTag("player_external_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "Open in external player",
                tint = Color.White,
              )
            }
            if (state.activeMedia?.url?.startsWith("http") == true) {
              IconButton(
                onClick = onDownload,
                modifier = Modifier.testTag("player_download_button"),
              ) {
                Icon(
                  imageVector = Icons.Default.Download,
                  contentDescription = stringResource(R.string.cd_download_media),
                  tint = Color.White,
                )
              }
            }
            if (state.hasVideoTrack) {
              IconButton(
                onClick = onEnterPip,
                modifier = Modifier.testTag("player_pip_button"),
              ) {
                Icon(
                  imageVector = Icons.Default.PictureInPictureAlt,
                  contentDescription = stringResource(R.string.cd_pip),
                  tint = voltColors.cyanAccent,
                )
              }
            }
            IconButton(
              onClick = onClose,
              modifier = Modifier.testTag("player_close_button"),
            ) {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.cd_close_player),
                tint = Color.White,
              )
            }
          }
        }

        // Center Transport Controls
        Row(
          modifier = Modifier.align(Alignment.Center),
          horizontalArrangement = Arrangement.spacedBy(28.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          IconButton(
            onClick = { onSeekBy(-10_000L) },
            modifier = Modifier.size(52.dp).testTag("player_rewind_button"),
          ) {
            Icon(
              imageVector = Icons.Default.Replay10,
              contentDescription = stringResource(R.string.cd_seek_back),
              tint = Color.White,
              modifier = Modifier.size(34.dp),
            )
          }

          Surface(
            shape = CircleShape,
            color = voltColors.cyanAccent,
            modifier =
              Modifier.size(68.dp)
                .clip(CircleShape)
                .clickable(onClick = onTogglePlayPause)
                .testTag("player_play_pause_button"),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = stringResource(R.string.cd_play_pause),
                tint = Color(0xFF002026),
                modifier = Modifier.size(38.dp),
              )
            }
          }

          IconButton(
            onClick = { onSeekBy(10_000L) },
            modifier = Modifier.size(52.dp).testTag("player_forward_button"),
          ) {
            Icon(
              imageVector = Icons.Default.Forward10,
              contentDescription = stringResource(R.string.cd_seek_forward),
              tint = Color.White,
              modifier = Modifier.size(34.dp),
            )
          }
        }

        // Bottom Scrubber & Speed Controls
        Column(
          modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          state.errorMessage?.let { error ->
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = MaterialTheme.colorScheme.error.copy(alpha = 0.88f),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Text(
                  text = error,
                  style = MaterialTheme.typography.bodySmall,
                  color = Color.White,
                  modifier = Modifier.weight(1f),
                )
                Surface(
                  shape = RoundedCornerShape(8.dp),
                  color = Color.White.copy(alpha = 0.2f),
                  modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onOpenExternal),
                ) {
                  Text(
                    text = "External Player",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                  )
                }
              }
            }
          }

          val duration = state.durationMs.coerceAtLeast(1L)
          val current = state.positionMs.coerceIn(0L, duration)
          Slider(
            value = current.toFloat(),
            onValueChange = { onSeekTo(it.toLong()) },
            valueRange = 0f..duration.toFloat(),
            colors =
              SliderDefaults.colors(
                thumbColor = voltColors.cyanAccent,
                activeTrackColor = voltColors.cyanAccent,
                inactiveTrackColor = Color.White.copy(alpha = 0.28f),
              ),
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "${formatDurationMs(state.positionMs)} / ${formatDurationMs(state.durationMs)}",
              style = MaterialTheme.typography.labelMedium,
              color = Color.White,
            )

            Row(
              horizontalArrangement = Arrangement.spacedBy(5.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              IconButton(
                onClick = { isControlsLocked = true },
                modifier = Modifier.size(34.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.LockOpen,
                  contentDescription = "Lock controls",
                  tint = Color.White,
                  modifier = Modifier.size(18.dp),
                )
              }

              speedOptions.forEach { spd ->
                val selected = state.playbackSpeed == spd
                Surface(
                  shape = RoundedCornerShape(8.dp),
                  color =
                    if (selected) voltColors.cyanAccent
                    else Color.White.copy(alpha = 0.14f),
                  modifier =
                    Modifier.clip(RoundedCornerShape(8.dp))
                      .clickable { onSelectSpeed(spd) },
                ) {
                  Text(
                    text = "${spd}x",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) Color(0xFF002026) else Color.White,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                  )
                }
              }

              if (state.hasVideoTrack) {
                IconButton(
                  onClick = {
                    resizeMode =
                      when (resizeMode) {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT ->
                          AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM ->
                          AspectRatioFrameLayout.RESIZE_MODE_FILL
                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                      }
                  },
                  modifier = Modifier.size(34.dp),
                ) {
                  Icon(
                    imageVector = Icons.Default.AspectRatio,
                    contentDescription = "Cycle video aspect mode",
                    tint = Color.White,
                    modifier = Modifier.size(19.dp),
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

@Composable
fun MiniMediaPlayerBar(
  state: MediaPlayerUiState,
  onExpand: () -> Unit,
  onTogglePlayPause: () -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val voltColors = LocalVoltColors.current

  Surface(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 4.dp)
        .clip(RoundedCornerShape(16.dp))
        .clickable(onClick = onExpand)
        .testTag("mini_media_player_bar"),
    shape = RoundedCornerShape(16.dp),
    color = voltColors.cardSurface,
    border = androidx.compose.foundation.BorderStroke(1.dp, voltColors.cyanAccent.copy(alpha = 0.45f)),
    tonalElevation = 8.dp,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Box(
        modifier =
          Modifier.size(36.dp)
            .clip(CircleShape)
            .background(voltColors.cyanAccent.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Default.GraphicEq,
          contentDescription = null,
          tint = voltColors.cyanAccent,
          modifier = Modifier.size(20.dp),
        )
      }

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = state.activeMedia?.title ?: "Playing Media",
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = "${formatDurationMs(state.positionMs)} • Tap to expand",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
        )
      }

      IconButton(
        onClick = onTogglePlayPause,
        modifier = Modifier.testTag("mini_player_play_pause"),
      ) {
        Icon(
          imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
          contentDescription = stringResource(R.string.cd_play_pause),
          tint = voltColors.cyanAccent,
        )
      }

      IconButton(
        onClick = onClose,
        modifier = Modifier.testTag("mini_player_close"),
      ) {
        Icon(
          imageVector = Icons.Default.Close,
          contentDescription = stringResource(R.string.cd_close_player),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
