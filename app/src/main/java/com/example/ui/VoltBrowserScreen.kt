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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.FullscreenExit
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import com.example.ui.screens.OmniSearchDialog
import com.example.ui.screens.SecurityTelemetrySheet
import com.example.ui.screens.SocialSidebarOverlay
import com.example.ui.screens.SpeedDialHome
import com.example.data.WebContextMenuTarget
import com.example.ui.screens.TabSwitcherOverlay
import com.example.ui.screens.WebContextMenuSheet
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
  val isBrowserFullscreen by viewModel.isBrowserFullscreen.collectAsStateWithLifecycle()
  val contextMenuTarget by viewModel.contextMenuTarget.collectAsStateWithLifecycle()
  val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()
  val mediaState by viewModel.mediaPlayerState.collectAsStateWithLifecycle()
  val mediaController by viewModel.mediaManager.controllerFlow.collectAsStateWithLifecycle()
  val vpnState by viewModel.vpnState.collectAsStateWithLifecycle()
  val sessionDataUsedBytes by viewModel.sessionDataUsedBytes.collectAsStateWithLifecycle()
  val socialSidebarOpen by viewModel.socialSidebarOpen.collectAsStateWithLifecycle()
  val socialApps by viewModel.socialApps.collectAsStateWithLifecycle()
  val hasSystemAutofill by viewModel.hasSystemAutofill.collectAsStateWithLifecycle()

  val isMediaOverlayFullscreen = mediaState.isVisible && !mediaState.isMinimized && !isInPipMode
  val isAnyFullscreen = fullscreenCustomView != null || isBrowserFullscreen || isMediaOverlayFullscreen
  val view = LocalView.current

  LaunchedEffect(isAnyFullscreen) {
    val window = (context as? Activity)?.window ?: return@LaunchedEffect
    val controller = WindowCompat.getInsetsController(window, view)
    if (isAnyFullscreen) {
      controller.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      controller.hide(WindowInsetsCompat.Type.systemBars())
    } else {
      controller.show(WindowInsetsCompat.Type.systemBars())
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      val window = (context as? Activity)?.window ?: return@onDispose
      WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.systemBars())
    }
  }

  val snackbarHostState = remember { SnackbarHostState() }
  var isErasingOnExit by remember { mutableStateOf(false) }

  var pendingVpnAction by remember { mutableStateOf<(() -> Unit)?>(null) }
  val vpnConsentLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      if (result.resultCode == Activity.RESULT_OK) {
        pendingVpnAction?.invoke()
      } else {
        viewModel.showMessage("VPN permission is required to enable the encrypted tunnel")
      }
      pendingVpnAction = null
    }

  val runWithVpnConsent: (() -> Unit) -> Unit = { action ->
    val consentIntent = viewModel.prepareVpnConsentIntent()
    if (consentIntent != null) {
      pendingVpnAction = action
      vpnConsentLauncher.launch(consentIntent)
    } else {
      action()
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
      contextMenuTarget != null ||
        fullscreenCustomView != null ||
        isBrowserFullscreen ||
        (mediaState.isVisible && !mediaState.isMinimized) ||
        socialSidebarOpen ||
        activeSheet != BrowserSheet.NONE ||
        findInPageVisible ||
        !activeTab.isSpeedDial
  ) {
    when {
      contextMenuTarget != null -> viewModel.dismissContextMenu()
      fullscreenCustomView != null -> viewModel.exitHtmlFullscreen()
      isBrowserFullscreen -> viewModel.toggleBrowserFullscreen(false)
      mediaState.isVisible && !mediaState.isMinimized -> viewModel.mediaManager.minimizeToMiniPlayer()
      socialSidebarOpen -> viewModel.closeSocialSidebar()
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
          // Adaptive Tablet / Foldable NavigationRail (hidden in fullscreen or PiP)
          if (isWideScreen && !isInPipMode && !isBrowserFullscreen) {
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
                .let { if (isAnyFullscreen) it else it.statusBarsPadding().navigationBarsPadding() }
                .let { if (!isWideScreen) it.imePadding() else it }
          ) {
            // Top Omnibox on Wide Screens
            if (isWideScreen && !isInPipMode && !isBrowserFullscreen) {
              OmniboxDock(
                tab = activeTab,
                tabCount = tabs.size,
                adBlockerType = settings.adBlockerType,
                isVpnActive = vpnState.isAnyProtectionActive,
                sessionDataUsedBytes = sessionDataUsedBytes,
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
                onSwipeLeft = viewModel::switchToNextTab,
                onSwipeRight = viewModel::switchToPreviousTab,
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
                  speedDialItems = speedDialItems,
                  onOpenUrl = viewModel::navigate,
                  onPrewarmUrl = viewModel.speedEngine::prewarmOrigin,
                  onOpenSecuritySheet = { viewModel.openSheet(BrowserSheet.SECURITY_TELEMETRY) },
                  onAddSpeedDialClick = { viewModel.openSheet(BrowserSheet.ADD_SPEED_DIAL) },
                  onOpenOmniSearch = { viewModel.openSheet(BrowserSheet.OMNI_SEARCH) },
                  onLongPressTile = { bookmark ->
                    viewModel.showContextMenu(
                      WebContextMenuTarget(
                        pageUrl = "",
                        linkUrl = bookmark.url,
                        linkText = bookmark.title,
                        bookmarkId = bookmark.id,
                      )
                    )
                  },
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

              if (!isInPipMode) {
                SocialSidebarOverlay(
                  isOpen = socialSidebarOpen,
                  socialApps = socialApps,
                  hasSystemAutofill = hasSystemAutofill,
                  onToggleSidebar = viewModel::toggleSocialSidebar,
                  onDismiss = viewModel::closeSocialSidebar,
                  onOpenInBrowser = viewModel::openSocialInBrowser,
                  onLaunchInstalledApp = viewModel::launchInstalledSocialApp,
                  onTriggerAutofill = viewModel::triggerSocialAutofill,
                  onOpenAutofillSettings = viewModel::openDeviceAutofillSettings,
                )
              }

              // Floating translucent pill to exit Browser Fullscreen
              if (isBrowserFullscreen && fullscreenCustomView == null && !isInPipMode) {
                Surface(
                  shape = RoundedCornerShape(100.dp),
                  color = Color(0xCC090D14),
                  border = BorderStroke(1.dp, voltColors.cyanAccent.copy(alpha = 0.55f)),
                  shadowElevation = 6.dp,
                  modifier =
                    Modifier.align(Alignment.TopEnd)
                      .padding(top = 12.dp, end = 12.dp)
                      .clip(RoundedCornerShape(100.dp))
                      .clickable { viewModel.toggleBrowserFullscreen(false) }
                      .testTag("exit_fullscreen_button"),
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Default.FullscreenExit,
                      contentDescription = "Exit fullscreen",
                      tint = voltColors.cyanAccent,
                      modifier = Modifier.size(16.dp),
                    )
                    Text(
                      text = "Exit Fullscreen",
                      style = MaterialTheme.typography.labelSmall,
                      color = Color.White,
                    )
                  }
                }
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

            // Bottom Omnibox Dock on Phones (hidden while browser fullscreen is active)
            if (!isWideScreen && !isInPipMode && !isBrowserFullscreen) {
              OmniboxDock(
                tab = activeTab,
                tabCount = tabs.size,
                adBlockerType = settings.adBlockerType,
                isVpnActive = vpnState.isAnyProtectionActive,
                sessionDataUsedBytes = sessionDataUsedBytes,
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
                onSwipeLeft = viewModel::switchToNextTab,
                onSwipeRight = viewModel::switchToPreviousTab,
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
          onOpenDownload = viewModel::openDownloadedFile,
          onExportDownload = viewModel::exportDownloadToDevice,
          onSaveDownloadAs = viewModel::saveDownloadAsUri,
          onShareDownload = viewModel::shareDownload,
          onDeleteDownload = viewModel::deleteDownload,
          onPlayLocalUri = viewModel::playLocalMediaUri,
          onLongPressDetectedMedia = { item ->
            viewModel.showContextMenu(
              WebContextMenuTarget(
                pageUrl = item.sourcePageUrl,
                mediaUrl = item.url,
                mediaKind = item.kind,
                mediaTitle = item.title,
                imageUrl = item.posterUrl,
              )
            )
          },
          onCopyUrlToast = { viewModel.showMessage("Media URL copied") },
          onDismiss = viewModel::closeSheet,
        )
      }
      BrowserSheet.SECURITY_TELEMETRY -> {
        SecurityTelemetrySheet(
          tab = activeTab,
          settings = settings,
          sessionDataUsedBytes = sessionDataUsedBytes,
          onSelectAdBlockerType = viewModel::setAdBlockerType,
          onToggleLiteMode = viewModel::toggleLiteMode,
          onToggleDarkWeb = viewModel::toggleDarkWeb,
          onWipeSessionNow = viewModel::wipeSessionCacheAndCookies,
          onDismiss = viewModel::closeSheet,
        )
      }
      BrowserSheet.LIBRARY_SETTINGS -> {
        LibrarySettingsSheet(
          activeTab = activeTab,
          isBookmarked = isBookmarked,
          isBrowserFullscreen = isBrowserFullscreen,
          settings = settings,
          bookmarks = bookmarks,
          onToggleBookmark = viewModel::toggleBookmark,
          onSharePage = viewModel::shareCurrentPage,
          onOpenFindInPage = { viewModel.toggleFindInPage(true) },
          onToggleDesktopMode = viewModel::toggleDesktopMode,
          onToggleBrowserFullscreen = { viewModel.toggleBrowserFullscreen() },
          onOpenSecuritySheet = { viewModel.openSheet(BrowserSheet.SECURITY_TELEMETRY) },
          onOpenUrl = viewModel::navigate,
          onDeleteBookmark = viewModel::deleteBookmark,
          onToggleLiteMode = viewModel::toggleLiteMode,
          onSelectSearchEngine = viewModel::setSearchEngine,
          onSelectThemeMode = viewModel::setThemeMode,
          onDismiss = viewModel::closeSheet,
        )
      }
      BrowserSheet.ADD_SPEED_DIAL -> {
        AddSpeedDialDialog(
          onConfirm = viewModel::addSpeedDial,
          onDismiss = viewModel::closeSheet,
        )
      }
      BrowserSheet.OMNI_SEARCH -> {
        OmniSearchDialog(
          onConfirmMultiQuery = viewModel::openOmniMultiQuery,
          onDismiss = viewModel::closeSheet,
        )
      }
      BrowserSheet.VPN -> {
        FreePublicVpnSheet(
          state = vpnState,
          settings = settings,
          onConnectWarp = { runWithVpnConsent { viewModel.connectCloudflareWarp() } },
          onConnectSavedCustomWg = { runWithVpnConsent { viewModel.connectSavedCustomWireGuard() } },
          onConnectCustomWgConf = { conf, name ->
            runWithVpnConsent { viewModel.connectCustomWireGuardConf(conf, name) }
          },
          onDisconnectWireGuard = { viewModel.disconnectWireGuard() },
          onToggleAutoWireGuardOnLaunch = viewModel::toggleAutoWireGuardOnLaunch,
          onRefreshRelays = viewModel::refreshPublicRelays,
          onAutoConnectFastestRelay = { viewModel.autoConnectFastestRelay(reloadActiveTab = false) },
          onRotateProxy = { viewModel.rotateToNextProxy(reloadActiveTab = false) },
          onConnectRelay = { relay -> viewModel.connectRelay(relay, reloadActiveTab = false) },
          onDisconnectRelay = viewModel::disconnectRelay,
          onToggleAutoProxyFailover = viewModel::toggleAutoProxyFailover,
          onToggleAutoProxyOnLaunch = viewModel::toggleAutoProxyOnLaunch,
          onToggleAutoDnsVpnOnLaunch = { enable ->
            viewModel.toggleAutoDnsVpnOnLaunch(enable)
            if (enable && !vpnState.isDnsVpnActive && !vpnState.isWireGuardActive) {
              runWithVpnConsent { viewModel.startDnsVpnService() }
            }
          },
          onSelectProtocolFilter = viewModel::setProxyProtocolFilter,
          onSelectCountryFilter = viewModel::setProxyCountryFilter,
          onConnectCustomProxy = viewModel::connectCustomProxy,
          onToggleDnsVpn = { enable ->
            if (enable) {
              runWithVpnConsent { viewModel.startDnsVpnService() }
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

    // Long-Press ("Right-Click") Context Menu Sheet for links, images, and media
    contextMenuTarget?.let { target ->
      WebContextMenuSheet(
        target = target,
        onOpenCurrentTab = viewModel::navigate,
        onOpenNewTab = { url, incognito ->
          viewModel.newTab(isIncognito = incognito, initialUrl = url)
        },
        onPlayMediaInternal = viewModel::playMediaUrlInternally,
        onOpenMediaExternal = viewModel::openMediaUrlExternally,
        onDownloadUrl = { url, title, mime, referer ->
          viewModel.downloadMedia(
            url = url,
            fileName = title,
            mimeType = mime,
            referer = referer,
          )
        },
        onShareUrl = viewModel::shareUrl,
        onDeleteBookmark = { id ->
          viewModel.deleteBookmark(id)
          viewModel.showMessage("Removed from Speed Dial")
        },
        onCopiedToast = { msg -> viewModel.showMessage(msg) },
        onDismiss = viewModel::dismissContextMenu,
      )
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
