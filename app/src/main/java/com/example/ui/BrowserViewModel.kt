package com.example.ui

import android.Manifest
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.VoltBrowserApp
import com.example.browser.DeviceTraceCleaner
import com.example.browser.FirefoxAddonManager
import com.example.browser.GeckoTabPool
import com.example.browser.MediaSniffer
import com.example.browser.MediaSnifferCoordinator
import com.example.browser.SocialHubHelper
import com.example.browser.TabWebCallbacks
import com.example.browser.VoltSpeedEngine
import com.example.browser.WebContextTargetResolver
import com.example.browser.WebViewTabPool
import com.example.data.AdBlockerType
import com.example.data.AppThemeMode
import com.example.data.BookmarkEntity
import com.example.data.BrowserEngineMode
import com.example.data.BrowserSettings
import com.example.data.BrowserTabState
import com.example.data.DetectedMediaItem
import com.example.data.DownloadItemEntity
import com.example.data.FirefoxAddonInfo
import com.example.data.HttpsWarningState
import com.example.data.MediaKind
import com.example.data.SearchEngine
import com.example.data.SitePermissionType
import com.example.data.SocialAppShortcut
import com.example.data.WebContextMenuTarget
import com.example.media.MediaDownloader
import com.example.media.MediaPlaybackManager
import com.example.media.MediaPlayerUiState
import com.example.media.VoltMediaService
import com.example.vpn.DohProvider
import com.example.vpn.ProxyProtocolFilter
import com.example.vpn.PublicRelayNode
import com.example.vpn.VpnProxyCoordinator
import com.example.vpn.VpnTunnelState
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Primary MVVM state holder coordinating browser tabs, WebView pool lifecycle,
 * per-site security/render overrides, Media3 playback/downloads, and VPN/proxy tunnels.
 */
class BrowserViewModel(application: Application) : AndroidViewModel(application) {

  private val app = application as VoltBrowserApp
  private val repository = app.repository
  val speedEngine = app.speedEngine

  val webViewPool = WebViewTabPool(application, speedEngine, viewModelScope)
  val firefoxAddonManager =
    FirefoxAddonManager(
      context = application,
      scope = viewModelScope,
      onShowMessage = ::showMessage,
      onBridgeMediaDetected = { pageUrl, items ->
        val matchingTab =
          _tabs.value.firstOrNull { !it.isSpeedDial && it.url == pageUrl } ?: activeTab.value
        if (!matchingTab.isSpeedDial) {
          mediaSnifferCoordinator.mergeAndProbeMediaItems(matchingTab.id, items)
        }
      },
    )
  val geckoTabPool = GeckoTabPool(application, firefoxAddonManager, speedEngine, viewModelScope)
  val mediaManager = MediaPlaybackManager(application, viewModelScope)
  private val mediaDownloader =
    MediaDownloader(application, speedEngine.okHttpClient, repository, viewModelScope)

  private val initialTab = BrowserTabState(title = "Speed Dial", isSpeedDial = true)
  private val _tabs = MutableStateFlow(listOf(initialTab))
  val tabs: StateFlow<List<BrowserTabState>> = _tabs.asStateFlow()

  private val _activeTabId = MutableStateFlow(initialTab.id)
  val activeTabId: StateFlow<String> = _activeTabId.asStateFlow()

  val activeTab: StateFlow<BrowserTabState> =
    combine(_tabs, _activeTabId) { list, id ->
        list.firstOrNull { it.id == id } ?: list.first()
      }
      .stateIn(viewModelScope, SharingStarted.Eagerly, initialTab)

