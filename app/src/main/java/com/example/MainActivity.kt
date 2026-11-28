package com.example

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
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

/**
 * Single-activity Compose host configuring edge-to-edge system bars, Private-tab
 * `FLAG_SECURE` screen-capture protection, and Media3 Picture-in-Picture transitions.
 */
class MainActivity : ComponentActivity() {

  private val viewModel: BrowserViewModel by viewModels()
  private var isInPipMode by mutableStateOf(false)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    if (savedInstanceState == null) {
      handleIncomingIntent(intent)
    }

    setContent {
      val settings by viewModel.settings.collectAsStateWithLifecycle()
      val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
      val mediaState by viewModel.mediaPlayerState.collectAsStateWithLifecycle()
      val isLightTheme = settings.themeMode == AppThemeMode.ZEN_LIGHT
      val view = LocalView.current

      if (!view.isInEditMode) {
        SideEffect {
          val controller = WindowCompat.getInsetsController(window, view)
          controller.isAppearanceLightStatusBars = isLightTheme
          controller.isAppearanceLightNavigationBars = isLightTheme
        }
      }

      // Prevent screenshots and recent-apps thumbnail leaks while browsing Private tabs.
      LaunchedEffect(activeTab.isIncognito) {
        if (activeTab.isIncognito) {
          window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
          window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
      }

      val canAutoEnterPip =
        mediaState.isVisible &&
          !mediaState.isMinimized &&
          mediaState.hasVideoTrack &&
          mediaState.isPlaying
      LaunchedEffect(canAutoEnterPip) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          val params =
            PictureInPictureParams.Builder()
              .setAspectRatio(Rational(16, 9))
              .setAutoEnterEnabled(canAutoEnterPip)
              .build()
          runCatching { setPictureInPictureParams(params) }
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
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
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
      val builder = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9))
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        builder.setAutoEnterEnabled(true)
      }
      runCatching { enterPictureInPictureMode(builder.build()) }
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
