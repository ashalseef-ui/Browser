package com.example.media

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.data.DetectedMediaItem
import com.example.data.MediaKind
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class MediaPlayerUiState(
  val isVisible: Boolean = false,
  val isMinimized: Boolean = false,
  val activeMedia: DetectedMediaItem? = null,
  val isPlaying: Boolean = false,
  val isBuffering: Boolean = false,
  val positionMs: Long = 0L,
  val durationMs: Long = 0L,
  val bufferedPositionMs: Long = 0L,
  val playbackSpeed: Float = 1.0f,
  val isMuted: Boolean = false,
  val isLooping: Boolean = false,
  val resizeMode: Int = 0, // AspectRatioFrameLayout.RESIZE_MODE_FIT
  val hasVideoTrack: Boolean = true,
  val errorMessage: String? = null,
)

class MediaPlaybackManager(
  private val context: Context,
  private val scope: CoroutineScope,
) {
  private val _uiState = MutableStateFlow(MediaPlayerUiState())
  val uiState: StateFlow<MediaPlayerUiState> = _uiState.asStateFlow()

  private var controllerFuture: ListenableFuture<MediaController>? = null
  private val _controllerFlow = MutableStateFlow<MediaController?>(null)
  val controllerFlow: StateFlow<MediaController?> = _controllerFlow.asStateFlow()

  private var tickerJob: Job? = null
  private var pendingMedia: DetectedMediaItem? = null

  private val playerListener =
    object : Player.Listener {
      override fun onIsPlayingChanged(isPlaying: Boolean) {
        _uiState.update { it.copy(isPlaying = isPlaying) }
      }

      override fun onPlaybackStateChanged(playbackState: Int) {
        val controller = _controllerFlow.value
        _uiState.update {
          it.copy(
            isBuffering = playbackState == Player.STATE_BUFFERING,
            durationMs = controller?.duration?.coerceAtLeast(0L) ?: it.durationMs,
          )
        }
      }

      override fun onVideoSizeChanged(videoSize: VideoSize) {
        val hasVideo = videoSize.width > 0 && videoSize.height > 0
        _uiState.update { it.copy(hasVideoTrack = hasVideo) }
      }

      override fun onPlayerError(error: PlaybackException) {
        _uiState.update {
          it.copy(
            isBuffering = false,
            isPlaying = false,
            errorMessage = error.localizedMessage ?: "Unable to play this media stream",
          )
        }
      }
    }

  fun connect() {
    if (controllerFuture != null) return
    val token = SessionToken(context, ComponentName(context, VoltMediaService::class.java))
    val future = MediaController.Builder(context, token).buildAsync()
    controllerFuture = future
    future.addListener(
      {
        runCatching {
          val controller = future.get()
          controller.addListener(playerListener)
          val snapshot = _uiState.value
          controller.volume = if (snapshot.isMuted) 0f else 1f
          controller.repeatMode = if (snapshot.isLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
          _controllerFlow.value = controller
          startPositionTicker()
          pendingMedia?.let { queued ->
            pendingMedia = null
            playMediaInternal(controller, queued)
          }
        }
      },
      ContextCompat.getMainExecutor(context),
    )
  }

  fun playMedia(item: DetectedMediaItem) {
    _uiState.update {
      it.copy(
        isVisible = true,
        isMinimized = false,
        activeMedia = item,
        hasVideoTrack = item.kind != MediaKind.AUDIO,
        errorMessage = null,
        positionMs = 0L,
        durationMs = (item.durationSeconds ?: 0L) * 1000L,
      )
    }
    val controller = _controllerFlow.value
    if (controller == null) {
      pendingMedia = item
      connect()
    } else {
      playMediaInternal(controller, item)
    }
  }

  private fun playMediaInternal(controller: MediaController, item: DetectedMediaItem) {
    val metadata =
      MediaMetadata.Builder()
        .setTitle(item.title)
        .setSubtitle(item.sourcePageUrl)
        .apply { item.posterUrl?.let { setArtworkUri(Uri.parse(it)) } }
        .build()

    val mediaItem =
      MediaItem.Builder()
        .setUri(item.url)
        .setMediaMetadata(metadata)
        .apply {
          if (item.kind == MediaKind.STREAM_HLS) {
            setMimeType("application/x-mpegURL")
          } else if (item.kind == MediaKind.STREAM_DASH) {
            setMimeType("application/dash+xml")
          }
        }
        .build()

    val snapshot = _uiState.value
    controller.setMediaItem(mediaItem)
    controller.setPlaybackSpeed(snapshot.playbackSpeed)
    controller.volume = if (snapshot.isMuted) 0f else 1f
    controller.repeatMode = if (snapshot.isLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    controller.prepare()
    controller.play()
  }

  fun togglePlayPause() {
    val controller = _controllerFlow.value ?: return
    if (controller.isPlaying) {
      controller.pause()
    } else {
      if (controller.playbackState == Player.STATE_ENDED) {
        controller.seekTo(0L)
      }
      controller.play()
    }
  }

  fun seekTo(positionMs: Long) {
    val controller = _controllerFlow.value ?: return
    controller.seekTo(positionMs.coerceAtLeast(0L))
    _uiState.update { it.copy(positionMs = positionMs.coerceAtLeast(0L)) }
  }

  fun seekBy(deltaMs: Long) {
    val controller = _controllerFlow.value ?: return
    val duration = controller.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
    val target = (controller.currentPosition + deltaMs).coerceIn(0L, duration)
    controller.seekTo(target)
    _uiState.update { it.copy(positionMs = target) }
  }

  fun setPlaybackSpeed(speed: Float) {
    _controllerFlow.value?.setPlaybackSpeed(speed)
    _uiState.update { it.copy(playbackSpeed = speed) }
  }

  fun toggleMute() {
    val nextMuted = !_uiState.value.isMuted
    _controllerFlow.value?.volume = if (nextMuted) 0f else 1f
    _uiState.update { it.copy(isMuted = nextMuted) }
  }

  fun toggleLoop() {
    val nextLooping = !_uiState.value.isLooping
    _controllerFlow.value?.repeatMode =
      if (nextLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    _uiState.update { it.copy(isLooping = nextLooping) }
  }

  fun cycleResizeMode() {
    _uiState.update {
      val next =
        when (it.resizeMode) {
          0 -> 4 // RESIZE_MODE_ZOOM
          4 -> 3 // RESIZE_MODE_FILL
          else -> 0 // RESIZE_MODE_FIT
        }
      it.copy(resizeMode = next)
    }
  }

  fun minimizeToMiniPlayer() {
    _uiState.update { it.copy(isVisible = true, isMinimized = true) }
  }

  fun expandFromMiniPlayer() {
    _uiState.update { it.copy(isVisible = true, isMinimized = false) }
  }

  fun closePlayer() {
    _controllerFlow.value?.apply {
      pause()
      stop()
      clearMediaItems()
    }
    _uiState.value = MediaPlayerUiState()
  }

  @Suppress("OPT_IN_USAGE")
  private fun startPositionTicker() {
    tickerJob?.cancel()
    tickerJob =
      scope.launch(Dispatchers.Main) {
        _uiState
          .map { it.isVisible && (it.isPlaying || it.isBuffering) }
          .distinctUntilChanged()
          .collectLatest { active ->
            val controller = _controllerFlow.value
            if (controller != null && _uiState.value.isVisible) {
              _uiState.update {
                it.copy(
                  positionMs = controller.currentPosition.coerceAtLeast(0L),
                  durationMs = controller.duration.coerceAtLeast(0L),
                  bufferedPositionMs = controller.bufferedPosition.coerceAtLeast(0L),
                  isPlaying = controller.isPlaying,
                )
              }
            }
            while (active && isActive) {
              delay(400L)
              val ctrl = _controllerFlow.value
              if (ctrl != null && _uiState.value.isVisible) {
                _uiState.update {
                  it.copy(
                    positionMs = ctrl.currentPosition.coerceAtLeast(0L),
                    durationMs = ctrl.duration.coerceAtLeast(0L),
                    bufferedPositionMs = ctrl.bufferedPosition.coerceAtLeast(0L),
                    isPlaying = ctrl.isPlaying,
                  )
                }
              }
            }
          }
      }
  }

  fun release() {
    tickerJob?.cancel()
    _controllerFlow.value?.removeListener(playerListener)
    controllerFuture?.let { MediaController.releaseFuture(it) }
    controllerFuture = null
    _controllerFlow.value = null
  }
}