  val settings: StateFlow<BrowserSettings> =
    repository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, BrowserSettings())

  val bookmarks: StateFlow<List<BookmarkEntity>> =
    repository.allBookmarks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val speedDialItems: StateFlow<List<BookmarkEntity>> =
    repository.speedDialBookmarks.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      emptyList(),
    )

  val downloads: StateFlow<List<DownloadItemEntity>> =
    repository.downloads.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  @Suppress("OPT_IN_USAGE")
  val isActivePageBookmarked: StateFlow<Boolean> =
    activeTab
      .flatMapLatest { tab ->
        if (tab.url.isBlank() || tab.isSpeedDial) flowOf(false)
        else repository.observeIsBookmarked(tab.url)
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

  private val _activeSheet = MutableStateFlow(BrowserSheet.NONE)
  val activeSheet: StateFlow<BrowserSheet> = _activeSheet.asStateFlow()

  private val _findInPageVisible = MutableStateFlow(false)
  val findInPageVisible: StateFlow<Boolean> = _findInPageVisible.asStateFlow()

  private val _fullscreenCustomView =
    MutableStateFlow<Pair<View, WebChromeClient.CustomViewCallback>?>(null)
  val fullscreenCustomView: StateFlow<Pair<View, WebChromeClient.CustomViewCallback>?> =
    _fullscreenCustomView.asStateFlow()

  private val _isBrowserFullscreen = MutableStateFlow(false)
  val isBrowserFullscreen: StateFlow<Boolean> = _isBrowserFullscreen.asStateFlow()

  private val _contextMenuTarget = MutableStateFlow<WebContextMenuTarget?>(null)
  val contextMenuTarget: StateFlow<WebContextMenuTarget?> = _contextMenuTarget.asStateFlow()

  private val _toastMessage = MutableStateFlow<String?>(null)
  val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

  private val _pendingSitePermissionPrompt = MutableStateFlow<SitePermissionPromptState?>(null)
  val pendingSitePermissionPrompt: StateFlow<SitePermissionPromptState?> =
    _pendingSitePermissionPrompt.asStateFlow()

  private val _pendingRuntimePermissionRequest =
    MutableStateFlow<RuntimePermissionRequestState?>(null)
  val pendingRuntimePermissionRequest: StateFlow<RuntimePermissionRequestState?> =
    _pendingRuntimePermissionRequest.asStateFlow()

  val mediaPlayerState: StateFlow<MediaPlayerUiState> = mediaManager.uiState
  val addons: StateFlow<List<FirefoxAddonInfo>> = firefoxAddonManager.addons
  val isAddonInstalling: StateFlow<Boolean> = firefoxAddonManager.isInstalling
  val pendingAddonInstallPrompt: StateFlow<AddonInstallPromptState?> =
    firefoxAddonManager.pendingInstallPrompt
  val activeAddonPopup: StateFlow<AddonPopupState?> = firefoxAddonManager.activeAddonPopup

  private val isGeckoActive: Boolean
    get() = settings.value.engineMode == BrowserEngineMode.GECKO

  private val vpnCoordinator =
    VpnProxyCoordinator(
      app = app,
      repository = repository,
      speedEngine = speedEngine,
      scope = viewModelScope,
      settingsFlow = settings,
      activeTabProvider = { activeTab.value },
      onReloadActiveTab = { reloadTabById(activeTab.value.id) },
      onStopAllWebViewLoads = {
        webViewPool.stopAllLoads()
        geckoTabPool.stopAllLoads()
      },
      onKillSwitchBlockingChanged = { blocked ->
        webViewPool.isKillSwitchBlocking = blocked
        geckoTabPool.isKillSwitchBlocking = blocked
        syncGeckoBridgeState()
      },
      onShowMessage = ::showMessage,
    )
  val vpnState: StateFlow<VpnTunnelState> = vpnCoordinator.vpnState

  private val mediaSnifferCoordinator =
    MediaSnifferCoordinator(
      speedEngine = speedEngine,
      scope = viewModelScope,
      tabProvider = { tabId -> _tabs.value.firstOrNull { it.id == tabId } },
      updateTab = ::updateTab,
      onShowMessage = ::showMessage,
    )

  private val _sessionDataUsedBytes = MutableStateFlow(0L)
  val sessionDataUsedBytes: StateFlow<Long> = _sessionDataUsedBytes.asStateFlow()

  private val _socialSidebarOpen = MutableStateFlow(false)
  val socialSidebarOpen: StateFlow<Boolean> = _socialSidebarOpen.asStateFlow()

  private val _socialApps = MutableStateFlow<List<SocialAppShortcut>>(emptyList())
  val socialApps: StateFlow<List<SocialAppShortcut>> = _socialApps.asStateFlow()

  private val _hasSystemAutofill = MutableStateFlow(false)
  val hasSystemAutofill: StateFlow<Boolean> = _hasSystemAutofill.asStateFlow()

  val tabWebCallbacks: TabWebCallbacks =
    object : TabWebCallbacks {
      override fun onPageStarted(tabId: String, url: String) {
        mediaSnifferCoordinator.resetTab(tabId)
        val isBypassed = WebViewTabPool.isHostMediaCompatBypassed(url)
        val isAdBlockPaused = WebViewTabPool.isHostAdBlockPaused(url)
        val siteOverride = webViewPool.getSiteOverrideForUrl(url)
        updateTab(tabId) {
          it.copy(
            url = url,
            isLoading = true,
            progress = 10,
            isSpeedDial = false,
            isSecure = url.startsWith("https://", ignoreCase = true),
            isMediaCompatBypassEnabled = isBypassed,
            isAdBlockPausedForSite = isAdBlockPaused,
            siteJsEnabled = siteOverride?.jsEnabled ?: true,
            siteDesktopOverride = siteOverride?.desktopMode,
            trackersBlockedOnPage = 0,
            bytesSavedOnPage = 0L,
            detectedMedia = emptyList(),
            isScanningMedia = false,
            httpsWarning = null,
          )
        }
      }

      override fun onPageFinished(
        tabId: String,
        url: String,
        title: String,
        loadTimeMs: Long,
        canGoBack: Boolean,
        canGoForward: Boolean,
      ) {
        var blockedCount = 0
        var bytesSaved = 0L
        val isBypassed = WebViewTabPool.isHostMediaCompatBypassed(url)
        val isAdBlockPaused = WebViewTabPool.isHostAdBlockPaused(url)
        val siteOverride = webViewPool.getSiteOverrideForUrl(url)

        updateTab(tabId) { tab ->
          blockedCount = tab.trackersBlockedOnPage
          bytesSaved = tab.bytesSavedOnPage
          tab.copy(
            url = url,
            title = title,
            isLoading = false,
            progress = 100,
            lastLoadTimeMs = loadTimeMs,
            canGoBack = canGoBack,
            canGoForward = canGoForward,
            isSpeedDial = false,
            isSecure = url.startsWith("https://", ignoreCase = true),
            isMediaCompatBypassEnabled = isBypassed,
            isAdBlockPausedForSite = isAdBlockPaused,
            siteJsEnabled = siteOverride?.jsEnabled ?: true,
            siteDesktopOverride = siteOverride?.desktopMode,
          )
        }

        viewModelScope.launch {
          repository.preferences.recordTelemetry(blockedCount, bytesSaved, loadTimeMs)
        }
      }

      override fun onProgressChanged(tabId: String, progress: Int) {
        updateTab(tabId) { tab ->
          tab.copy(progress = progress, isLoading = progress in 1..99)
        }
      }

      override fun onTitleReceived(tabId: String, title: String) {
        updateTab(tabId) { it.copy(title = title) }
      }

      override fun onTrackerBlocked(tabId: String, bytesSaved: Long) {
        updateTab(tabId) {
          it.copy(
            trackersBlockedOnPage = it.trackersBlockedOnPage + 1,
            bytesSavedOnPage = it.bytesSavedOnPage + bytesSaved,
          )
        }
      }

      override fun onMediaDetected(tabId: String, items: List<DetectedMediaItem>) {
        mediaSnifferCoordinator.mergeAndProbeMediaItems(tabId, items)
      }

      override fun onDownloadRequested(
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimeType: String,
      ) {
        downloadMedia(
          url = url,
          fileName = null,
          mimeType = mimeType,
          contentDisposition = contentDisposition,
        )
      }

      override fun onOpenNewTabRequested(url: String) {
        newTab(isIncognito = activeTab.value.isIncognito, initialUrl = url)
      }

      override fun onCreatePopupTabRequested(isIncognito: Boolean): String? {
        val popupTab =
          BrowserTabState(
            url = "",
            title = if (isIncognito) "Private Tab" else "New Tab",
            isIncognito = isIncognito,
            isSpeedDial = false,
            isLoading = true,
            progress = 10,
          )
        _tabs.update { it + popupTab }
        _activeTabId.value = popupTab.id
        _activeSheet.value = BrowserSheet.NONE
        return popupTab.id
      }

      override fun onHttpsUpgradeFailed(
        tabId: String,
        failedHttpsUrl: String,
        fallbackHttpUrl: String,
        reason: String,
      ) {
        updateTab(tabId) {
          it.copy(
            isLoading = false,
            progress = 0,
            isSpeedDial = false,
            httpsWarning =
              HttpsWarningState(
                failedUrl = failedHttpsUrl,
                fallbackHttpUrl = fallbackHttpUrl,
                reason = reason,
                isPublicProxyWarning = false,
              ),
          )
        }
      }

      override fun onPublicProxyHttpWarning(tabId: String, httpUrl: String) {
        updateTab(tabId) {
          it.copy(
            url = httpUrl,
            isLoading = false,
            progress = 0,
            isSpeedDial = false,
            isSecure = false,
            httpsWarning =
              HttpsWarningState(
                failedUrl = WebViewTabPool.upgradeHttpToHttps(httpUrl),
                fallbackHttpUrl = httpUrl,
                reason =
                  "Plain HTTP traffic over a free public proxy can be read or modified by the relay operator.",
                isPublicProxyWarning = true,
              ),
          )
        }
      }

      override fun onEnsureRuntimePermissions(
        types: Set<SitePermissionType>,
        onResult: (Boolean) -> Unit,
      ) {
        viewModelScope.launch(Dispatchers.Main) {
          val missing = mutableListOf<String>()
          if (types.contains(SitePermissionType.CAMERA) &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.CAMERA) !=
              PackageManager.PERMISSION_GRANTED
          ) {
            missing.add(Manifest.permission.CAMERA)
          }
          if (types.contains(SitePermissionType.MICROPHONE) &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) !=
              PackageManager.PERMISSION_GRANTED
          ) {
            missing.add(Manifest.permission.RECORD_AUDIO)
          }
          if (types.contains(SitePermissionType.LOCATION)) {
            val hasFine =
              ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
            val hasCoarse =
              ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
            if (!hasFine && !hasCoarse) {
              missing.add(Manifest.permission.ACCESS_FINE_LOCATION)
              missing.add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
          }
          if (missing.isEmpty()) {
            onResult(true)
          } else {
            _pendingRuntimePermissionRequest.value?.onResult?.invoke(false)
            _pendingRuntimePermissionRequest.value = RuntimePermissionRequestState(missing, onResult)
          }
        }
      }

      override fun onSitePermissionRequested(
        tabId: String,
        origin: String,
        host: String,
        isIncognito: Boolean,
        requestedTypes: Set<SitePermissionType>,
        onDecision: (granted: Boolean, rememberForSite: Boolean) -> Unit,
      ) {
        viewModelScope.launch(Dispatchers.Main) {
          _pendingSitePermissionPrompt.value?.onDecision?.invoke(false, false)
          _pendingSitePermissionPrompt.value =
            SitePermissionPromptState(
              tabId = tabId,
              origin = origin,
              host = host,
              isIncognito = isIncognito,
              requestedTypes = requestedTypes,
              onDecision = onDecision,
            )
        }
      }

      override fun onSitePermissionCanceled(tabId: String) {
        viewModelScope.launch(Dispatchers.Main) {
          if (_pendingSitePermissionPrompt.value?.tabId == tabId) {
            _pendingSitePermissionPrompt.value = null
          }
        }
      }

      override fun onPersistSitePermission(host: String, type: SitePermissionType, granted: Boolean) {
        viewModelScope.launch { repository.saveSitePermission(host, type, granted) }
      }

      override fun onShowFullscreenView(view: View, callback: WebChromeClient.CustomViewCallback) {
        _fullscreenCustomView.value = view to callback
      }

      override fun onHideFullscreenView() {
        _fullscreenCustomView.value?.second?.onCustomViewHidden()
        _fullscreenCustomView.value = null
      }

      override fun onMainFrameNetworkError(tabId: String, errorCode: Int, description: String) {
        val isProxyOrNetworkError =
          errorCode == WebViewClient.ERROR_PROXY_AUTHENTICATION ||
            errorCode == WebViewClient.ERROR_CONNECT ||
            errorCode == WebViewClient.ERROR_TIMEOUT ||
            errorCode == WebViewClient.ERROR_HOST_LOOKUP ||
            errorCode == WebViewClient.ERROR_IO ||
            errorCode == WebViewClient.ERROR_FAILED_SSL_HANDSHAKE
        vpnCoordinator.handleMainFrameNetworkError(tabId, isProxyOrNetworkError)
      }

      override fun onContextMenuRequested(tabId: String, target: WebContextMenuTarget) {
        if (tabId == activeTab.value.id) {
          _contextMenuTarget.value = target
        }
      }
    }

  init {
    webViewPool.proxyAuthTargetProvider = { speedEngine.getActiveProxyAuthTarget() }
    webViewPool.proxyCredentialsProvider = { speedEngine.getActiveProxyCredentials() }
    webViewPool.isPublicRelayActiveProvider = { vpnState.value.activeRelay?.isPublicFreeRelay == true }
    geckoTabPool.isPublicRelayActiveProvider = { vpnState.value.activeRelay?.isPublicFreeRelay == true }
    geckoTabPool.onXpiIntercepted = { xpiUrl ->
      firefoxAddonManager.installFromUrl(xpiUrl, settings.value)
    }
    geckoTabPool.onFullscreenChanged = { fs -> _isBrowserFullscreen.value = fs }
    refreshSocialAppStates()
    val baselineUidBytes = readAppUidTotalBytes()
    viewModelScope.launch {
      while (isActive) {
        val currentUidBytes = readAppUidTotalBytes()
        val trafficDelta =
          if (baselineUidBytes != null && currentUidBytes != null) {
            (currentUidBytes - baselineUidBytes).coerceAtLeast(0L)
          } else {
            0L
          }
        val dlBytes = downloads.value.sumOf { it.downloadedBytes.coerceAtLeast(0L) }
        _sessionDataUsedBytes.value = maxOf(trafficDelta, dlBytes)
        delay(2500L)
      }
    }
    viewModelScope.launch {
      withContext(Dispatchers.IO) {
        repository.initializeCleanSession()
        repository.preferences.ensureStartupVpnOffDefault()
      }
      val initialSettings = repository.settings.first()
      vpnCoordinator.initializeOnStartup(initialSettings)
      delay(300L)
      if (_tabs.value.all { it.isSpeedDial }) {
        webViewPool.wipeAllCacheAndCookies()
      }
    }
    viewModelScope.launch {
      settings.collect { currentSettings ->
        webViewPool.refreshSettings(currentSettings)
        geckoTabPool.refreshSettings(currentSettings)
        vpnCoordinator.syncSavedConfigs(currentSettings)
        syncGeckoBridgeState()
      }
    }
    viewModelScope.launch {
      vpnState.collect { syncGeckoBridgeState() }
    }
    viewModelScope.launch {
      repository.sitePermissions.collect { entities ->
        webViewPool.syncPersistedSitePermissions(entities)
        geckoTabPool.syncPersistedSitePermissions(entities)
      }
    }
    viewModelScope.launch {
      repository.siteSettings.collect { entities ->
        webViewPool.syncSiteHostOverrides(entities)
        geckoTabPool.syncSiteHostOverrides(entities)
        val overridesByHost = entities.associateBy { it.host }
        _tabs.update { list ->
          list.map { tab ->
            if (tab.isSpeedDial || tab.url.isBlank()) {
              tab
            } else {
              val host = WebViewTabPool.normalizeHostKey(tab.url)
              val override = overridesByHost[host]
              tab.copy(
                siteJsEnabled = override?.jsEnabled ?: true,
                siteDesktopOverride = override?.desktopMode,
              )
            }
          }
        }
      }
    }
    mediaManager.connect()
  }

  private fun syncGeckoBridgeState() {
    val auth = speedEngine.getActiveProxyAuthTarget()
    val relay = vpnState.value.activeRelay
    val proxyHost = auth?.host ?: relay?.ip.orEmpty()
    val proxyPort = auth?.port ?: relay?.port ?: 0
    val proxyType = if (auth != null) "http" else relay?.protocol?.lowercase().orEmpty()
    firefoxAddonManager.syncBridgeState(
      killSwitchBlocking = geckoTabPool.isKillSwitchBlocking,
      liteModeEnabled = settings.value.liteModeEnabled,
      proxyType = proxyType,
      proxyHost = proxyHost,
      proxyPort = proxyPort,
      proxyUsername = auth?.username ?: relay?.username.orEmpty(),
      proxyPassword = auth?.password ?: relay?.password.orEmpty(),
      bypassedHosts = WebViewTabPool.getMediaCompatBypassedHosts(),
    )
  }

  private fun reloadTabById(tabId: String) {
    if (isGeckoActive) {
      geckoTabPool.findSession(tabId)?.reload()
    } else {
      webViewPool.findWebView(tabId)?.reload()
    }
  }

  private fun loadUrlInActiveEngine(tabId: String, isIncognito: Boolean, url: String) {
    if (isGeckoActive) {
      val session = geckoTabPool.getOrCreateSession(tabId, isIncognito, tabWebCallbacks)
      session.loadUri(url)
    } else {
      val wv = webViewPool.getOrCreateWebView(tabId, isIncognito, tabWebCallbacks)
      wv.loadUrl(url)
    }
  }

  fun navigate(rawInput: String, overrideEngine: SearchEngine? = null) {
    val trimmed = rawInput.trim()
    if (trimmed.isEmpty()) return
    val engine = overrideEngine ?: settings.value.searchEngine
    val targetUrl = VoltSpeedEngine.normalizeInputToUrl(trimmed, engine) ?: return
    val current = activeTab.value
    when (val decision = webViewPool.resolveOutgoingNavigation(current.id, targetUrl)) {
      WebViewTabPool.OutgoingNavigationDecision.BlockedByKillSwitch -> {
        updateTab(current.id) { it.copy(isLoading = false, progress = 0) }
        showMessage("Blocked by VPN Kill Switch — reconnect WireGuard or turn off Kill Switch")
        _activeSheet.value = BrowserSheet.NONE
      }
      is WebViewTabPool.OutgoingNavigationDecision.WarnPublicProxyHttp -> {
        tabWebCallbacks.onPublicProxyHttpWarning(current.id, decision.httpUrl)
        _activeSheet.value = BrowserSheet.NONE
      }
      is WebViewTabPool.OutgoingNavigationDecision.LoadUrl -> {
        val resolvedUrl = decision.url
        if (isGeckoActive && FirefoxAddonManager.isXpiUrl(resolvedUrl)) {
          firefoxAddonManager.installFromUrl(resolvedUrl, settings.value)
          _activeSheet.value = BrowserSheet.NONE
          return
        }
        speedEngine.prewarmOrigin(resolvedUrl)
        updateTab(current.id) {
          it.copy(
            url = resolvedUrl,
            isSpeedDial = false,
            isLoading = true,
            progress = 5,
            isSecure = resolvedUrl.startsWith("https://", ignoreCase = true),
            isMediaCompatBypassEnabled = WebViewTabPool.isHostMediaCompatBypassed(resolvedUrl),
            isAdBlockPausedForSite = WebViewTabPool.isHostAdBlockPaused(resolvedUrl),
            httpsWarning = null,
          )
        }
        loadUrlInActiveEngine(current.id, current.isIncognito, resolvedUrl)
        _activeSheet.value = BrowserSheet.NONE
      }
    }
  }

  fun hardReloadActiveTab() {
    val current = activeTab.value
    if (current.isSpeedDial) return
    if (isGeckoActive) {
      geckoTabPool.findSession(current.id)?.reload(org.mozilla.geckoview.GeckoSession.LOAD_FLAGS_BYPASS_CACHE)
    } else {
      val wv = webViewPool.findWebView(current.id) ?: return
      wv.clearCache(true)
      wv.reload()
    }
    showMessage("Hard reload (cache bypassed)")
  }

  fun toggleCurrentSiteMediaCompatBypass() {
    val current = activeTab.value
    if (current.isSpeedDial || current.url.isBlank()) {
      showMessage("Open a website first to toggle Site Media Bypass")
      return
    }
    val host = WebViewTabPool.normalizeHostKey(current.url)
    if (host.isEmpty()) return
    val nextEnabled = !WebViewTabPool.isHostMediaCompatBypassed(current.url)
    WebViewTabPool.setHostMediaCompatBypass(current.url, nextEnabled)
    syncGeckoBridgeState()
    updateTab(current.id) { it.copy(isMediaCompatBypassEnabled = nextEnabled) }
    webViewPool.findWebView(current.id)?.let { wv ->
      wv.clearCache(true)
      wv.reload()
    }
    if (isGeckoActive) {
      geckoTabPool.findSession(current.id)?.reload(org.mozilla.geckoview.GeckoSession.LOAD_FLAGS_BYPASS_CACHE)
    }
    showMessage(
      if (nextEnabled) "Media Compatibility Bypass ON for $host"
      else "Standard optimization restored for $host"
    )
  }

  /** Toggles per-site JavaScript execution for the active tab's host and reloads. */
  fun toggleCurrentSiteJs() {
    val current = activeTab.value
    if (current.isSpeedDial || current.url.isBlank()) {
      showMessage("Open a website first to change site JavaScript setting")
      return
    }
    val host = WebViewTabPool.normalizeHostKey(current.url)
    if (host.isEmpty()) return
    val nextJs = !current.siteJsEnabled
    val persistedJs: Boolean? = if (nextJs) null else false
    updateTab(current.id) { it.copy(siteJsEnabled = nextJs) }
    viewModelScope.launch {
      repository.saveSiteJsOverride(host, persistedJs)
      reloadTabById(current.id)
    }
    showMessage(
      if (nextJs) "JavaScript enabled for $host" else "JavaScript blocked for $host"
    )
  }

  /** Toggles per-site Desktop Mode for the active tab's host and reloads. */
  fun toggleCurrentSiteDesktop() {
    val current = activeTab.value
    if (current.isSpeedDial || current.url.isBlank()) {
      showMessage("Open a website first to change site Desktop Mode")
      return
    }
    val host = WebViewTabPool.normalizeHostKey(current.url)
    if (host.isEmpty()) return
    val currentlyDesktop = current.siteDesktopOverride ?: settings.value.desktopModeEnabled
    val nextDesktop = !currentlyDesktop
    val overrideValue: Boolean? =
      if (nextDesktop == settings.value.desktopModeEnabled) null else nextDesktop
    updateTab(current.id) { it.copy(siteDesktopOverride = overrideValue) }
    viewModelScope.launch {
      repository.saveSiteDesktopOverride(host, overrideValue)
      reloadTabById(current.id)
    }
    showMessage(
      if (nextDesktop) "Desktop view enabled for $host" else "Mobile view enabled for $host"
    )
  }

  fun toggleCurrentSiteAdBlockPause() {
    val current = activeTab.value
    if (current.isSpeedDial || current.url.isBlank()) {
      showMessage("Open a website first to pause Ad Remover for a site")
      return
    }
    val host = WebViewTabPool.normalizeHostKey(current.url)
    if (host.isEmpty()) return
    val nextPaused = !WebViewTabPool.isHostAdBlockPaused(current.url)
    WebViewTabPool.setHostAdBlockPaused(current.url, nextPaused)
    webViewPool.syncAdBlockerScripts()
    updateTab(current.id) { it.copy(isAdBlockPausedForSite = nextPaused) }
    webViewPool.findWebView(current.id)?.let { wv ->
      wv.clearCache(true)
      wv.reload()
    }
    if (isGeckoActive) {
      geckoTabPool.findSession(current.id)?.reload()
    }
    showMessage(
      if (nextPaused) "Ad Remover paused on $host"
      else "Ad Remover resumed on $host"
    )
  }

  /** Clears per-site JS, Desktop Mode, Media Bypass, Ad Remover pause, and permission decisions for the active host. */
  fun resetCurrentSiteSettings() {
    val current = activeTab.value
    if (current.isSpeedDial || current.url.isBlank()) return
    val host = WebViewTabPool.normalizeHostKey(current.url)
    if (host.isEmpty()) return
    WebViewTabPool.setHostMediaCompatBypass(current.url, false)
    WebViewTabPool.setHostAdBlockPaused(current.url, false)
    syncGeckoBridgeState()
    webViewPool.syncAdBlockerScripts()
    updateTab(current.id) {
      it.copy(
        isMediaCompatBypassEnabled = false,
        isAdBlockPausedForSite = false,
        siteJsEnabled = true,
        siteDesktopOverride = null,
      )
    }
    viewModelScope.launch {
      repository.clearSiteSettingsForHost(host)
      repository.clearSitePermissionsForHost(host)
      reloadTabById(current.id)
    }
    showMessage("Site settings & permissions reset for $host")
  }

  @Suppress("UNUSED_PARAMETER")
  fun onOmniboxQueryChanged(query: String) {
    // Intentionally does not fire speculative DNS/HTTP requests per keystroke for zero-leak privacy.
  }

  fun goBack(): Boolean {
    val current = activeTab.value
    if (current.isSpeedDial) return false
    if (isGeckoActive) {
      val session = geckoTabPool.findSession(current.id)
      return if (session != null && geckoTabPool.canGoBack(current.id)) {
        session.goBack()
        true
      } else {
        goHome()
        true
      }
    }
    val wv = webViewPool.findWebView(current.id)
    return if (wv != null && wv.canGoBack()) {
      wv.goBack()
      true
    } else {
      goHome()
      true
    }
  }

  fun goForward() {
    val current = activeTab.value
    if (isGeckoActive) {
      val session = geckoTabPool.findSession(current.id)
      if (current.isSpeedDial && session != null && current.url.isNotBlank()) {
        updateTab(current.id) { it.copy(isSpeedDial = false) }
      } else if (session != null && geckoTabPool.canGoForward(current.id)) {
        session.goForward()
      }
      return
    }
    val wv = webViewPool.findWebView(current.id)
    if (current.isSpeedDial && wv != null && current.url.isNotBlank()) {
      updateTab(current.id) { it.copy(isSpeedDial = false) }
    } else if (wv != null && wv.canGoForward()) {
      wv.goForward()
    }
  }

  fun reloadOrStop() {
    val current = activeTab.value
    if (current.isSpeedDial) return
    if (isGeckoActive) {
      val session = geckoTabPool.findSession(current.id) ?: return
      if (current.isLoading) {
        session.stop()
        updateTab(current.id) { it.copy(isLoading = false, progress = 0) }
      } else {
        session.reload()
      }
      return
    }
    val wv = webViewPool.findWebView(current.id) ?: return
    if (current.isLoading) {
      wv.stopLoading()
      updateTab(current.id) { it.copy(isLoading = false, progress = 0) }
    } else {
      wv.reload()
    }
  }

  fun goHome() {
    val current = activeTab.value
    updateTab(current.id) {
      it.copy(isSpeedDial = true, title = "Speed Dial", isLoading = false, progress = 0)
    }
    _isBrowserFullscreen.value = false
    _activeSheet.value = BrowserSheet.NONE
  }

  fun newTab(isIncognito: Boolean = false, initialUrl: String = "") {
    val resolvedInputUrl =
      if (initialUrl.isNotBlank()) {
        VoltSpeedEngine.normalizeInputToUrl(initialUrl, settings.value.searchEngine).orEmpty()
      } else {
        ""
      }
    val newTab =
      BrowserTabState(
        url = resolvedInputUrl,
        title = if (resolvedInputUrl.isBlank()) (if (isIncognito) "Private Dial" else "Speed Dial") else resolvedInputUrl,
        isIncognito = isIncognito,
        isSpeedDial = resolvedInputUrl.isBlank(),
      )
    _tabs.update { it + newTab }
    _activeTabId.value = newTab.id
    _activeSheet.value = BrowserSheet.NONE
    if (resolvedInputUrl.isNotBlank()) {
      when (val decision = webViewPool.resolveOutgoingNavigation(newTab.id, resolvedInputUrl)) {
        WebViewTabPool.OutgoingNavigationDecision.BlockedByKillSwitch -> {
          showMessage("Blocked by VPN Kill Switch — reconnect WireGuard or turn off Kill Switch")
        }
        is WebViewTabPool.OutgoingNavigationDecision.WarnPublicProxyHttp -> {
          tabWebCallbacks.onPublicProxyHttpWarning(newTab.id, decision.httpUrl)
        }
        is WebViewTabPool.OutgoingNavigationDecision.LoadUrl -> {
          val loadTarget = decision.url
          updateTab(newTab.id) {
            it.copy(
              url = loadTarget,
              isLoading = true,
              progress = 5,
              isSecure = loadTarget.startsWith("https://", ignoreCase = true),
            )
          }
          speedEngine.prewarmOrigin(loadTarget)
          loadUrlInActiveEngine(newTab.id, isIncognito, loadTarget)
        }
      }
    }
  }

  fun switchToNextTab() {
    val list = _tabs.value
    if (list.size <= 1) return
    val currentIndex = list.indexOfFirst { it.id == _activeTabId.value }
    if (currentIndex == -1) return
    _activeTabId.value = list[(currentIndex + 1) % list.size].id
    _activeSheet.value = BrowserSheet.NONE
  }

  fun switchToPreviousTab() {
    val list = _tabs.value
    if (list.size <= 1) return
    val currentIndex = list.indexOfFirst { it.id == _activeTabId.value }
    if (currentIndex == -1) return
    _activeTabId.value = list[(currentIndex - 1 + list.size) % list.size].id
    _activeSheet.value = BrowserSheet.NONE
  }

  fun selectTab(tabId: String) {
    if (_tabs.value.any { it.id == tabId }) {
      _activeTabId.value = tabId
    }
    _activeSheet.value = BrowserSheet.NONE
  }

  fun closeTab(tabId: String) {
    val currentList = _tabs.value
    if (currentList.size <= 1) {
      webViewPool.destroyTab(tabId)
      geckoTabPool.destroyTab(tabId)
      mediaSnifferCoordinator.resetTab(tabId)
      val fresh = BrowserTabState(title = "Speed Dial", isSpeedDial = true)
      _tabs.value = listOf(fresh)
      _activeTabId.value = fresh.id
      return
    }
    val index = currentList.indexOfFirst { it.id == tabId }
    val remaining = currentList.filterNot { it.id == tabId }
    webViewPool.destroyTab(tabId)
    geckoTabPool.destroyTab(tabId)
    mediaSnifferCoordinator.resetTab(tabId)
    _tabs.value = remaining
    if (_activeTabId.value == tabId) {
      val nextIndex = (index - 1).coerceIn(0, remaining.lastIndex)
      _activeTabId.value = remaining[nextIndex].id
    }
  }

  fun closeAllTabs() {
    val currentIds = _tabs.value.map { it.id }
    currentIds.forEach { id ->
      webViewPool.destroyTab(id)
      geckoTabPool.destroyTab(id)
      mediaSnifferCoordinator.resetTab(id)
    }
    val fresh = BrowserTabState(title = "Speed Dial", isSpeedDial = true)
    _tabs.value = listOf(fresh)
    _activeTabId.value = fresh.id
    _activeSheet.value = BrowserSheet.NONE
    showMessage("All tabs closed")
  }

  fun openSheet(sheet: BrowserSheet) {
    if (sheet == BrowserSheet.ADDONS) {
      firefoxAddonManager.refreshAddons(settings.value)
    }
    _activeSheet.value = sheet
  }

  fun closeSheet() {
    _activeSheet.value = BrowserSheet.NONE
  }

  fun toggleFindInPage(show: Boolean? = null) {
    val next = show ?: !_findInPageVisible.value
    _findInPageVisible.value = next
    if (!next) {
      webViewPool.findWebView(activeTab.value.id)?.clearMatches()
      geckoTabPool.clearFindMatches(activeTab.value.id)
    }
  }

  fun findInPage(query: String, forward: Boolean = true) {
    if (isGeckoActive) {
      geckoTabPool.findInPage(activeTab.value.id, query, forward)
      return
    }
    val wv = webViewPool.findWebView(activeTab.value.id) ?: return
    if (query.isBlank()) {
      wv.clearMatches()
    } else {
      wv.findAllAsync(query)
      wv.findNext(forward)
    }
  }

  fun exitHtmlFullscreen() {
    _fullscreenCustomView.value?.second?.onCustomViewHidden()
    _fullscreenCustomView.value = null
  }

  fun toggleBrowserFullscreen(enabled: Boolean? = null) {
    val next = enabled ?: !_isBrowserFullscreen.value
    _isBrowserFullscreen.value = next
    _activeSheet.value = BrowserSheet.NONE
  }

  fun showContextMenu(target: WebContextMenuTarget) {
    _contextMenuTarget.value = target
  }

  fun dismissContextMenu() {
    _contextMenuTarget.value = null
  }

  fun playMediaUrlInternally(
    url: String,
    title: String?,
    kind: MediaKind?,
    pageUrl: String,
  ) {
    _contextMenuTarget.value = null
    _activeSheet.value = BrowserSheet.NONE
    val resolvedKind =
      kind
        ?: MediaSniffer.classifyUrl(url, pageUrl, title.orEmpty())?.kind
        ?: MediaKind.VIDEO
    val resolvedMime =
      when (resolvedKind) {
        MediaKind.AUDIO -> "audio/*"
        MediaKind.STREAM_HLS -> "application/x-mpegURL"
        MediaKind.STREAM_DASH -> "application/dash+xml"
        else -> "video/mp4"
      }
    val item =
      DetectedMediaItem(
        url = url,
        title = title?.takeIf { it.isNotBlank() } ?: url.substringAfterLast('/').substringBefore('?').ifBlank { "Web Media" },
        kind = resolvedKind,
        mimeType = resolvedMime,
        sourcePageUrl = pageUrl,
      )
    mediaManager.playMedia(item)
  }

  fun openMediaUrlExternally(
    url: String,
    title: String?,
    kind: MediaKind?,
    pageUrl: String,
  ) {
    _contextMenuTarget.value = null
    val resolvedKind =
      kind
        ?: MediaSniffer.classifyUrl(url, pageUrl, title.orEmpty())?.kind
        ?: MediaKind.VIDEO
    val item =
      DetectedMediaItem(
        url = url,
        title = title?.takeIf { it.isNotBlank() } ?: "Web Media",
        kind = resolvedKind,
        mimeType = if (resolvedKind == MediaKind.AUDIO) "audio/*" else "video/*",
        sourcePageUrl = pageUrl,
      )
    openInExternalPlayer(item)
  }

  fun shareUrl(url: String, title: String? = null) {
    _contextMenuTarget.value = null
    val trimmed = url.trim()
    if (trimmed.isBlank()) return
    val intent =
      Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        if (!title.isNullOrBlank()) putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, trimmed)
      }
    val chooser =
      Intent.createChooser(intent, "Share link").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    runCatching { app.startActivity(chooser) }
  }

  fun playDetectedMedia(item: DetectedMediaItem) {
    _activeSheet.value = BrowserSheet.NONE
    mediaManager.playMedia(item)
  }

  fun playLocalMediaUri(uri: Uri, title: String, isVideo: Boolean) {
    _activeSheet.value = BrowserSheet.NONE
    val item =
      DetectedMediaItem(
        url = uri.toString(),
        title = title,
        kind = if (isVideo) MediaKind.VIDEO else MediaKind.AUDIO,
        mimeType = if (isVideo) "video/*" else "audio/*",
        sourcePageUrl = "Device Storage",
      )
    mediaManager.playMedia(item)
  }

  fun playDownloadedFile(download: DownloadItemEntity) {
    val file = File(download.localPath)
    if (!file.exists()) {
      showMessage("Downloaded file is no longer available")
      return
    }
    val isAudio = download.mimeType.startsWith("audio/")
    val item =
      DetectedMediaItem(
        url = Uri.fromFile(file).toString(),
        title = download.fileName,
        kind = if (isAudio) MediaKind.AUDIO else MediaKind.VIDEO,
        mimeType = download.mimeType,
        sourcePageUrl = download.url,
      )
    _activeSheet.value = BrowserSheet.NONE
    mediaManager.playMedia(item)
  }

  fun openInExternalPlayer(item: DetectedMediaItem) {
    if (mediaPlayerState.value.isPlaying) {
      mediaManager.togglePlayPause()
    }
    val rawUrl = item.url.trim()
    val targetUri: Uri
    var grantRead = false
    if (rawUrl.startsWith("file://")) {
      val path = rawUrl.toUri().path
      val file = path?.let { File(it) }
      if (file == null || !file.exists()) {
        showMessage("Local file is no longer available")
        return
      }
      targetUri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
      grantRead = true
    } else {
      targetUri = rawUrl.toUri()
      grantRead = rawUrl.startsWith("content://")
    }

    val mime =
      when {
        item.kind == MediaKind.AUDIO -> "audio/*"
        item.kind == MediaKind.STREAM_HLS -> "application/x-mpegURL"
        item.kind == MediaKind.STREAM_DASH -> "application/dash+xml"
        item.mimeType.isNotBlank() && item.mimeType != "application/octet-stream" -> item.mimeType
        else -> "video/*"
      }

    launchExternalPlayerIntent(targetUri, mime, item.title, grantRead)
  }

  fun openDownloadInExternalPlayer(download: DownloadItemEntity) {
    val file = File(download.localPath)
    if (!file.exists()) {
      showMessage("Downloaded file is no longer available")
      return
    }
    val contentUri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
    val mime =
      when {
        download.mimeType.startsWith("audio/") -> download.mimeType
        download.mimeType.startsWith("video/") -> download.mimeType
        download.fileName.endsWith(".mp3", true) || download.fileName.endsWith(".m4a", true) -> "audio/*"
        else -> "video/*"
      }
    launchExternalPlayerIntent(contentUri, mime, download.fileName, grantReadPermission = true)
  }

  fun openDownloadedFile(download: DownloadItemEntity) {
    val file = File(download.localPath)
    if (!file.exists()) {
      showMessage("Downloaded file is no longer available")
      return
    }
    if (!mediaDownloader.openDownloadedFile(download)) {
      showMessage("No installed app found to open ${download.fileName}")
    }
  }

  fun openDirectUrlInExternalPlayer(rawUrl: String) {
    val trimmed = rawUrl.trim()
    if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
      showMessage("Enter a valid http:// or https:// media link")
      return
    }
    val item =
      MediaSniffer.classifyUrl(trimmed, "Direct URL", "External Stream")
        ?: DetectedMediaItem(
          url = trimmed,
          title = "External Media Stream",
          kind = MediaKind.VIDEO,
          mimeType = "video/*",
          sourcePageUrl = "Direct URL",
        )
    openInExternalPlayer(item)
  }

  private fun launchExternalPlayerIntent(
    uri: Uri,
    mimeType: String,
    title: String,
    grantReadPermission: Boolean,
  ) {
    val primaryIntent =
      Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        if (grantReadPermission) addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
    val fallbackMime = if (mimeType.startsWith("audio/")) "audio/*" else "video/*"
    val fallbackIntent =
      Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, fallbackMime)
        if (grantReadPermission) addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }

    val pm = app.packageManager
    val resolvedIntent =
      when {
        primaryIntent.resolveActivity(pm) != null -> primaryIntent
        fallbackIntent.resolveActivity(pm) != null -> fallbackIntent
        else -> primaryIntent
      }

    try {
      val chooser =
        Intent.createChooser(resolvedIntent, "Play \"$title\" with").apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          if (grantReadPermission) addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
      app.startActivity(chooser)
    } catch (_: ActivityNotFoundException) {
      showMessage("No external video/audio player app found on device")
    } catch (_: Exception) {
      showMessage("Unable to open external player for this link")
    }
  }

  fun rescanActivePageMedia() {
    val tab = activeTab.value
    if (tab.isSpeedDial || tab.url.isBlank()) {
      showMessage("Open a webpage first, or paste a playlist/video URL below")
      return
    }
    mediaSnifferCoordinator.resetTab(tab.id)
    if (isGeckoActive) {
      firefoxAddonManager.requestMediaRescan()
    } else {
      webViewPool.triggerMediaScan(tab.id)
    }
    requeryAllMediaSizes()
    showMessage("Scanning page for downloadable videos (> 1 MB)...")
  }

  fun resolveMediaOrPlaylistUrl(rawUrl: String) {
    val trimmed = rawUrl.trim()
    if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
      showMessage("Enter a valid http:// or https:// playlist or video URL")
      return
    }
    val tabId = activeTab.value.id
    val classified =
      MediaSniffer.classifyUrl(trimmed, activeTab.value.url.ifBlank { "Direct URL" }, "Playlist / Media")
        ?: DetectedMediaItem(
          url = trimmed,
          title = trimmed.substringAfterLast('/').substringBefore('?').ifBlank { "Resolved Video" },
          kind =
            when {
              trimmed.contains(".m3u8", ignoreCase = true) -> MediaKind.STREAM_HLS
              trimmed.contains(".mpd", ignoreCase = true) -> MediaKind.STREAM_DASH
              else -> MediaKind.PLAYLIST
            },
          mimeType =
            when {
              trimmed.contains(".m3u8", ignoreCase = true) -> "application/x-mpegURL"
              trimmed.contains(".mpd", ignoreCase = true) -> "application/dash+xml"
              else -> "text/plain"
            },
          sourcePageUrl = activeTab.value.url.ifBlank { "Direct Playlist" },
        )
    if (classified.kind == MediaKind.AUDIO) {
      showMessage("Audio-only links are excluded — showing downloadable videos > 1 MB only")
      return
    }
    mediaSnifferCoordinator.mergeAndProbeMediaItems(
      tabId = tabId,
      items = listOf(classified),
      forceReprobe = true,
      isManualResolve = true,
    )
    showMessage("Resolving link & checking downloadable videos (> 1 MB)...")
  }

  fun requeryAllMediaSizes() {
    val tab = activeTab.value
    if (tab.detectedMedia.isEmpty()) return
    mediaSnifferCoordinator.mergeAndProbeMediaItems(tab.id, tab.detectedMedia, forceReprobe = true)
  }

  fun streamDirectMediaUrl(url: String) {
    val trimmed = url.trim()
    if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
      showMessage("Enter a valid http:// or https:// media link")
      return
    }
    val item =
      MediaSniffer.classifyUrl(trimmed, "Direct URL", "Direct Stream")
        ?: DetectedMediaItem(
          url = trimmed,
          title = "Direct Media Stream",
          kind = MediaKind.VIDEO,
          mimeType = "video/mp4",
          sourcePageUrl = "Direct URL",
        )
    _activeSheet.value = BrowserSheet.NONE
    mediaManager.playMedia(item)
  }

  fun downloadMedia(
    url: String,
    fileName: String? = null,
    mimeType: String = "application/octet-stream",
    referer: String? = activeTab.value.url.takeIf { it.startsWith("http") },
    contentDisposition: String? = null,
  ) {
    val trimmed = url.trim()
    val isHttp = trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)
    val isDataUrl = trimmed.startsWith("data:", true)
    if (!isHttp && !isDataUrl) {
      showMessage("Unsupported download link")
      return
    }
    mediaDownloader.enqueueDownload(
      url = trimmed,
      suggestedFileName = fileName,
      mimeType = mimeType,
      referer = referer,
      contentDisposition = contentDisposition,
    )
    showMessage("Downloading — will auto-save to Downloads/VoltBrowser")
  }

  fun cancelDownload(id: Long) {
    mediaDownloader.cancelDownload(id)
    showMessage("Download cancelled")
  }

  /** Resumes a paused/failed HTTP download via HTTP Range when partial bytes exist, or retries cleanly. */
  fun retryDownload(item: DownloadItemEntity) {
    viewModelScope.launch {
      if (item.url.startsWith("data:", ignoreCase = true) && MediaDownloader.parseDataUrl(item.url) == null) {
        showMessage("Blob/data download expired — please trigger it from the webpage again")
        return@launch
      }
      val referer = activeTab.value.url.takeIf { it.startsWith("http") }
      mediaDownloader.resumeOrRetryDownload(item, referer)
      showMessage("Resuming download...")
    }
  }

  fun exportDownloadToDevice(item: DownloadItemEntity) {
    viewModelScope.launch {
      val ok = withContext(Dispatchers.IO) { mediaDownloader.exportToPublicDownloads(item) }
      showMessage(
        if (ok) "Saved to device Downloads/VoltBrowser"
        else "Use Save As to choose a folder on this Android version"
      )
    }
  }

  fun saveDownloadAsUri(item: DownloadItemEntity, destUri: Uri) {
    viewModelScope.launch {
      val ok = withContext(Dispatchers.IO) { mediaDownloader.copyDownloadedFileToUri(item, destUri) }
      showMessage(
        if (ok) "Saved to selected device folder"
        else "Could not save file to selected location"
      )
    }
  }

  fun shareDownload(item: DownloadItemEntity) {
    mediaDownloader.shareDownloadedFile(item)
  }

  fun deleteDownload(item: DownloadItemEntity) {
    mediaDownloader.cancelDownload(item.id)
    viewModelScope.launch {
      runCatching {
        val file = File(item.localPath)
        file.delete()
        File(file.parentFile, "${file.name}.segmeta").delete()
      }
      repository.deleteDownload(item.id)
    }
  }

  fun toggleBookmark() {
    val tab = activeTab.value
    if (tab.url.isBlank() || tab.isSpeedDial) return
    val currentlyBookmarked = isActivePageBookmarked.value
    viewModelScope.launch {
      repository.toggleBookmark(tab.url, tab.title, currentlyBookmarked)
      showMessage(if (currentlyBookmarked) "Bookmark removed" else "Saved to Bookmarks")
    }
  }

  fun addSpeedDial(url: String, title: String) {
    val normalized =
      VoltSpeedEngine.normalizeInputToUrl(url, settings.value.searchEngine, forceUrlOnly = true)
        ?: return
    viewModelScope.launch {
      repository.addSpeedDial(normalized, title.ifBlank { normalized })
      _activeSheet.value = BrowserSheet.NONE
      showMessage("Pinned to Speed Dial")
    }
  }

  fun deleteBookmark(id: Long) {
    viewModelScope.launch { repository.deleteBookmark(id) }
  }

  fun wipeSessionCacheAndCookies() {
    webViewPool.wipeAllCacheAndCookies()
    geckoTabPool.wipeAllSessionData()
    showMessage("Session cache and cookies wiped")
  }

  fun shareCurrentPage() {
    val url = activeTab.value.url
    if (url.isBlank()) return
    val intent =
      Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, activeTab.value.title)
        putExtra(Intent.EXTRA_TEXT, url)
      }
    val chooser =
      Intent.createChooser(intent, "Share link").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    app.startActivity(chooser)
  }

  // ---- Engine Switching & Firefox Add-ons (.xpi) ----

  fun setEngineMode(mode: BrowserEngineMode) {
    if (settings.value.engineMode == mode) return
    viewModelScope.launch {
      repository.preferences.setEngineMode(mode)
      val current = activeTab.value
      if (!current.isSpeedDial && current.url.isNotBlank()) {
        if (mode == BrowserEngineMode.GECKO) {
          val session = geckoTabPool.getOrCreateSession(current.id, current.isIncognito, tabWebCallbacks)
          session.loadUri(current.url)
        } else {
          val wv = webViewPool.getOrCreateWebView(current.id, current.isIncognito, tabWebCallbacks)
          wv.loadUrl(current.url)
        }
      }
      if (mode == BrowserEngineMode.GECKO) {
        firefoxAddonManager.refreshAddons(settings.value.copy(engineMode = mode))
      }
      showMessage("Switched engine to ${mode.displayName}")
    }
  }

  fun refreshAddons() {
    firefoxAddonManager.refreshAddons(settings.value)
  }

  fun installAddonFromUrl(rawUrl: String) {
    val effectiveSettings =
      if (!isGeckoActive) {
        setEngineMode(BrowserEngineMode.GECKO)
        settings.value.copy(engineMode = BrowserEngineMode.GECKO)
      } else {
        settings.value
      }
    firefoxAddonManager.installFromUrl(rawUrl, effectiveSettings)
  }

  fun installAddonFromUri(uri: Uri) {
    val effectiveSettings =
      if (!isGeckoActive) {
        setEngineMode(BrowserEngineMode.GECKO)
        settings.value.copy(engineMode = BrowserEngineMode.GECKO)
      } else {
        settings.value
      }
    firefoxAddonManager.installFromContentUri(uri, effectiveSettings)
  }

  fun openAmoStore() {
    val amoUrl = "https://addons.mozilla.org/en-US/firefox/"
    if (!isGeckoActive) {
      viewModelScope.launch {
        repository.preferences.setEngineMode(BrowserEngineMode.GECKO)
        settings.first { it.engineMode == BrowserEngineMode.GECKO }
        firefoxAddonManager.refreshAddons(settings.value)
        navigate(amoUrl)
      }
    } else {
      navigate(amoUrl)
    }
  }

  fun respondToAddonInstallPrompt(allow: Boolean, allowPrivate: Boolean) {
    firefoxAddonManager.respondToInstallPrompt(allow, allowPrivate)
  }

  fun closeAddonPopup() {
    firefoxAddonManager.closeAddonPopup()
  }

  fun setAddonEnabled(addonId: String, enabled: Boolean) {
    firefoxAddonManager.setAddonEnabled(addonId, enabled)
  }

  fun setAddonAllowedInPrivate(addonId: String, allowed: Boolean) {
    firefoxAddonManager.setAllowedInPrivateBrowsing(addonId, allowed)
  }

  fun openAddonPopup(addonId: String) {
    firefoxAddonManager.openAddonPopup(addonId)
  }

  fun uninstallAddon(addonId: String) {
    firefoxAddonManager.uninstallAddon(addonId)
  }

  fun setAdBlockerType(type: AdBlockerType) {
    viewModelScope.launch {
      repository.preferences.setAdBlockerType(type)
      val current = activeTab.value
      if (!current.isSpeedDial && current.url.isNotBlank()) {
        reloadTabById(current.id)
      }
      showMessage(
        if (type == AdBlockerType.OFF) "Ad Remover turned off"
        else "Ad Remover: ${type.displayName} active"
      )
    }
  }

  fun toggleLiteMode(enabled: Boolean) {
    viewModelScope.launch { repository.preferences.setLiteMode(enabled) }
  }

  fun toggleHttpsOnly(enabled: Boolean) {
    viewModelScope.launch { repository.preferences.setHttpsOnly(enabled) }
  }

  fun clearSitePermissions() {
    viewModelScope.launch {
      repository.clearAllSitePermissions()
      showMessage("Saved site permissions cleared")
    }
  }

  fun proceedWithInsecureHttp(tabId: String, httpUrl: String) {
    webViewPool.allowInsecureHttpHost(httpUrl)
    geckoTabPool.allowInsecureHttpHost(httpUrl)
    updateTab(tabId) {
      it.copy(
        url = httpUrl,
        isSpeedDial = false,
        isLoading = true,
        progress = 10,
        isSecure = false,
        httpsWarning = null,
      )
    }
    val tab = _tabs.value.firstOrNull { it.id == tabId } ?: return
    loadUrlInActiveEngine(tabId, tab.isIncognito, httpUrl)
  }

  fun tryHttpsInstead(tabId: String, httpsUrl: String) {
    updateTab(tabId) {
      it.copy(
        url = httpsUrl,
        isSpeedDial = false,
        isLoading = true,
        progress = 10,
        isSecure = true,
        httpsWarning = null,
      )
    }
    val tab = _tabs.value.firstOrNull { it.id == tabId } ?: return
    loadUrlInActiveEngine(tabId, tab.isIncognito, httpsUrl)
  }

  fun dismissHttpsWarning(tabId: String) {
    updateTab(tabId) { it.copy(httpsWarning = null) }
    if (isGeckoActive) {
      val session = geckoTabPool.findSession(tabId)
      if (session != null && geckoTabPool.canGoBack(tabId)) {
        session.goBack()
      } else {
        updateTab(tabId) {
          it.copy(isSpeedDial = true, title = "Speed Dial", isLoading = false, progress = 0, httpsWarning = null)
        }
      }
      return
    }
    val wv = webViewPool.findWebView(tabId)
    if (wv != null && wv.canGoBack()) {
      wv.goBack()
    } else {
      updateTab(tabId) {
        it.copy(isSpeedDial = true, title = "Speed Dial", isLoading = false, progress = 0, httpsWarning = null)
      }
    }
  }

  fun respondToSitePermissionPrompt(granted: Boolean, rememberForSite: Boolean) {
    val prompt = _pendingSitePermissionPrompt.value ?: return
    _pendingSitePermissionPrompt.value = null
    prompt.onDecision(granted, rememberForSite)
  }

  fun onRuntimePermissionsResult(grantResults: Map<String, Boolean>) {
    val req = _pendingRuntimePermissionRequest.value ?: return
    _pendingRuntimePermissionRequest.value = null
    val needsLocation =
      req.permissions.contains(Manifest.permission.ACCESS_FINE_LOCATION) ||
        req.permissions.contains(Manifest.permission.ACCESS_COARSE_LOCATION)
    val locationGranted =
      if (needsLocation) {
        grantResults[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
          grantResults[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
          ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
          ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
      } else {
        true
      }
    val otherGranted =
      req.permissions
        .filter {
          it != Manifest.permission.ACCESS_FINE_LOCATION &&
            it != Manifest.permission.ACCESS_COARSE_LOCATION
        }
        .all { perm ->
          grantResults[perm] == true ||
            ContextCompat.checkSelfPermission(app, perm) == PackageManager.PERMISSION_GRANTED
        }
    req.onResult(locationGranted && otherGranted)
  }

  fun toggleDarkWeb(enabled: Boolean) {
    viewModelScope.launch { repository.preferences.setDarkWeb(enabled) }
  }

  fun toggleDesktopMode(enabled: Boolean) {
    viewModelScope.launch {
      repository.preferences.setDesktopMode(enabled)
      reloadTabById(activeTab.value.id)
    }
  }

  fun setSearchEngine(engine: SearchEngine) {
    viewModelScope.launch { repository.preferences.setSearchEngine(engine) }
  }

  fun cycleSearchEngine(step: Int) {
    val next = settings.value.searchEngine.cycled(step)
    setSearchEngine(next)
  }

  fun setThemeMode(mode: AppThemeMode) {
    viewModelScope.launch { repository.preferences.setThemeMode(mode) }
  }

  fun showMessage(msg: String) {
    _toastMessage.value = msg
  }

  fun clearMessage() {
    _toastMessage.value = null
  }

  // ---- Delegated VPN / Proxy Operations ----
  fun onVpnSheetOpened() {
    openSheet(BrowserSheet.VPN)
    refreshExitIp()
  }

  fun toggleAutoWireGuardOnLaunch(enabled: Boolean) = vpnCoordinator.toggleAutoWireGuardOnLaunch(enabled)
  fun toggleWireGuardKillSwitch(enabled: Boolean) = vpnCoordinator.toggleWireGuardKillSwitch(enabled)
  fun resetWireGuardKillSwitch() = vpnCoordinator.resetKillSwitch()
  fun markPublicProxyWarned() = vpnCoordinator.markPublicProxyWarned()
  fun toggleAutoProxyOnLaunch(enabled: Boolean) = vpnCoordinator.toggleAutoProxyOnLaunch(enabled)
  fun toggleAutoProxyFailover(enabled: Boolean) = vpnCoordinator.toggleAutoProxyFailover(enabled)
  fun toggleAutoDnsVpnOnLaunch(enabled: Boolean) = vpnCoordinator.toggleAutoDnsVpnOnLaunch(enabled)
  fun setProxyProtocolFilter(filter: ProxyProtocolFilter) = vpnCoordinator.setProxyProtocolFilter(filter)
  fun setProxyCountryFilter(countryCode: String) = vpnCoordinator.setProxyCountryFilter(countryCode)
  fun connectCustomProxy(rawAddress: String, protocol: String) = vpnCoordinator.connectCustomProxy(rawAddress, protocol)
  fun connectCloudflareWarp(silentStartup: Boolean = false) = vpnCoordinator.connectCloudflareWarp(silentStartup)
  fun connectSavedCustomWireGuard() = vpnCoordinator.connectSavedCustomWireGuard()
  fun connectCustomWireGuardConf(rawConf: String, profileName: String = "Custom WireGuard", persistConfig: Boolean = true) =
    vpnCoordinator.connectCustomWireGuardConf(rawConf, profileName, persistConfig)
  fun disconnectWireGuard(silent: Boolean = false) = vpnCoordinator.disconnectWireGuard(silent)
  fun toggleFreeAutoProxy() = vpnCoordinator.toggleFreeAutoProxy()
  fun prepareVpnConsentIntent(): Intent? = vpnCoordinator.prepareVpnConsentIntent()
  fun startDnsVpnService() = vpnCoordinator.startDnsVpnService()
  fun stopDnsVpn(silent: Boolean = false) = vpnCoordinator.stopDnsVpn(silent)
  fun selectDohProvider(provider: DohProvider) = vpnCoordinator.selectDohProvider(provider)
  fun refreshExitIp() = vpnCoordinator.refreshExitIp()
  fun refreshPublicRelays() = vpnCoordinator.refreshPublicRelays()
  fun autoConnectFastestRelay(reloadActiveTab: Boolean = false, fastStartup: Boolean = false) =
    vpnCoordinator.autoConnectFastestRelay(reloadActiveTab, fastStartup)
  fun rotateToNextProxy(reloadActiveTab: Boolean = false) = vpnCoordinator.rotateToNextProxy(reloadActiveTab)
  fun connectRelay(relay: PublicRelayNode, reloadActiveTab: Boolean = false) =
    vpnCoordinator.connectRelay(relay, reloadActiveTab)
  fun disconnectRelay() = vpnCoordinator.disconnectRelay()

  fun exitAndEraseAllData(onComplete: () -> Unit) {
    _activeSheet.value = BrowserSheet.NONE
    _findInPageVisible.value = false
    _isBrowserFullscreen.value = false
    exitHtmlFullscreen()

    mediaDownloader.cancelAllDownloads()
    mediaManager.closePlayer()
    mediaManager.release()
    runCatching { app.stopService(Intent(app, VoltMediaService::class.java)) }

    vpnCoordinator.shutdownAndWipeState()
    firefoxAddonManager.respondToInstallPrompt(allow = false, allowPrivate = false)
    firefoxAddonManager.closeAddonPopup()
    webViewPool.destroyAllTabs()
    geckoTabPool.destroyAllTabs()
    geckoTabPool.wipeAllSessionData()
    WebViewTabPool.clearMediaCompatBypassHosts()
    WebViewTabPool.clearAdBlockPausedHosts()
    MediaSniffer.clearPlaybackContexts()
    DeviceTraceCleaner.wipeWebViewTraces(app)

    mediaSnifferCoordinator.clearAll()
    val cleanTab = BrowserTabState(title = "Speed Dial", isSpeedDial = true)
    _tabs.value = listOf(cleanTab)
    _activeTabId.value = cleanTab.id

    viewModelScope.launch {
      withContext(NonCancellable + Dispatchers.IO) {
        vpnCoordinator.disconnectWireGuardBlocking()
        runCatching { repository.wipeAllPersistedData() }
        DeviceTraceCleaner.wipeFileSystemTraces(app)
        speedEngine.clearNetworkPools()
      }
      onComplete()
    }
  }

  private fun readAppUidTotalBytes(): Long? {
    val uid = android.os.Process.myUid()
    val rx = android.net.TrafficStats.getUidRxBytes(uid)
    val tx = android.net.TrafficStats.getUidTxBytes(uid)
    val unsupported = android.net.TrafficStats.UNSUPPORTED.toLong()
    if (rx == unsupported || tx == unsupported || rx < 0L || tx < 0L) return null
    return rx + tx
  }

  fun refreshSocialAppStates() {
    _socialApps.value = SocialHubHelper.buildShortcuts(app)
    _hasSystemAutofill.value = SocialHubHelper.isAutofillEnabled(app)
  }

  fun toggleSocialSidebar() {
    val next = !_socialSidebarOpen.value
    if (next) refreshSocialAppStates()
    _socialSidebarOpen.value = next
  }

  fun closeSocialSidebar() {
    _socialSidebarOpen.value = false
  }

  fun openSocialInBrowser(shortcut: SocialAppShortcut) {
    _socialSidebarOpen.value = false
    navigate(shortcut.webUrl)
  }

  fun launchInstalledSocialApp(shortcut: SocialAppShortcut) {
    val launchIntent = app.packageManager.getLaunchIntentForPackage(shortcut.packageName)
    if (launchIntent != null) {
      launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      runCatching { app.startActivity(launchIntent) }
        .onFailure { navigate(shortcut.webUrl) }
      _socialSidebarOpen.value = false
    } else {
      showMessage("${shortcut.name} app not installed — opening web version")
      openSocialInBrowser(shortcut)
    }
  }

  fun triggerSocialAutofill() {
    val tab = activeTab.value
    if (tab.isSpeedDial) {
      showMessage("Open Instagram or Facebook first, then tap Autofill Login")
      return
    }
    refreshSocialAppStates()
    val requested = webViewPool.requestAutofill(tab.id)
    if (requested) {
      _socialSidebarOpen.value = false
      showMessage("System Autofill triggered — tap email or password box to fill saved login")
    } else if (!_hasSystemAutofill.value) {
      openDeviceAutofillSettings()
    } else {
      showMessage("Tap the username or password box on the page to trigger device Autofill")
    }
  }

  fun openDeviceAutofillSettings() {
    if (!SocialHubHelper.openAutofillSettings(app)) {
      showMessage("Open Android Settings > Passwords & accounts to enable Autofill")
    }
  }

  private fun updateTab(tabId: String, transform: (BrowserTabState) -> BrowserTabState) {
    _tabs.update { list -> list.map { if (it.id == tabId) transform(it) else it } }
  }

  override fun onCleared() {
    super.onCleared()
    mediaManager.release()
    vpnCoordinator.stopWireGuardStatsPolling()
    webViewPool.destroyAllTabs()
    geckoTabPool.destroyAllTabs()
  }
}
