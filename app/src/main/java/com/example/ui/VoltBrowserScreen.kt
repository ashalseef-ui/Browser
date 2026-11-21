package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.components.OmniboxDock
import com.example.ui.screens.AddSpeedDialDialog
import com.example.ui.screens.ExitConfirmDialog
import com.example.ui.screens.FreePublicVpnSheet
import com.example.ui.screens.LibrarySettingsSheet
import com.example.ui.screens.MediaHubSheet
import com.example.ui.screens.MediaPlayerOverlay
import com.example.ui.screens.MiniMediaPlayerBar
import com.example.ui.screens.SecurityTelemetrySheet
import com.example.ui.screens.SpeedDialHome
import com.example.ui.screens.TabSwitcherOverlay
import com.example.ui.theme.LocalVoltColors

@Composable
fun VoltBrowserScreen(
  viewModel: BrowserViewModel,
  isInPipMode: Boolean,
  onEnterPip: () -> Unit,
) {
  val context = LocalContext.current
  val voltColors = LocalVoltColors.current

  val tabs by viewModel.tabs.collectAsStateWithLifecycle()
  val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
  val settings by viewModel.settings.collectAsStateWithLifecycle()
  val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
  val speedDialItems by viewModel.speedDialItems.collectAsStateWithLifecycle()
  val downloads by viewModel.downloads.collectAsStateWithLifecycle()
  val isBookmarked by viewModel.isActivePageBookmarked.collectAsStateWithLifecycle()
  val activeSheet by viewModel.activeSheet.collectAsStateWithLifecycle()
  val findInPageVisible by viewModel.findInPageVisible.collectAsStateWithLifecycle()
  val fullscreenCustomView by viewModel.fullscreenCustomView.collectAsStateWithLifecycle()
  val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()
  val mediaState by viewModel.mediaPlayerState.collectAsStateWithLifecycle()
  val mediaController by viewModel.mediaManager.controllerFlow.collectAsStateWithLifecycle()
  val vpnState by viewModel.vpnState.collectAsStateWithLifecycle()

  val snackbarHostState = remember { SnackbarHostState() }
  var isErasingOnExit by remember { mutableStateOf(false) }

  val vpnConsentLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      if (result.resultCode == Activity.RESULT_OK) {
        viewModel.startDnsVpnService()
      } else {
        viewModel.showMessage("VPN permission is required to enable the Encrypted DNS tunnel")
      }
    }

  // Contextual notification permission request when starting media playback on Android 13+
  var askedNotificationPermission by remember { mutableStateOf(false) }
  val notificationPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

  val hasActiveDownload = downloads.any { it.status == "DOWNLOADING" }
  LaunchedEffect(mediaState.isVisible, hasActiveDownload) {
    if (
      (mediaState.isVisible || hasActiveDownload) &&
        !askedNotificationPermission &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    ) {
      val granted =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
          PackageManager.PERMISSION_GRANTED
      if (!granted) {
        askedNotificationPermission = true
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
      }
    }
  }

  LaunchedEffect(toastMessage) {
    val msg = toastMessage ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(msg)
    viewModel.clearMessage()
  }

  // Hierarchical Back Navigation
  BackHandler(
    enabled =
      fullscreenCustomView != null ||
        (mediaState.isVisible && !mediaState.isMinimized) ||
        activeSheet != BrowserSheet.NONE ||
        findInPageVisible ||
        !activeTab.isSpeedDial
  ) {
    when {
      fullscreenCustomView != null -> viewModel.exitHtmlFullscreen()
      mediaState.isVisible && !mediaState.isMinimized -> viewModel.mediaManager.minimizeToMiniPlayer()
      activeSheet != BrowserSheet.NONE -> viewModel.closeSheet()
      findInPageVisible -> viewModel.toggleFindInPage(false)
      else -> viewModel.goBack()
    }
  }

  Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    Scaffold(
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
      containerColor = MaterialTheme.colorScheme.background,
    ) { _ ->
      BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp

        Row(modifier = Modifier.fillMaxSize()) {
          // Adaptive Tablet / Foldable NavigationRail
          if (isWideScreen && !isInPipMode) {
            NavigationRail(
              containerColor = MaterialTheme.colorScheme.surface,
              modifier = Modifier.statusBarsPadding().navigationBarsPadding(),
            ) {
              Spacer(modifier = Modifier.weight(0.2f))
              NavigationRailItem(
                selected = activeTab.isSpeedDial,
                onClick = { viewModel.goHome() },
                icon = { Icon(Icons.Default.Home, contentDescription = stringResource(R.string.cd_home)) },
                label = { Text("Home") },
              )
              NavigationRailItem(
                selected = false,
                onClick = { viewModel.goBack() },
                enabled = !activeTab.isSpeedDial,
                icon = {
                  Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                  )
                },
                label = { Text("Back") },
              )
              NavigationRailItem(
                selected = false,
                onClick = { viewModel.goForward() },
                enabled = activeTab.canGoForward,
                icon = {
                  Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(R.string.cd_forward),
                  )
                },
                label = { Text("Fwd") },
              )
              NavigationRailItem(
                selected = activeSheet == BrowserSheet.TAB_SWITCHER,
                onClick = { viewModel.openSheet(BrowserSheet.TAB_SWITCHER) },
                icon = {
                  BadgedBox(badge = { Badge { Text("${tabs.size}") } }) {
                    Icon(Icons.Default.Layers, contentDescription = stringResource(R.string.cd_tabs))
                  }
                },
                label = { Text("Tabs") },
              )
              NavigationRailItem(
                selected = activeSheet == BrowserSheet.MEDIA_HUB,
                onClick = { viewModel.openSheet(BrowserSheet.MEDIA_HUB) },
                icon = {
                  BadgedBox(
                    badge = {
                      if (activeTab.detectedMedia.isNotEmpty()) {
                        Badge { Text("${activeTab.detectedMedia.size}") }
                      }
                    }
                  ) {
                    Icon(
                      Icons.Default.VideoLibrary,
                      contentDescription = stringResource(R.string.cd_media_detected),
                    )
                  }
                },
                label = { Text("Media") },
              )
              NavigationRailItem(
                selected = activeSheet == BrowserSheet.VPN,
                onClick = { viewModel.onVpnSheetOpened() },
                icon = {
                  Icon(
                    Icons.Default.Public,
                    contentDescription = stringResource(R.string.cd_vpn_status),
                    tint =
                      if (vpnState.isAnyProtectionActive) voltColors.emeraldShield
                      else MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                },
                label = { Text("Proxy") },
              )
              NavigationRailItem(
                selected = activeSheet == BrowserSheet.LIBRARY_SETTINGS,
                onClick = { viewModel.openSheet(BrowserSheet.LIBRARY_SETTINGS) },
                icon = { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_menu)) },
                label = { Text("Menu") },
              )
              Spacer(modifier = Modifier.weight(1f))
              NavigationRailItem(
                selected = activeSheet == BrowserSheet.EXIT_CONFIRM,
                onClick = { viewModel.openSheet(BrowserSheet.EXIT_CONFIRM) },
                icon = {
                  Icon(
                    Icons.Default.PowerSettingsNew,
                    contentDescription = stringResource(R.string.cd_exit),
                    tint = MaterialTheme.colorScheme.error,
                  )
                },
                label = { Text("Exit", color = MaterialTheme.colorScheme.error) },
                modifier = Modifier.testTag("rail_exit_button"),
              )
            }
          }

          // Primary Browser Column
          Column(
            modifier =
              Modifier.weight(1f)
                .fillMaxHeight()
                .statusBarsPadding()
                .let { if (!isWideScreen) it.navigationBarsPadding().imePadding() else it.navigationBarsPadding() }
          ) {
            // Top Omnibox on Wide Screens
            if (isWideScreen && !isInPipMode) {
              OmniboxDock(
                tab = activeTab,
                tabCount = tabs.size,
                turboShieldEnabled = settings.turboShieldEnabled,
                isVpnActive = vpnState.isAnyProtectionActive,
                showBottomNavControls = false,
                onNavigate = viewModel::navigate,
                onQueryChange = viewModel::onOmniboxQueryChanged,
                onBack = { viewModel.goBack() },
                onForward = viewModel::goForward,
                onReloadOrStop = viewModel::reloadOrStop,
                onHome = viewModel::goHome,
                onOpenSecuritySheet = { viewModel.openSheet(BrowserSheet.SECURITY_TELEMETRY) },
                onOpenVpnSheet = { viewModel.onVpnSheetOpened() },
                onOpenMediaHub = { viewModel.openSheet(BrowserSheet.MEDIA_HUB) },
                onOpenTabs = { viewModel.openSheet(BrowserSheet.TAB_SWITCHER) },
                onOpenMenu = { viewModel.openSheet(BrowserSheet.LIBRARY_SETTINGS) },
                onRequestExit = { viewModel.openSheet(BrowserSheet.EXIT_CONFIRM) },
              )
            }

            // Find in Page Bar
            AnimatedVisibility(visible = findInPageVisible && !activeTab.isSpeedDial) {
              FindInPageBar(
                onQueryChange = { viewModel.findInPage(it, forward = true) },
                onFindNext = { q -> viewModel.findInPage(q, forward = true) },
                onFindPrev = { q -> viewModel.findInPage(q, forward = false) },
                onClose = { viewModel.toggleFindInPage(false) },
              )
            }

            // Viewport: Speed Dial OR Live WebView
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
              if (activeTab.isSpeedDial) {
                SpeedDialHome(
                  isIncognito = activeTab.isIncognito,
                  settings = settings,
                  isVpnActive = vpnState.isAnyProtectionActive,
                  speedDialItems = speedDialItems,
                  isWideScreen = isWideScreen,
                  onOpenUrl = viewModel::navigate,
                  onPrewarmUrl = viewModel.speedEngine::prewarmOrigin,
                  onPlayMedia = viewModel::playDetectedMedia,
                  onOpenMediaHub = { viewModel.openSheet(BrowserSheet.MEDIA_HUB) },
                  onOpenVpn = { viewModel.onVpnSheetOpened() },
                  onAddSpeedDialClick = { viewModel.openSheet(BrowserSheet.ADD_SPEED_DIAL) },
                )
              } else {
                AndroidView(
                  factory = { ctx -> FrameLayout(ctx) },
                  update = { container ->
                    val wv =
                      viewModel.webViewPool.getOrCreateWebView(
                        tabId = activeTab.id,
                        isIncognito = activeTab.isIncognito,
                        callbacks = viewModel.tabWebCallbacks,
                      )
                    if (container.childCount != 1 || container.getChildAt(0) !== wv) {
                      container.removeAllViews()
                      (wv.parent as? ViewGroup)?.removeView(wv)
                      container.addView(
                        wv,
                        FrameLayout.LayoutParams(
                          ViewGroup.LayoutParams.MATCH_PARENT,
                          ViewGroup.LayoutParams.MATCH_PARENT,
                        ),
                      )
                    }
                  },
                  modifier = Modifier.fillMaxSize().testTag("active_webview_container"),
                )
              }
            }

            // Minimized Persistent Media3 Background Audio/Video Bar
            AnimatedVisibility(visible = mediaState.isVisible && mediaState.isMinimized && !isInPipMode) {
              MiniMediaPlayerBar(
                state = mediaState,
                onExpand = viewModel.mediaManager::expandFromMiniPlayer,
                onTogglePlayPause = viewModel.mediaManager::togglePlayPause,
                onClose = viewModel.mediaManager::closePlayer,
              )
            }

            // Bottom Omnibox Dock on Phones
            if (!isWideScreen && !isInPipMode) {
              OmniboxDock(
                tab = activeTab,
                tabCount = tabs.size,
                turboShieldEnabled = settings.turboShieldEnabled,
                isVpnActive = vpnState.isAnyProtectionActive,
                showBottomNavControls = true,
                onNavigate = viewModel::navigate,
                onQueryChange = viewModel::onOmniboxQueryChanged,
                onBack = { viewModel.goBack() },
                onForward = viewModel::goForward,
                onReloadOrStop = viewModel::reloadOrStop,
                onHome = viewModel::goHome,
                onOpenSecuritySheet = { viewModel.openSheet(BrowserSheet.SECURITY_TELEMETRY) },
                onOpenVpnSheet = { viewModel.onVpnSheetOpened() },
                onOpenMediaHub = { viewModel.openSheet(BrowserSheet.MEDIA_HUB) },
                onOpenTabs = { viewModel.openSheet(BrowserSheet.TAB_SWITCHER) },
                onOpenMenu = { viewModel.openSheet(BrowserSheet.LIBRARY_SETTINGS) },
                onRequestExit = { viewModel.openSheet(BrowserSheet.EXIT_CONFIRM) },
              )
            }
          }
        }
      }
    }

    // Visual Tab Switcher Overlay
    AnimatedVisibility(visible = activeSheet == BrowserSheet.TAB_SWITCHER && !isInPipMode) {
      TabSwitcherOverlay(
        tabs = tabs,
        activeTabId = activeTab.id,
        onSelectTab = viewModel::selectTab,
        onCloseTab = viewModel::closeTab,
        onNewTab = { incognito -> viewModel.newTab(isIncognito = incognito) },
        onDismiss = viewModel::closeSheet,
      )
    }

    // Native Media3 Fullscreen / PiP Cinema Player Overlay
    AnimatedVisibility(visible = mediaState.isVisible && !mediaState.isMinimized) {
      MediaPlayerOverlay(
        state = mediaState,
        controller = mediaController,
        isInPipMode = isInPipMode,
        onTogglePlayPause = viewModel.mediaManager::togglePlayPause,
        onSeekTo = viewModel.mediaManager::seekTo,
        onSeekBy = viewModel.mediaManager::seekBy,
        onSelectSpeed = viewModel.mediaManager::setPlaybackSpeed,
        onEnterPip = onEnterPip,
        onMinimize = viewModel.mediaManager::minimizeToMiniPlayer,
        onDownload = {
          mediaState.activeMedia?.let { item ->
            viewModel.downloadMedia(item.url, item.title, item.mimeType)
          }
        },
        onOpenExternal = {
          mediaState.activeMedia?.let { item ->
            viewModel.openInExternalPlayer(item)
          }
        },
        onClose = viewModel.mediaManager::closePlayer,
      )
    }

    // HTML5 WebView Fullscreen Custom View
    fullscreenCustomView?.let { (customView, _) ->
      Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
          factory = { ctx -> FrameLayout(ctx) },
          update = { container ->
            if (container.childCount != 1 || container.getChildAt(0) !== customView) {
              container.removeAllViews()
              (customView.parent as? ViewGroup)?.removeView(customView)
              container.addView(
                customView,
                FrameLayout.LayoutParams(
                  ViewGroup.LayoutParams.MATCH_PARENT,
                  ViewGroup.LayoutParams.MATCH_PARENT,
                ),
              )
            }
          },
          modifier = Modifier.fillMaxSize(),
        )
      }
    }

    // Sheets & Dialogs
    when (activeSheet) {
      BrowserSheet.MEDIA_HUB -> {
        MediaHubSheet(
          detectedMedia = activeTab.detectedMedia,
          isScanningMedia = activeTab.isScanningMedia,
          downloads = downloads,
          onRescanPage = viewModel::rescanActivePageMedia,
          onResolvePlaylistUrl = viewModel::resolveMediaOrPlaylistUrl,
          onRequerySizes = viewModel::requeryAllMediaSizes,
          onPlayDetected = viewModel::playDetectedMedia,
          onOpenExternalDetected = viewModel::openInExternalPlayer,
          onDownloadDetected = { item ->
            viewModel.downloadMedia(item.url, item.title, item.mimeType, item.sourcePageUrl)
          },
          onDownloadDirectUrl = { url -> viewModel.downloadMedia(url = url) },
          onStreamDirectUrl = viewModel::streamDirectMediaUrl,
          onOpenExternalDirectUrl = viewModel::openDirectUrlInExternalPlayer,
          onCancelDownload = viewModel::cancelDownload,
          onRetryDownload = viewModel::retryDownload,
          onPlayDownload = viewModel::playDownloadedFile,
          onOpenExternalDownload = viewModel::openDownloadInExternalPlayer,
          onExportDownload = viewModel::exportDownloadToDevice,
          onSaveDownloadAs = viewModel::saveDownloadAsUri,
          onShareDownload = viewModel::shareDownload,
          onDeleteDownload = viewModel::deleteDownload,
          onPlayLocalUri = viewModel::playLocalMediaUri,
          onCopyUrlToast = { viewModel.showMessage("Media URL copied") },
          onDismiss = viewModel::closeSheet,
        )
      }
      BrowserSheet.SECURITY_TELEMETRY -> {
        SecurityTelemetrySheet(
          tab = activeTab,
          settings = settings,
          onToggleTurboShield = viewModel::toggleTurboShield,
          onToggleLiteMode = viewModel::toggleLiteMode,
          onToggleDarkWeb = viewModel::toggleDarkWeb,
          onToggleDesktopMode = viewModel::toggleDesktopMode,
          onWipeSessionNow = viewModel::wipeSessionCacheAndCookies,
          onRequestExit = { viewModel.openSheet(BrowserSheet.EXIT_CONFIRM) },
          onDismiss = viewModel::closeSheet,
        )
      }
      BrowserSheet.LIBRARY_SETTINGS -> {
        LibrarySettingsSheet(
          activeTab = activeTab,
          isBookmarked = isBookmarked,
          settings = settings,
          bookmarks = bookmarks,
          onToggleBookmark = viewModel::toggleBookmark,
          onSharePage = viewModel::shareCurrentPage,
          onOpenFindInPage = { viewModel.toggleFindInPage(true) },
          onOpenMediaHub = { viewModel.openSheet(BrowserSheet.MEDIA_HUB) },
          onOpenUrl = viewModel::navigate,
          onDeleteBookmark = viewModel::deleteBookmark,
          onSelectSearchEngine = viewModel::setSearchEngine,
          onWipeSessionNow = viewModel::wipeSessionCacheAndCookies,
          onRequestExit = { viewModel.openSheet(BrowserSheet.EXIT_CONFIRM) },
          onDismiss = viewModel::closeSheet,
        )
      }
      BrowserSheet.ADD_SPEED_DIAL -> {
        AddSpeedDialDialog(
          onConfirm = viewModel::addSpeedDial,
          onDismiss = viewModel::closeSheet,
        )
      }
      BrowserSheet.VPN -> {
        FreePublicVpnSheet(
          state = vpnState,
          settings = settings,
          onRefreshRelays = viewModel::refreshPublicRelays,
          onAutoConnectFastestRelay = { viewModel.autoConnectFastestRelay(reloadActiveTab = false) },
          onRotateProxy = { viewModel.rotateToNextProxy(reloadActiveTab = false) },
          onConnectRelay = { relay -> viewModel.connectRelay(relay, reloadActiveTab = false) },
          onDisconnectRelay = viewModel::disconnectRelay,
          onToggleAutoProxyFailover = viewModel::toggleAutoProxyFailover,
          onToggleAutoProxyOnLaunch = viewModel::toggleAutoProxyOnLaunch,
          onToggleDnsVpn = { enable ->
            if (enable) {
              val consentIntent = viewModel.prepareVpnConsentIntent()
              if (consentIntent != null) {
                vpnConsentLauncher.launch(consentIntent)
              } else {
                viewModel.startDnsVpnService()
              }
            } else {
              viewModel.stopDnsVpn()
            }
          },
          onSelectDohProvider = viewModel::selectDohProvider,
          onDismiss = viewModel::closeSheet,
        )
      }
      BrowserSheet.EXIT_CONFIRM -> {
        ExitConfirmDialog(
          isErasing = isErasingOnExit,
          onConfirm = {
            if (!isErasingOnExit) {
              isErasingOnExit = true
              viewModel.exitAndEraseAllData {
                isErasingOnExit = false
                (context as? Activity)?.finishAndRemoveTask()
              }
            }
          },
          onDismiss = viewModel::closeSheet,
        )
      }
      else -> Unit
    }
  }
}

@Composable
private fun FindInPageBar(
  onQueryChange: (String) -> Unit,
  onFindNext: (String) -> Unit,
  onFindPrev: (String) -> Unit,
  onClose: () -> Unit,
) {
  val voltColors = LocalVoltColors.current
  var query by remember { mutableStateOf("") }

  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.weight(1f),
      ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
          if (query.isEmpty()) {
            Text(
              text = "Find in page...",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          BasicTextField(
            value = query,
            onValueChange = {
              query = it
              onQueryChange(it)
            },
            singleLine = true,
            textStyle =
              MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface
              ),
            cursorBrush = SolidColor(voltColors.cyanAccent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onFindNext(query) }),
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
      IconButton(onClick = { onFindPrev(query) }, modifier = Modifier.size(36.dp)) {
        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous match")
      }
      IconButton(onClick = { onFindNext(query) }, modifier = Modifier.size(36.dp)) {
        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next match")
      }
      IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
        Icon(Icons.Default.Close, contentDescription = "Close find bar")
      }
    }
  }
}
