package com.example

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppThemeMode
import com.example.ui.BrowserViewModel
import com.example.ui.VoltBrowserScreen
import com.example.ui.theme.VoltBrowserTheme

class MainActivity : ComponentActivity() {

  private val viewModel: BrowserViewModel by viewModels()
  private var isInPipMode by mutableStateOf(false)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    handleIncomingIntent(intent)

    setContent {
      val settings by viewModel.settings.collectAsStateWithLifecycle()
      val isLightTheme = settings.themeMode == AppThemeMode.ZEN_LIGHT
      val view = LocalView.current
      if (!view.isInEditMode) {
        SideEffect {
          val controller = WindowCompat.getInsetsController(window, view)
          controller.isAppearanceLightStatusBars = isLightTheme
          controller.isAppearanceLightNavigationBars = isLightTheme
        }
      }
      VoltBrowserTheme(themeMode = settings.themeMode) {
        VoltBrowserScreen(
          viewModel = viewModel,
          isInPipMode = isInPipMode,
          onEnterPip = { enterVideoPipMode() },
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    handleIncomingIntent(intent)
  }

  override fun onUserLeaveHint() {
    super.onUserLeaveHint()
    val mediaState = viewModel.mediaPlayerState.value
    if (
      mediaState.isVisible &&
        !mediaState.isMinimized &&
        mediaState.hasVideoTrack &&
        mediaState.isPlaying
    ) {
      enterVideoPipMode()
    }
  }

  override fun onPictureInPictureModeChanged(
    isInPictureInPictureMode: Boolean,
    newConfig: Configuration,
  ) {
    super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
    isInPipMode = isInPictureInPictureMode
  }

  private fun enterVideoPipMode() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val params =
        PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
      runCatching { enterPictureInPictureMode(params) }
    }
  }

  private fun handleIncomingIntent(intent: Intent?) {
    if (intent?.action == Intent.ACTION_VIEW) {
      val url = intent.dataString
      if (!url.isNullOrBlank()) {
        viewModel.newTab(isIncognito = false, initialUrl = url)
      }
    }
  }
}
