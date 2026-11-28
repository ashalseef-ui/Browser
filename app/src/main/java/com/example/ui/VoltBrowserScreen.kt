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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.WebContextMenuTarget
import com.example.ui.components.EdgeGestureOverlay
import com.example.ui.components.OmniboxDock
import com.example.ui.components.extractHost
import com.example.ui.screens.AddSpeedDialDialog
import com.example.ui.screens.ExitConfirmDialog
import com.example.ui.screens.FreePublicVpnSheet
import com.example.ui.screens.LibrarySettingsSheet
import com.example.ui.screens.MediaHubSheet
import com.example.ui.screens.MediaPlayerOverlay
import com.example.ui.screens.MiniMediaPlayerBar
import com.example.ui.screens.SecurityTelemetrySheet
import com.example.ui.screens.SocialSidebarOverlay
import com.example.ui.screens.SpeedDialHome
import com.example.ui.screens.TabSwitcherOverlay
import com.example.ui.screens.WebContextMenuSheet
import com.example.ui.theme.LocalVoltColors

/**
 * Root adaptive Compose shell orchestrating Speed Dial / WebView stage, bottom Omnibox dock,
 * tablet NavigationRail, fullscreen Media3 player, and modal sheets.
 */
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
  val pendingSitePermissionPrompt by viewModel.pendingSitePermissionPrompt.collectAsStateWithLifecycle()
  val pendingRuntimePermissions by viewModel.pendingRuntimePermissionRequest.collectAsStateWithLifecycle()

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

  var askedNotificationPermission by remember { mutableStateOf(false) }
  val notificationPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

  val runtimePermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
      viewModel.onRuntimePermissionsResult(results)
    }

  LaunchedEffect(pendingRuntimePermissions) {
    val req = pendingRuntimePermissions ?: return@LaunchedEffect
    runtimePermissionLauncher.launch(req.permissions.toTypedArray())
  }

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

  BoxWithConstraints(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    val isWideScreen = maxWidth >= 600.dp
    val showPhoneBottomDock = !isWideScreen && !isInPipMode && !isBrowserFullscreen
    val showMiniPlayerInBar = mediaState.isVisible && mediaState.isMinimized && !isInPipMode && !isBrowserFullscreen
    val bottomBarConsumesNavInset = showPhoneBottomDock || showMiniPlayerInBar
    val activeTabIndex = remember(tabs, activeTab.id) {
      tabs.indexOfFirst { it.id == activeTab.id }.coerceAtLeast(0)
    }
    val previousTabTitle =
      if (tabs.size > 1) tabs[(activeTabIndex - 1 + tabs.size) % tabs.size].title else ""
    val nextTabTitle =
      if (tabs.size > 1) tabs[(activeTabIndex + 1) % tabs.size].title else ""

    Scaffold(
      contentWindowInsets = WindowInsets(0, 0, 0, 0),
      snackbarHost = {
        SnackbarHost(
          hostState = snackbarHostState,
          modifier =
            if (!bottomBarConsumesNavInset && !isAnyFullscreen) {
              Modifier.navigationBarsPadding()
            } else {
              Modifier
            },
        )
      },
      bottomBar = {
        if (bottomBarConsumesNavInset) {
          Column(
            modifier =
              Modifier.fillMaxWidth()
                .let { if (isAnyFullscreen) it else it.navigationBarsPadding() }
                .let { if (!isWideScreen) it.imePadding() else it }
          ) {
            AnimatedVisibility(visible = showMiniPlayerInBar) {
              MiniMediaPlayerBar(
                state = mediaState,
                onExpand = viewModel.mediaManager::expandFromMiniPlayer,
                onTogglePlayPause = viewModel.mediaManager::togglePlayPause,
                onClose = viewModel.mediaManager::closePlayer,
              )
            }
            if (showPhoneBottomDock) {
              OmniboxDock(
                tab = activeTab,
                tabCount = tabs.size,
                previousTabTitle = previousTabTitle,
                nextTabTitle = nextTabTitle,
                searchEngine = settings.searchEngine,
                adBlockerType = settings.adBlockerType,
                isVpnActive = vpnState.isAnyProtectionActive,
                showBottomNavControls = true,
                onNavigate = viewModel::navigate,
                onQueryChange = viewModel::onOmniboxQueryChanged,
                onSelectSearchEngine = viewModel::setSearchEngine,
                onCycleSearchEngine = viewModel::cycleSearchEngine,
                onBack = { viewModel.goBack() },
                onForward = viewModel::goForward,
                onReloadOrStop = viewModel::reloadOrStop,
                onHardReload = viewModel::hardReloadActiveTab,
                onHome = viewModel::goHome,
                onNewPrivateTab = { viewModel.newTab(isIncognito = true) },
                onOpenSecuritySheet = { viewModel.openSheet(BrowserSheet.SECURITY_TELEMETRY) },
                onOpenVpnSheet = { viewModel.onVpnSheetOpened() },
                onOpenMediaHub = { viewModel.openSheet(BrowserSheet.MEDIA_HUB) },
                onOpenTabs = { viewModel.openSheet(BrowserSheet.TAB_SWITCHER) },
                onOpenMenu = { viewModel.openSheet(BrowserSheet.LIBRARY_SETTINGS) },
                onShowToast = viewModel::showMessage,
                onSwipeLeft = viewModel::switchToNextTab,
                onSwipeRight = viewModel::switchToPreviousTab,
              )
            }
          }
        }
      },
      containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
      Row(modifier = Modifier.fillMaxSize().padding(bottom = innerPadding.calculateBottomPadding())) {
        if (isWideScreen && !isInPipMode && !isBrowserFullscreen) {
          BrowserNavigationRail(
            activeTab = activeTab,
            tabCount = tabs.size,
            activeSheet = activeSheet,
            isVpnActive = vpnState.isAnyProtectionActive,
            bottomBarConsumesNavInset = bottomBarConsumesNavInset,
            onHome = { viewModel.goHome() },
            onBack = { viewModel.goBack() },
            onForward = viewModel::goForward,
            onOpenTabs = { viewModel.openSheet(BrowserSheet.TAB_SWITCHER) },
            onOpenMediaHub = { viewModel.openSheet(BrowserSheet.MEDIA_HUB) },
            onOpenVpnSheet = { viewModel.onVpnSheetOpened() },
            onOpenMenu = { viewModel.openSheet(BrowserSheet.LIBRARY_SETTINGS) },
            onRequestExit = { viewModel.openSheet(BrowserSheet.EXIT_CONFIRM) },
          )
        }

        Column(
          modifier =
            Modifier.weight(1f)
              .fillMaxHeight()
              .let {
                when {
                  isAnyFullscreen -> it
                  bottomBarConsumesNavInset -> it.statusBarsPadding()
                  else -> it.statusBarsPadding().navigationBarsPadding()
                }
              }
              .let { if (!isWideScreen && !bottomBarConsumesNavInset) it.imePadding() else it }
        ) {
          if (isWideScreen && !isInPipMode && !isBrowserFullscreen) {
            OmniboxDock(
              tab = activeTab,
              tabCount = tabs.size,
              previousTabTitle = previousTabTitle,
              nextTabTitle = nextTabTitle,
              searchEngine = settings.searchEngine,
              adBlockerType = settings.adBlockerType,
              isVpnActive = vpnState.isAnyProtectionActive,
              showBottomNavControls = false,
              onNavigate = viewModel::navigate,
              onQueryChange = viewModel::onOmniboxQueryChanged,
              onSelectSearchEngine = viewModel::setSearchEngine,
              onCycleSearchEngine = viewModel::cycleSearchEngine,
              onBack = { viewModel.goBack() },
              onForward = viewModel::goForward,
              onReloadOrStop = viewModel::reloadOrStop,
              onHardReload = viewModel::hardReloadActiveTab,
              onHome = viewModel::goHome,
              onNewPrivateTab = { viewModel.newTab(isIncognito = true) },
              onOpenSecuritySheet = { viewModel.openSheet(BrowserSheet.SECURITY_TELEMETRY) },
              onOpenVpnSheet = { viewModel.onVpnSheetOpened() },
              onOpenMediaHub = { viewModel.openSheet(BrowserSheet.MEDIA_HUB) },
              onOpenTabs = { viewModel.openSheet(BrowserSheet.TAB_SWITCHER) },
              onOpenMenu = { viewModel.openSheet(BrowserSheet.LIBRARY_SETTINGS) },
              onShowToast = viewModel::showMessage,
              onSwipeLeft = viewModel::switchToNextTab,
              onSwipeRight = viewModel::switchToPreviousTab,
            )
          }

          AnimatedVisibility(visible = findInPageVisible && !activeTab.isSpeedDial) {
            FindInPageBar(
              onQueryChange = { viewModel.findInPage(it, forward = true) },
              onFindNext = { q -> viewModel.findInPage(q, forward = true) },
              onFindPrev = { q -> viewModel.findInPage(q, forward = false) },
              onClose = { viewModel.toggleFindInPage(false) },
            )
          }

          Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (activeTab.isSpeedDial) {
              SpeedDialHome(
                isIncognito = activeTab.isIncognito,
                settings = settings,
                sessionDataUsedBytes = sessionDataUsedBytes,
                isVpnActive = vpnState.isAnyProtectionActive,
                speedDialItems = speedDialItems,
                onOpenUrl = { url -> viewModel.navigate(url, null) },
                onPrewarmUrl = viewModel.speedEngine::prewarmOrigin,
                onNewPrivateTab = { viewModel.newTab(isIncognito = true) },
                onOpenSecuritySheet = { viewModel.openSheet(BrowserSheet.SECURITY_TELEMETRY) },
                onOpenVpnSheet = { viewModel.onVpnSheetOpened() },
                onRequestExit = { viewModel.openSheet(BrowserSheet.EXIT_CONFIRM) },
                onAddSpeedDialClick = { viewModel.openSheet(BrowserSheet.ADD_SPEED_DIAL) },
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
              EdgeGestureOverlay(
                enabled = !isBrowserFullscreen && !isInPipMode,
                canGoBack = activeTab.canGoBack,
                canGoForward = activeTab.canGoForward,
                onSwipeBack = { viewModel.goBack() },
                onSwipeForward = viewModel::goForward,
                modifier = Modifier.fillMaxSize(),
              ) {
                AndroidView(
                  factory = { ctx -> FrameLayout(ctx) },
                  update = { container ->
                    val wv =
                      viewModel.webViewPool.getOrCreateWebView(
                        tabId = activeTab.id,
                        isIncognito = activeTab.isIncognito,
                        callbacks = viewModel.tabWebCallbacks,
                        fallbackUrl = activeTab.url.takeIf { it.isNotBlank() },
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

              activeTab.httpsWarning?.let { warning ->
                HttpsWarningOverlay(
                  warning = warning,
                  onTryHttps = { viewModel.tryHttpsInstead(activeTab.id, warning.failedUrl) },
                  onGoBack = { viewModel.dismissHttpsWarning(activeTab.id) },
                  onContinueInsecure = {
                    viewModel.proceedWithInsecureHttp(activeTab.id, warning.fallbackHttpUrl)
                  },
                )
              }
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

            if (isBrowserFullscreen && fullscreenCustomView == null && !isInPipMode) {
              Surface(
                shape = RoundedCornerShape(100.dp),
                color = Color(0xCC121216),
                border = BorderStroke(1.dp, voltColors.brandAccent.copy(alpha = 0.65f)),
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
                    tint = voltColors.brandAccent,
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
        }
      }
    }

    AnimatedVisibility(visible = activeSheet == BrowserSheet.TAB_SWITCHER && !isInPipMode) {
      TabSwitcherOverlay(
        tabs = tabs,
        activeTabId = activeTab.id,
        onSelectTab = viewModel::selectTab,
        onCloseTab = viewModel::closeTab,
        onCloseAllTabs = viewModel::closeAllTabs,
        onNewTab = { incognito -> viewModel.newTab(isIncognito = incognito) },
        onDismiss = viewModel::closeSheet,
      )
    }

    AnimatedVisibility(visible = mediaState.isVisible && !mediaState.isMinimized) {
      MediaPlayerOverlay(
        state = mediaState,
        controller = mediaController,
        isInPipMode = isInPipMode,
        onTogglePlayPause = viewModel.mediaManager::togglePlayPause,
        onSeekTo = viewModel.mediaManager::seekTo,
        onSeekBy = viewModel.mediaManager::seekBy,
        onSelectSpeed = viewModel.mediaManager::setPlaybackSpeed,
        onToggleMute = viewModel.mediaManager::toggleMute,
        onToggleLoop = viewModel.mediaManager::toggleLoop,
        onCycleResizeMode = viewModel.mediaManager::cycleResizeMode,
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

    when (activeSheet) {
      BrowserSheet.MEDIA_HUB -> {
        MediaHubSheet(
          detectedMedia = activeTab.detectedMedia,
          isScanningMedia = activeTab.isScanningMedia,
          downloads = downloads,
          currentHost = if (activeTab.isSpeedDial) "" else extractHost(activeTab.url),
          isSiteBypassEnabled = activeTab.isMediaCompatBypassEnabled,
          onToggleSiteBypass = viewModel::toggleCurrentSiteMediaCompatBypass,
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
          onToggleHttpsOnly = viewModel::toggleHttpsOnly,
          onToggleDarkWeb = viewModel::toggleDarkWeb,
          onToggleSiteAdBlockPause = viewModel::toggleCurrentSiteAdBlockPause,
          onToggleSiteJs = viewModel::toggleCurrentSiteJs,
          onToggleSiteDesktop = viewModel::toggleCurrentSiteDesktop,
          onToggleSiteMediaBypass = viewModel::toggleCurrentSiteMediaCompatBypass,
          onResetSiteSettings = viewModel::resetCurrentSiteSettings,
          onClearSitePermissions = viewModel::clearSitePermissions,
          onWipeSessionNow = viewModel::wipeSessionCacheAndCookies,
          onDismiss = viewModel::closeSheet,
        )
      }
      BrowserSheet.LIBRARY_SETTINGS -> {
        LibrarySettingsSheet(
          activeTab = activeTab,
          isBookmarked = isBookmarked,
          isBrowserFullscreen = isBrowserFullscreen,
          sessionDataUsedBytes = sessionDataUsedBytes,
          settings = settings,
          bookmarks = bookmarks,
          onToggleBookmark = viewModel::toggleBookmark,
          onSharePage = viewModel::shareCurrentPage,
          onOpenFindInPage = { viewModel.toggleFindInPage(true) },
          onToggleDesktopMode = viewModel::toggleDesktopMode,
          onToggleBrowserFullscreen = { viewModel.toggleBrowserFullscreen() },
          onOpenSecuritySheet = { viewModel.openSheet(BrowserSheet.SECURITY_TELEMETRY) },
          onRequestExit = { viewModel.openSheet(BrowserSheet.EXIT_CONFIRM) },
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
          onToggleWireGuardKillSwitch = viewModel::toggleWireGuardKillSwitch,
          onResetKillSwitch = viewModel::resetWireGuardKillSwitch,
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
          onConfirmPublicProxyWarning = viewModel::markPublicProxyWarned,
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
          viewModel.showMessage("Removed from Workspace")
        },
        onCopiedToast = { msg -> viewModel.showMessage(msg) },
        onDismiss = viewModel::dismissContextMenu,
      )
    }

    pendingSitePermissionPrompt?.let { prompt ->
      SitePermissionPromptDialog(
        prompt = prompt,
        onDecision = viewModel::respondToSitePermissionPrompt,
      )
    }
  }
}
