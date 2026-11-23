package com.example.ui

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.VoltBrowserApp
import com.example.browser.DeviceTraceCleaner
import com.example.browser.MediaSniffer
import com.example.browser.PlaylistResolver
import com.example.browser.TabWebCallbacks
import com.example.browser.VoltSpeedEngine
import com.example.browser.WebViewTabPool
import com.example.data.AdBlockerType
import com.example.data.AppThemeMode
import com.example.data.BookmarkEntity
import com.example.data.BrowserSettings
import com.example.data.BrowserTabState
import com.example.data.DetectedMediaItem
import com.example.data.DownloadItemEntity
import com.example.data.MediaKind
import com.example.data.SearchEngine
import com.example.data.SocialAppShortcut
import com.example.data.WebContextMenuTarget
import com.example.media.MediaDownloader
import com.example.media.MediaPlaybackManager
import com.example.media.MediaPlayerUiState
import com.example.media.VoltMediaService
import com.example.vpn.DohProvider
import com.example.vpn.PublicRelayManager
import com.example.vpn.PublicRelayNode
import com.example.vpn.VoltVpnService
import com.example.vpn.VpnTunnelState
import com.example.vpn.WireGuardManager
import com.example.vpn.WireGuardMode
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class BrowserSheet {
  NONE,
  TAB_SWITCHER,
  MEDIA_HUB,
  SECURITY_TELEMETRY,
  LIBRARY_SETTINGS,
  ADD_SPEED_DIAL,
  VPN,
  EXIT_CONFIRM,
  OMNI_SEARCH,
}

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

  private val app = application as VoltBrowserApp
  private val repository = app.repository
  val speedEngine = app.speedEngine

  val webViewPool = WebViewTabPool(application, speedEngine, viewModelScope)
  val mediaManager = MediaPlaybackManager(application, viewModelScope)
  private val mediaDownloader =
    MediaDownloader(application, speedEngine.okHttpClient, repository, viewModelScope)
  private val relayManager = PublicRelayManager()
  private val wireGuardManager = WireGuardManager(application)
  private var wireGuardStatsJob: Job? = null
  private val skippedRelayIds = mutableSetOf<String>()
  private var lastFailoverTimestamp = 0L
  private val activeMediaScans = ConcurrentHashMap<String, AtomicInteger>()
  private val rejectedMediaUrlsByTab = ConcurrentHashMap<String, MutableSet<String>>()

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

  val mediaPlayerState: StateFlow<MediaPlayerUiState> = mediaManager.uiState

  private val _vpnRelayState = MutableStateFlow(VpnTunnelState())
  val vpnState: StateFlow<VpnTunnelState> =
    combine(_vpnRelayState, VoltVpnService.runtimeStats) { local, runtime ->
        local.copy(
          isDnsVpnActive = runtime.isRunning,
          activeDohProvider = if (runtime.isRunning) runtime.activeProvider else local.activeDohProvider,
          queriesEncrypted = runtime.queriesEncrypted,
          lastDnsLatencyMs = runtime.lastLatencyMs,
          statusMessage = runtime.errorMessage ?: local.statusMessage,
        )
      }
      .stateIn(viewModelScope, SharingStarted.Eagerly, VpnTunnelState())

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
        rejectedMediaUrlsByTab.remove(tabId)
        updateTab(tabId) {
          it.copy(
            url = url,
            isLoading = true,
            progress = 10,
            isSpeedDial = false,
            isSecure = url.startsWith("https://", ignoreCase = true),
            trackersBlockedOnPage = 0,
            bytesSavedOnPage = 0L,
            detectedMedia = emptyList(),
            isScanningMedia = false,
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
        mergeAndProbeMediaItems(tabId, items)
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
        val currentIncognito = activeTab.value.isIncognito
        newTab(isIncognito = currentIncognito, initialUrl = url)
      }

      override fun onShowFullscreenView(view: View, callback: WebChromeClient.CustomViewCallback) {
        _fullscreenCustomView.value = view to callback
      }

      override fun onHideFullscreenView() {
        _fullscreenCustomView.value?.second?.onCustomViewHidden()
        _fullscreenCustomView.value = null
      }

      override fun onMainFrameNetworkError(tabId: String, errorCode: Int, description: String) {
        val relay = _vpnRelayState.value.activeRelay ?: return
        if (!settings.value.autoProxyFailoverEnabled) return
        val isProxyOrNetworkError =
          errorCode == WebViewClient.ERROR_PROXY_AUTHENTICATION ||
            errorCode == WebViewClient.ERROR_CONNECT ||
            errorCode == WebViewClient.ERROR_TIMEOUT ||
            errorCode == WebViewClient.ERROR_HOST_LOOKUP ||
            errorCode == WebViewClient.ERROR_IO ||
            errorCode == WebViewClient.ERROR_FAILED_SSL_HANDSHAKE
        if (!isProxyOrNetworkError) return

        val now = System.currentTimeMillis()
        if (now - lastFailoverTimestamp < 5_000L || _vpnRelayState.value.isConnectingRelay) return
        lastFailoverTimestamp = now
        skippedRelayIds.add(relay.id)
        _vpnRelayState.update { it.copy(autoFailoversCount = it.autoFailoversCount + 1) }
        rotateToNextProxy(reloadActiveTab = tabId == activeTab.value.id)
      }

      override fun onContextMenuRequested(tabId: String, target: WebContextMenuTarget) {
        if (tabId == activeTab.value.id) {
          _contextMenuTarget.value = target
        }
      }
    }

  init {
    webViewPool.proxyCredentialsProvider = { speedEngine.getActiveProxyCredentials() }
    refreshSocialAppStates()
    val baselineUidBytes = readAppUidTotalBytes()
    viewModelScope.launch {
      while (true) {
        val currentUidBytes = readAppUidTotalBytes()
        val trafficDelta =
          if (baselineUidBytes != null && currentUidBytes != null) {
            (currentUidBytes - baselineUidBytes).coerceAtLeast(0L)
          } else {
            0L
          }
        val dlBytes = downloads.value.sumOf { it.downloadedBytes.coerceAtLeast(0L) }
        _sessionDataUsedBytes.value = maxOf(trafficDelta, dlBytes)
        delay(1500L)
      }
    }
    viewModelScope.launch {
      withContext(Dispatchers.IO) {
        repository.initializeCleanSession()
        repository.preferences.ensureStartupVpnOffDefault()
      }
      val initialSettings = repository.settings.first()
      _vpnRelayState.update {
        it.copy(
          activeDohProvider = initialSettings.preferredDohProvider,
          hasSavedWarpConfig = initialSettings.savedWarpConf.isNotBlank(),
          hasSavedCustomWgConf = initialSettings.savedCustomWgConf.isNotBlank(),
        )
      }

      var startedWireGuardOnBoot = false
      if (initialSettings.autoWireGuardOnLaunchEnabled && VpnService.prepare(app) == null) {
        when {
          initialSettings.preferredWireGuardMode == WireGuardMode.CUSTOM_CONF &&
            initialSettings.savedCustomWgConf.isNotBlank() -> {
            startedWireGuardOnBoot = true
            connectCustomWireGuardConf(
              rawConf = initialSettings.savedCustomWgConf,
              profileName = initialSettings.savedCustomWgName.ifBlank { "Custom WireGuard" },
              persistConfig = false,
            )
          }
          initialSettings.savedWarpConf.isNotBlank() -> {
            startedWireGuardOnBoot = true
            connectCloudflareWarp(silentStartup = true)
          }
        }
      }

      if (!startedWireGuardOnBoot && initialSettings.autoDnsVpnOnLaunchEnabled && VpnService.prepare(app) == null) {
        startDnsVpnService()
      }
      if (!startedWireGuardOnBoot && initialSettings.autoProxyOnLaunchEnabled) {
        autoConnectFastestRelay(fastStartup = true)
      }
      // Run WebView cookie/storage cleanup after initial composition so first frame stays instant
      delay(300L)
      if (_tabs.value.all { it.isSpeedDial }) {
        webViewPool.wipeAllCacheAndCookies()
      }
    }
    viewModelScope.launch {
      settings.collect { currentSettings ->
        webViewPool.refreshSettings(currentSettings)
        _vpnRelayState.update {
          it.copy(
            hasSavedWarpConfig = currentSettings.savedWarpConf.isNotBlank(),
            hasSavedCustomWgConf = currentSettings.savedCustomWgConf.isNotBlank(),
          )
        }
      }
    }
    mediaManager.connect()
  }

  fun navigate(rawInput: String) {
    val trimmed = rawInput.trim()
    if (trimmed.isEmpty()) return
    val targetUrl =
      VoltSpeedEngine.normalizeInputToUrl(trimmed, settings.value.searchEngine) ?: return
    speedEngine.prewarmOrigin(targetUrl)

    val current = activeTab.value
    updateTab(current.id) {
      it.copy(url = targetUrl, isSpeedDial = false, isLoading = true, progress = 5)
    }
    val wv = webViewPool.getOrCreateWebView(current.id, current.isIncognito, tabWebCallbacks)
    wv.loadUrl(targetUrl)
    _activeSheet.value = BrowserSheet.NONE
  }

  fun onOmniboxQueryChanged(query: String) {
    if (query.isBlank()) return
    // Pre-warm DNS & TLS socket if user typed a probable domain (zero history saved)
    if (query.contains('.') && !query.contains(' ')) {
      speedEngine.prewarmOrigin(query)
    }
  }

  fun goBack(): Boolean {
    val current = activeTab.value
    if (current.isSpeedDial) return false
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
    val resolvedUrl =
      if (initialUrl.isNotBlank()) {
        VoltSpeedEngine.normalizeInputToUrl(initialUrl, settings.value.searchEngine).orEmpty()
      } else {
        ""
      }
    val newTab =
      BrowserTabState(
        url = resolvedUrl,
        title = if (resolvedUrl.isBlank()) (if (isIncognito) "Private Dial" else "Speed Dial") else resolvedUrl,
        isIncognito = isIncognito,
        isSpeedDial = resolvedUrl.isBlank(),
      )
    _tabs.update { it + newTab }
    _activeTabId.value = newTab.id
    _activeSheet.value = BrowserSheet.NONE
    if (resolvedUrl.isNotBlank()) {
      speedEngine.prewarmOrigin(resolvedUrl)
      val wv = webViewPool.getOrCreateWebView(newTab.id, isIncognito, tabWebCallbacks)
      wv.loadUrl(resolvedUrl)
    }
  }

  fun openOmniMultiQuery(query: String) {
    val trimmed = query.trim()
    if (trimmed.isBlank()) return
    val encoded = java.net.URLEncoder.encode(trimmed, "UTF-8")
    val engines =
      listOf(
        SearchEngine.SEARXNG,
        SearchEngine.MOJEEK,
        SearchEngine.MARGINALIA,
        SearchEngine.BRAVE,
        SearchEngine.DUCKDUCKGO,
      )
    val urls = engines.map { it.searchUrlTemplate.replace("%s", encoded) }
    urls.forEachIndexed { index, url ->
      if (index == 0) {
        navigate(url)
      } else {
        newTab(isIncognito = activeTab.value.isIncognito, initialUrl = url)
      }
    }
    _activeSheet.value = BrowserSheet.NONE
    showMessage("Launched parallel search across ${engines.size} independent engines")
  }

  fun switchToNextTab() {
    val list = _tabs.value
    if (list.size <= 1) return
    val currentIndex = list.indexOfFirst { it.id == _activeTabId.value }
    if (currentIndex == -1) return
    val nextIndex = (currentIndex + 1) % list.size
    _activeTabId.value = list[nextIndex].id
    _activeSheet.value = BrowserSheet.NONE
  }

  fun switchToPreviousTab() {
    val list = _tabs.value
    if (list.size <= 1) return
    val currentIndex = list.indexOfFirst { it.id == _activeTabId.value }
    if (currentIndex == -1) return
    val prevIndex = (currentIndex - 1 + list.size) % list.size
    _activeTabId.value = list[prevIndex].id
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
      val fresh = BrowserTabState(title = "Speed Dial", isSpeedDial = true)
      _tabs.value = listOf(fresh)
      _activeTabId.value = fresh.id
      return
    }
    val index = currentList.indexOfFirst { it.id == tabId }
    val remaining = currentList.filterNot { it.id == tabId }
    webViewPool.destroyTab(tabId)
    _tabs.value = remaining
    if (_activeTabId.value == tabId) {
      val nextIndex = (index - 1).coerceIn(0, remaining.lastIndex)
      _activeTabId.value = remaining[nextIndex].id
    }
  }

  fun openSheet(sheet: BrowserSheet) {
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
    }
  }

  fun findInPage(query: String, forward: Boolean = true) {
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
      val path = Uri.parse(rawUrl).path
      val file = path?.let { File(it) }
      if (file == null || !file.exists()) {
        showMessage("Local file is no longer available")
        return
      }
      targetUri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
      grantRead = true
    } else {
      targetUri = Uri.parse(rawUrl)
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
    rejectedMediaUrlsByTab.remove(tab.id)
    webViewPool.triggerMediaScan(tab.id)
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
    rejectedMediaUrlsByTab[tabId]?.remove(trimmed)
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
    mergeAndProbeMediaItems(tabId, listOf(classified), forceReprobe = true, isManualResolve = true)
    showMessage("Resolving link & checking downloadable videos (> 1 MB)...")
  }

  fun requeryAllMediaSizes() {
    val tab = activeTab.value
    if (tab.detectedMedia.isEmpty()) return
    mergeAndProbeMediaItems(tab.id, tab.detectedMedia, forceReprobe = true)
  }

  private fun buildVerifiedVideoItem(
    baseItem: DetectedMediaItem,
    probe: MediaSniffer.VideoProbeResult,
  ): DetectedMediaItem? {
    if (!probe.isDownloadable) return null
    val verifiedSize = probe.contentSizeBytes ?: return null
    if (verifiedSize <= MediaSniffer.MIN_DOWNLOADABLE_VIDEO_BYTES) return null

    val finalUrl =
      probe.resolvedUrl?.takeIf {
        it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
      } ?: baseItem.url

    val resolvedTitle =
      if (finalUrl != baseItem.url) {
        val segment =
          runCatching { Uri.parse(finalUrl).lastPathSegment }.getOrNull()
            ?.takeIf { it.isNotBlank() && it.length <= 72 && it.contains('.') }
        val genericTitles = setOf("Playlist / Media", "Resolved Video", "Web Video", "Captured Video", "")
        if (baseItem.title in genericTitles || !baseItem.title.contains('.')) {
          segment ?: baseItem.title
        } else {
          baseItem.title
        }
      } else {
        baseItem.title
      }

    return baseItem.copy(
      url = finalUrl,
      title = resolvedTitle,
      contentSizeBytes = verifiedSize,
      isSizeEstimated = false,
      mimeType = probe.mimeType?.takeIf { it.startsWith("video/", ignoreCase = true) } ?: baseItem.mimeType,
      isResolvingMetadata = false,
    )
  }

  private fun mergeAndProbeMediaItems(
    tabId: String,
    items: List<DetectedMediaItem>,
    forceReprobe: Boolean = false,
    isManualResolve: Boolean = false,
  ) {
    val currentTab = _tabs.value.firstOrNull { it.id == tabId }
    val existingMap = currentTab?.detectedMedia?.associateBy { it.url }.orEmpty()
    val rejectedSet = rejectedMediaUrlsByTab.computeIfAbsent(tabId) { java.util.Collections.synchronizedSet(HashSet()) }

    val candidates =
      items.filter { item ->
        item.kind != MediaKind.AUDIO &&
          !item.url.contains("#dash_") &&
          (forceReprobe || (!existingMap.containsKey(item.url) && !rejectedSet.contains(item.url)))
      }
    if (candidates.isEmpty()) return

    candidates.forEach { candidate ->
      val isPlaylistOrAdaptive =
        candidate.kind == MediaKind.PLAYLIST ||
          candidate.kind == MediaKind.STREAM_DASH ||
          candidate.kind == MediaKind.STREAM_HLS

      if (isPlaylistOrAdaptive) {
        rejectedSet.add(candidate.url) // Prevent duplicate background resolves unless forceReprobe
        beginMediaScan(tabId)
        viewModelScope.launch {
          try {
            val result = PlaylistResolver.resolve(speedEngine.okHttpClient, candidate)

            val directVideos = result.resolvedItems.filter { it.kind == MediaKind.VIDEO }.toMutableList()
            val hlsVideos =
              result.resolvedItems.filter {
                it.kind == MediaKind.STREAM_HLS &&
                  (it.segmentCount ?: 0) > 0 &&
                  (it.contentSizeBytes ?: 0L) > MediaSniffer.MIN_DOWNLOADABLE_VIDEO_BYTES
              }.toMutableList()

            // If an HTML/JSON wrapper resolved to an uninspected .m3u8 master playlist, resolve it one hop deeper
            val nestedUninspectedHls =
              result.resolvedItems
                .filter { it.kind == MediaKind.STREAM_HLS && (it.segmentCount ?: 0) == 0 }
                .take(3)
            nestedUninspectedHls.forEach { nested ->
              val nestedRes = PlaylistResolver.resolve(speedEngine.okHttpClient, nested)
              directVideos.addAll(nestedRes.resolvedItems.filter { it.kind == MediaKind.VIDEO })
              hlsVideos.addAll(
                nestedRes.resolvedItems.filter {
                  it.kind == MediaKind.STREAM_HLS &&
                    (it.segmentCount ?: 0) > 0 &&
                    (it.contentSizeBytes ?: 0L) > MediaSniffer.MIN_DOWNLOADABLE_VIDEO_BYTES
                }
              )
              if (nestedRes.isParentDownloadableVideo &&
                (nestedRes.updatedParent.contentSizeBytes ?: 0L) > MediaSniffer.MIN_DOWNLOADABLE_VIDEO_BYTES
              ) {
                hlsVideos.add(nestedRes.updatedParent)
              }
            }

            val verifiedToAdd = mutableListOf<DetectedMediaItem>()
            if (result.isParentDownloadableVideo &&
              (result.updatedParent.contentSizeBytes ?: 0L) > MediaSniffer.MIN_DOWNLOADABLE_VIDEO_BYTES
            ) {
              verifiedToAdd.add(result.updatedParent.copy(isResolvingMetadata = false))
            }
            verifiedToAdd.addAll(hlsVideos.map { it.copy(isResolvingMetadata = false) })

            directVideos.distinctBy { it.url }.forEach { videoChild ->
              val probe =
                MediaSniffer.probeDownloadableVideo(
                  okHttpClient = speedEngine.okHttpClient,
                  url = videoChild.url,
                  referer = videoChild.sourcePageUrl,
                  minBytes = MediaSniffer.MIN_DOWNLOADABLE_VIDEO_BYTES,
                )
              val verified = buildVerifiedVideoItem(videoChild, probe)
              if (verified != null) {
                verifiedToAdd.add(verified)
              } else {
                rejectedSet.add(videoChild.url)
                probe.resolvedUrl?.let { rejectedSet.add(it) }
              }
            }

            if (verifiedToAdd.isNotEmpty()) {
              upsertVerifiedDownloadableVideos(tabId, verifiedToAdd.distinctBy { it.url })
              if (isManualResolve) {
                showMessage("Found ${verifiedToAdd.distinctBy { it.url }.size} downloadable video(s) > 1 MB")
              }
            } else {
              if (forceReprobe) {
                removeMediaItem(tabId, candidate.url)
              }
              if (isManualResolve) {
                showMessage("No downloadable video > 1 MB found at that URL")
              }
            }
          } finally {
            endMediaScan(tabId)
          }
        }
      } else {
        // Direct VIDEO candidate: resolve redirects, verify HTTP 2xx downloadability & strictly > 1 MB BEFORE listing
        beginMediaScan(tabId)
        viewModelScope.launch {
          try {
            val probe =
              MediaSniffer.probeDownloadableVideo(
                okHttpClient = speedEngine.okHttpClient,
                url = candidate.url,
                referer = candidate.sourcePageUrl,
                minBytes = MediaSniffer.MIN_DOWNLOADABLE_VIDEO_BYTES,
              )
            val verified = buildVerifiedVideoItem(candidate, probe)
            if (verified != null) {
              if (verified.url != candidate.url) {
                removeMediaItem(tabId, candidate.url)
              }
              upsertVerifiedDownloadableVideos(tabId, listOf(verified))
              if (isManualResolve) {
                showMessage("Verified downloadable video (> 1 MB)")
              }
            } else {
              rejectedSet.add(candidate.url)
              probe.resolvedUrl?.let { rejectedSet.add(it) }
              if (forceReprobe) {
                removeMediaItem(tabId, candidate.url)
              }
              if (isManualResolve) {
                showMessage("Video unavailable or smaller than 1 MB")
              }
            }
          } finally {
            endMediaScan(tabId)
          }
        }
      }
    }
  }

  private fun beginMediaScan(tabId: String) {
    val count = activeMediaScans.computeIfAbsent(tabId) { AtomicInteger(0) }.incrementAndGet()
    if (count > 0) {
      updateTab(tabId) { it.copy(isScanningMedia = true) }
    }
  }

  private fun endMediaScan(tabId: String) {
    val count = activeMediaScans[tabId]?.decrementAndGet() ?: 0
    if (count <= 0) {
      activeMediaScans[tabId]?.set(0)
      updateTab(tabId) { it.copy(isScanningMedia = false) }
    }
  }

  private fun upsertVerifiedDownloadableVideos(tabId: String, items: List<DetectedMediaItem>) {
    updateTab(tabId) { tab ->
      val merged = LinkedHashMap<String, DetectedMediaItem>()
      tab.detectedMedia.forEach { merged[it.url] = it }
      items.forEach { verified ->
        merged[verified.url] = verified
      }
      val sorted =
        merged.values
          .filter { item ->
            (item.kind == MediaKind.VIDEO || item.kind == MediaKind.STREAM_HLS) &&
              !item.url.contains("#dash_") &&
              (item.contentSizeBytes ?: 0L) > MediaSniffer.MIN_DOWNLOADABLE_VIDEO_BYTES
          }
          .sortedWith(
            compareBy<DetectedMediaItem> { if (itemKindRank(it.kind) == 0) 0 else 1 }
              .thenByDescending { it.contentSizeBytes ?: it.bitrateBps ?: 0L }
          )
      tab.copy(detectedMedia = sorted)
    }
  }

  private fun itemKindRank(kind: MediaKind): Int = if (kind == MediaKind.VIDEO) 0 else 1

  private fun removeMediaItem(tabId: String, url: String) {
    updateTab(tabId) { tab ->
      tab.copy(detectedMedia = tab.detectedMedia.filterNot { it.url == url })
    }
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

  fun retryDownload(item: DownloadItemEntity) {
    viewModelScope.launch {
      if (item.url.startsWith("data:", ignoreCase = true) && MediaDownloader.parseDataUrl(item.url) == null) {
        showMessage("Blob/data download expired — please trigger it from the webpage again")
        return@launch
      }
      repository.deleteDownload(item.id)
      downloadMedia(
        url = item.url,
        fileName = item.fileName,
        mimeType = item.mimeType,
        referer = activeTab.value.url.takeIf { it.startsWith("http") },
      )
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
      runCatching { File(item.localPath).delete() }
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

  fun setAdBlockerType(type: AdBlockerType) {
    viewModelScope.launch {
      repository.preferences.setAdBlockerType(type)
      if (!activeTab.value.isSpeedDial) {
        webViewPool.findWebView(activeTab.value.id)?.reload()
      }
      showMessage("Blocker set to ${type.displayName}")
    }
  }

  fun toggleTurboShield(enabled: Boolean) {
    setAdBlockerType(if (enabled) AdBlockerType.ADGUARD else AdBlockerType.OFF)
  }

  fun toggleLiteMode(enabled: Boolean) {
    viewModelScope.launch { repository.preferences.setLiteMode(enabled) }
  }

  fun toggleDarkWeb(enabled: Boolean) {
    viewModelScope.launch { repository.preferences.setDarkWeb(enabled) }
  }

  fun toggleDesktopMode(enabled: Boolean) {
    viewModelScope.launch {
      repository.preferences.setDesktopMode(enabled)
      webViewPool.findWebView(activeTab.value.id)?.reload()
    }
  }

  fun setSearchEngine(engine: SearchEngine) {
    viewModelScope.launch { repository.preferences.setSearchEngine(engine) }
  }

  fun setThemeMode(mode: AppThemeMode) {
    viewModelScope.launch { repository.preferences.setThemeMode(mode) }
  }

  fun toggleAutoWireGuardOnLaunch(enabled: Boolean) {
    viewModelScope.launch { repository.preferences.setAutoWireGuardOnLaunch(enabled) }
  }

  fun toggleAutoProxyOnLaunch(enabled: Boolean) {
    viewModelScope.launch { repository.preferences.setAutoProxyOnLaunch(enabled) }
    if (enabled && _vpnRelayState.value.activeRelay == null && !_vpnRelayState.value.isWireGuardActive) {
      autoConnectFastestRelay(fastStartup = true)
    }
  }

  fun toggleAutoProxyFailover(enabled: Boolean) {
    viewModelScope.launch { repository.preferences.setAutoProxyFailover(enabled) }
  }

  fun toggleAutoDnsVpnOnLaunch(enabled: Boolean) {
    viewModelScope.launch { repository.preferences.setAutoDnsVpnOnLaunch(enabled) }
  }

  fun setProxyProtocolFilter(filter: com.example.vpn.ProxyProtocolFilter) {
    _vpnRelayState.update { it.copy(selectedProtocolFilter = filter) }
  }

  fun setProxyCountryFilter(countryCode: String) {
    _vpnRelayState.update { it.copy(selectedCountryFilter = countryCode) }
  }

  fun connectCustomProxy(rawAddress: String, protocol: String) {
    val parsed = PublicRelayManager.parseSingleProxy(rawAddress, protocol)
    if (parsed == null) {
      showMessage("Enter ip:port or user:pass@ip:port (public IPv4)")
      return
    }
    connectRelay(parsed, reloadActiveTab = false)
  }

  fun showMessage(msg: String) {
    _toastMessage.value = msg
  }

  fun clearMessage() {
    _toastMessage.value = null
  }

  fun onVpnSheetOpened() {
    openSheet(BrowserSheet.VPN)
    refreshExitIp()
  }

  fun connectCloudflareWarp(silentStartup: Boolean = false) {
    if (_vpnRelayState.value.isWireGuardConnecting) return
    viewModelScope.launch {
      if (VoltVpnService.runtimeStats.value.isRunning) {
        stopDnsVpn(silent = true)
      }
      if (_vpnRelayState.value.activeRelay != null) {
        relayManager.clearWebViewProxy {}
        speedEngine.setActiveProxy(null)
      }
      _vpnRelayState.update {
        it.copy(
          isWireGuardConnecting = true,
          activeRelay = null,
          statusMessage = "Connecting Cloudflare WARP (WireGuard)...",
        )
      }
      val existingConf = settings.value.savedWarpConf
      val confResult =
        if (existingConf.isNotBlank()) {
          Result.success(existingConf)
        } else {
          wireGuardManager.registerCloudflareWarpConfig().also { result ->
            result.getOrNull()?.let { freshConf ->
              repository.preferences.saveWarpConfig(freshConf)
            }
          }
        }

      val confText = confResult.getOrElse { err ->
        _vpnRelayState.update {
          it.copy(
            isWireGuardConnecting = false,
            statusMessage = "WARP setup error: ${err.message ?: "Network unreachable"}",
          )
        }
        if (!silentStartup) showMessage("Could not register Cloudflare WARP right now")
        return@launch
      }

      if (existingConf.isNotBlank()) {
        repository.preferences.setPreferredWireGuardMode(WireGuardMode.WARP_FREE)
      }

      var connectResult = wireGuardManager.connect(confText, "Cloudflare WARP")
      if (connectResult.isFailure && existingConf.isNotBlank()) {
        // Refresh WARP registration once if cached key expired
        val fresh = wireGuardManager.registerCloudflareWarpConfig().getOrNull()
        if (fresh != null) {
          repository.preferences.saveWarpConfig(fresh)
          connectResult = wireGuardManager.connect(fresh, "Cloudflare WARP")
        }
      }

      connectResult.fold(
        onSuccess = { info ->
          _vpnRelayState.update {
            it.copy(
              isWireGuardActive = true,
              isWireGuardConnecting = false,
              wireGuardMode = WireGuardMode.WARP_FREE,
              wireGuardProfileName = info.profileName,
              wireGuardEndpoint = info.endpoint,
              hasSavedWarpConfig = true,
              statusMessage = "Cloudflare WARP WireGuard active (${info.endpoint})",
            )
          }
          startWireGuardStatsPolling()
          delay(350L)
          refreshExitIp()
          if (!silentStartup) showMessage("Cloudflare WARP WireGuard connected")
        },
        onFailure = { err ->
          _vpnRelayState.update {
            it.copy(
              isWireGuardActive = false,
              isWireGuardConnecting = false,
              statusMessage = "WireGuard failed: ${err.message ?: "VPN permission needed"}",
            )
          }
          if (!silentStartup) showMessage("Failed to start WireGuard tunnel")
        },
      )
    }
  }

  fun connectSavedCustomWireGuard() {
    val saved = settings.value.savedCustomWgConf
    if (saved.isBlank()) {
      showMessage("Import or paste a WireGuard .conf first")
      return
    }
    val name = settings.value.savedCustomWgName.ifBlank { "Custom WireGuard" }
    connectCustomWireGuardConf(saved, name, persistConfig = false)
  }

  fun connectCustomWireGuardConf(
    rawConf: String,
    profileName: String = "Custom WireGuard",
    persistConfig: Boolean = true,
  ) {
    if (_vpnRelayState.value.isWireGuardConnecting) return
    val validation = wireGuardManager.parseConfig(rawConf)
    if (validation.isFailure) {
      val msg = validation.exceptionOrNull()?.message ?: "Invalid WireGuard .conf format"
      _vpnRelayState.update { it.copy(statusMessage = msg) }
      showMessage(msg)
      return
    }

    viewModelScope.launch {
      if (VoltVpnService.runtimeStats.value.isRunning) {
        stopDnsVpn(silent = true)
      }
      if (_vpnRelayState.value.activeRelay != null) {
        relayManager.clearWebViewProxy {}
        speedEngine.setActiveProxy(null)
      }
      _vpnRelayState.update {
        it.copy(
          isWireGuardConnecting = true,
          activeRelay = null,
          statusMessage = "Starting $profileName...",
        )
      }
      if (persistConfig) {
        repository.preferences.saveCustomWireGuardConfig(rawConf.trim(), profileName.trim())
      } else {
        repository.preferences.setPreferredWireGuardMode(WireGuardMode.CUSTOM_CONF)
      }

      wireGuardManager.connect(rawConf, profileName).fold(
        onSuccess = { info ->
          _vpnRelayState.update {
            it.copy(
              isWireGuardActive = true,
              isWireGuardConnecting = false,
              wireGuardMode = WireGuardMode.CUSTOM_CONF,
              wireGuardProfileName = info.profileName,
              wireGuardEndpoint = info.endpoint,
              hasSavedCustomWgConf = true,
              statusMessage = "$profileName active (${info.endpoint})",
            )
          }
          startWireGuardStatsPolling()
          delay(350L)
          refreshExitIp()
          showMessage("$profileName connected")
        },
        onFailure = { err ->
          _vpnRelayState.update {
            it.copy(
              isWireGuardActive = false,
              isWireGuardConnecting = false,
              statusMessage = "WireGuard error: ${err.message ?: "Unable to connect"}",
            )
          }
          showMessage("WireGuard error: ${err.message ?: "Unable to connect"}")
        },
      )
    }
  }

  fun disconnectWireGuard(silent: Boolean = false) {
    stopWireGuardStatsPolling()
    viewModelScope.launch {
      wireGuardManager.disconnect()
      _vpnRelayState.update {
        it.copy(
          isWireGuardActive = false,
          isWireGuardConnecting = false,
          wireGuardMode = WireGuardMode.NONE,
          wireGuardProfileName = "",
          wireGuardEndpoint = "",
          wireGuardRxBytes = 0L,
          wireGuardTxBytes = 0L,
          statusMessage = if (silent) it.statusMessage else "WireGuard VPN disconnected.",
        )
      }
      if (!silent) {
        refreshExitIp()
        showMessage("WireGuard VPN disconnected")
      }
    }
  }

  private fun startWireGuardStatsPolling() {
    wireGuardStatsJob?.cancel()
    wireGuardStatsJob =
      viewModelScope.launch {
        while (true) {
          val up = wireGuardManager.isTunnelActiveNow()
          if (!up) {
            _vpnRelayState.update {
              if (it.isWireGuardActive) it.copy(isWireGuardActive = false, wireGuardMode = WireGuardMode.NONE)
              else it
            }
            break
          }
          val (rx, tx) = wireGuardManager.readStatistics()
          _vpnRelayState.update {
            it.copy(
              wireGuardRxBytes = rx,
              wireGuardTxBytes = tx,
            )
          }
          delay(1500L)
        }
      }
  }

  private fun stopWireGuardStatsPolling() {
    wireGuardStatsJob?.cancel()
    wireGuardStatsJob = null
  }

  fun toggleFreeAutoProxy() {
    if (_vpnRelayState.value.activeRelay != null) {
      disconnectRelay()
    } else {
      autoConnectFastestRelay()
    }
  }

  fun prepareVpnConsentIntent(): Intent? = VpnService.prepare(app)

  fun startDnsVpnService() {
    viewModelScope.launch {
      if (_vpnRelayState.value.isWireGuardActive) {
        stopWireGuardStatsPolling()
        wireGuardManager.disconnect()
        _vpnRelayState.update {
          it.copy(
            isWireGuardActive = false,
            wireGuardMode = WireGuardMode.NONE,
            wireGuardProfileName = "",
            wireGuardEndpoint = "",
            wireGuardRxBytes = 0L,
            wireGuardTxBytes = 0L,
          )
        }
      }
      val provider = _vpnRelayState.value.activeDohProvider
      val intent =
        Intent(app, VoltVpnService::class.java).apply {
          action = VoltVpnService.ACTION_START
          putExtra(VoltVpnService.EXTRA_DOH_PROVIDER, provider.name)
        }
      ContextCompat.startForegroundService(app, intent)
      showMessage("Encrypted DNS VPN active (${provider.displayName})")
    }
  }

  fun stopDnsVpn(silent: Boolean = false) {
    val intent =
      Intent(app, VoltVpnService::class.java).apply {
        action = VoltVpnService.ACTION_STOP
      }
    app.startService(intent)
    if (!silent) showMessage("Encrypted DNS VPN disconnected")
  }

  fun selectDohProvider(provider: DohProvider) {
    _vpnRelayState.update { it.copy(activeDohProvider = provider) }
    viewModelScope.launch { repository.preferences.setPreferredDohProvider(provider) }
    if (VoltVpnService.runtimeStats.value.isRunning) {
      startDnsVpnService()
    }
  }

  fun refreshExitIp() {
    val relay = _vpnRelayState.value.activeRelay
    viewModelScope.launch {
      val info = relayManager.fetchCurrentExitIp(relay)
      _vpnRelayState.update {
        it.copy(
          currentExitIp = info?.ip ?: "Unavailable",
          currentExitCountry = info?.country.orEmpty(),
        )
      }
    }
  }

  fun refreshPublicRelays() {
    if (_vpnRelayState.value.isFetchingRelays) return
    _vpnRelayState.update {
      it.copy(
        isFetchingRelays = true,
        statusMessage = "Scanning 9 global feeds & benchmarking HTTPS/SOCKS5 proxies...",
      )
    }
    viewModelScope.launch {
      val summary = relayManager.fetchAndProbePublicRelays(excludeIds = skippedRelayIds)
      val relays = summary.relays
      val reachableCount = relays.count { it.isReachable == true }
      _vpnRelayState.update {
        it.copy(
          availableRelays = relays,
          totalCandidatesScanned = summary.totalCandidatesScanned,
          isFetchingRelays = false,
          statusMessage =
            when {
              relays.isEmpty() -> "Could not reach public proxy feeds right now."
              reachableCount == 0 ->
                "Scanned ${summary.totalCandidatesScanned} proxies; none responded cleanly. Tap Refresh to try a fresh batch."
              else ->
                "$reachableCount live relays verified from ${summary.totalCandidatesScanned} scanned."
            },
        )
      }
    }
  }

  fun autoConnectFastestRelay(reloadActiveTab: Boolean = false, fastStartup: Boolean = false) {
    if (_vpnRelayState.value.isFetchingRelays || _vpnRelayState.value.isConnectingRelay) return
    viewModelScope.launch {
      val stateSnapshot = _vpnRelayState.value
      val existingCandidates =
        filterRelays(
          relays = stateSnapshot.availableRelays,
          protocolFilter = stateSnapshot.selectedProtocolFilter,
          countryFilter = stateSnapshot.selectedCountryFilter,
        ).filter { it.isReachable == true && it.id !in skippedRelayIds }

      val candidates =
        if (existingCandidates.isNotEmpty()) {
          existingCandidates
        } else {
          _vpnRelayState.update {
            it.copy(
              isFetchingRelays = true,
              statusMessage =
                if (fastStartup) "Auto-loading fastest free proxy on startup..."
                else "Scanning global feeds & benchmarking fastest free proxies...",
            )
          }
          val summary =
            if (fastStartup) relayManager.findFastestStartupBatch(excludeIds = skippedRelayIds)
            else relayManager.fetchAndProbePublicRelays(excludeIds = skippedRelayIds)
          _vpnRelayState.update {
            it.copy(
              availableRelays = summary.relays,
              totalCandidatesScanned = summary.totalCandidatesScanned,
              isFetchingRelays = false,
            )
          }
          val filtered =
            filterRelays(
              relays = summary.relays,
              protocolFilter = _vpnRelayState.value.selectedProtocolFilter,
              countryFilter = _vpnRelayState.value.selectedCountryFilter,
            ).filter { it.isReachable == true && it.id !in skippedRelayIds }
          filtered.ifEmpty {
            summary.relays.filter { it.isReachable == true && it.id !in skippedRelayIds }
          }
        }

      val ordered = candidates.sortedWith(PublicRelayManager.relayRankingComparator)
      val selected =
        if (fastStartup) {
          val fastDeOrIn = ordered.firstOrNull { it.isPreferredRegion && (it.latencyMs ?: Long.MAX_VALUE) <= 1200L }
          fastDeOrIn ?: ordered.firstOrNull { (it.latencyMs ?: Long.MAX_VALUE) <= 1200L }
        } else {
          ordered.firstOrNull()
        }

      if (selected != null) {
        connectRelay(selected, reloadActiveTab = reloadActiveTab)
      } else {
        skippedRelayIds.clear()
        _vpnRelayState.update {
          it.copy(
            statusMessage =
              if (fastStartup && ordered.isNotEmpty())
                "Public proxies too slow right now (>1.2s) — staying direct for max speed."
              else
                "No responsive free proxies found in this batch. Tap Auto-Connect to retry.",
          )
        }
      }
    }
  }

  private fun filterRelays(
    relays: List<PublicRelayNode>,
    protocolFilter: com.example.vpn.ProxyProtocolFilter,
    countryFilter: String,
  ): List<PublicRelayNode> =
    relays.filter { relay ->
      val protoOk =
        when (protocolFilter) {
          com.example.vpn.ProxyProtocolFilter.ALL -> true
          com.example.vpn.ProxyProtocolFilter.HTTP -> relay.protocol.equals("HTTP", ignoreCase = true)
          com.example.vpn.ProxyProtocolFilter.SOCKS5 -> relay.protocol.equals("SOCKS5", ignoreCase = true)
        }
      val countryOk =
        countryFilter == "ALL" || relay.countryCode.equals(countryFilter, ignoreCase = true)
      protoOk && countryOk
    }

  fun rotateToNextProxy(reloadActiveTab: Boolean = false) {
    _vpnRelayState.value.activeRelay?.let { skippedRelayIds.add(it.id) }
    autoConnectFastestRelay(reloadActiveTab = reloadActiveTab)
  }

  fun connectRelay(relay: PublicRelayNode, reloadActiveTab: Boolean = false) {
    _vpnRelayState.update {
      it.copy(
        isConnectingRelay = true,
        statusMessage = "Connecting auto proxy ${relay.country} (${relay.protocol})...",
      )
    }
    relayManager.applyWebViewProxy(relay) { applied ->
      viewModelScope.launch {
        if (!applied) {
          _vpnRelayState.update {
            it.copy(
              isConnectingRelay = false,
              statusMessage = "WebView proxy override not supported on this device.",
            )
          }
          return@launch
        }
        speedEngine.setActiveProxy(
          proxy = PublicRelayManager.toJavaNetProxy(relay),
          username = relay.username,
          password = relay.password,
        )
        val ipInfo =
          if (relay.resolvedExitIp != null) null
          else relayManager.fetchCurrentExitIp(relay)
        val resolvedIp = relay.resolvedExitIp ?: ipInfo?.ip ?: relay.ip
        val resolvedCountry = ipInfo?.country?.ifBlank { relay.country } ?: relay.country
        val updatedRelay = relay.copy(country = resolvedCountry, resolvedExitIp = resolvedIp)
        _vpnRelayState.update {
          it.copy(
            activeRelay = updatedRelay,
            isConnectingRelay = false,
            currentExitIp = resolvedIp,
            currentExitCountry = resolvedCountry,
            statusMessage =
              "Optional Proxy active: $resolvedCountry (${relay.protocol} • ${relay.latencyMs ?: 0}ms)",
          )
        }
        if (reloadActiveTab && !activeTab.value.isSpeedDial) {
          webViewPool.findWebView(activeTab.value.id)?.reload()
        }
        showMessage("Proxy connected ($resolvedCountry)")
      }
    }
  }

  fun disconnectRelay() {
    relayManager.clearWebViewProxy {
      speedEngine.setActiveProxy(null)
      viewModelScope.launch {
        _vpnRelayState.update {
          it.copy(
            activeRelay = null,
            isConnectingRelay = false,
            statusMessage = "Optional Proxy disconnected (direct connection).",
          )
        }
        refreshExitIp()
        showMessage("Proxy disconnected")
      }
    }
  }

  fun exitAndEraseAllData(onComplete: () -> Unit) {
    _activeSheet.value = BrowserSheet.NONE
    _findInPageVisible.value = false
    _isBrowserFullscreen.value = false
    exitHtmlFullscreen()

    // Stop active downloads, media playback, WireGuard / DoH VPN tunnels, and proxy overrides immediately
    mediaDownloader.cancelAllDownloads()
    mediaManager.closePlayer()
    mediaManager.release()
    runCatching { app.stopService(Intent(app, VoltMediaService::class.java)) }

    stopWireGuardStatsPolling()
    if (VoltVpnService.runtimeStats.value.isRunning) {
      runCatching {
        app.startService(Intent(app, VoltVpnService::class.java).apply { action = VoltVpnService.ACTION_STOP })
      }
      runCatching { app.stopService(Intent(app, VoltVpnService::class.java)) }
    }
    speedEngine.setActiveProxy(null)
    relayManager.clearWebViewProxy {}

    // Destroy every WebView and erase WebView/Cookie/Storage/SSL/Form/Auth data
    webViewPool.destroyAllTabs()
    DeviceTraceCleaner.wipeWebViewTraces(app)

    // Clear in-memory state
    rejectedMediaUrlsByTab.clear()
    activeMediaScans.clear()
    skippedRelayIds.clear()
    val cleanTab = BrowserTabState(title = "Speed Dial", isSpeedDial = true)
    _tabs.value = listOf(cleanTab)
    _activeTabId.value = cleanTab.id
    _vpnRelayState.value = VpnTunnelState()

    viewModelScope.launch {
      withContext(NonCancellable + Dispatchers.IO) {
        runCatching { wireGuardManager.disconnect() }
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
    val pm = app.packageManager
    val igInstalled = isPackageInstalled(pm, "com.instagram.android")
    val fbInstalled = isPackageInstalled(pm, "com.facebook.katana")
    _socialApps.value =
      listOf(
        SocialAppShortcut(
          id = "instagram",
          name = "Instagram",
          packageName = "com.instagram.android",
          webUrl = "https://www.instagram.com/",
          loginUrl = "https://www.instagram.com/accounts/login/",
          isInstalled = igInstalled,
        ),
        SocialAppShortcut(
          id = "facebook",
          name = "Facebook",
          packageName = "com.facebook.katana",
          webUrl = "https://www.facebook.com/",
          loginUrl = "https://www.facebook.com/login/",
          isInstalled = fbInstalled,
        ),
      )
    _hasSystemAutofill.value =
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        runCatching {
          val manager = app.getSystemService(android.view.autofill.AutofillManager::class.java)
          manager != null && manager.isEnabled && manager.hasEnabledAutofillServices()
        }.getOrDefault(false)
      } else {
        false
      }
  }

  private fun isPackageInstalled(pm: android.content.pm.PackageManager, packageName: String): Boolean {
    return runCatching {
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        pm.getPackageInfo(packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
      } else {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(packageName, 0)
      }
      true
    }.getOrDefault(false)
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
    val opened =
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        runCatching {
          val intent =
            Intent(android.provider.Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
              data = Uri.parse("package:${app.packageName}")
              addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
          app.startActivity(intent)
          true
        }.getOrDefault(false)
      } else {
        false
      }
    if (!opened) {
      runCatching {
        app.startActivity(
          Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
      }.onFailure {
        showMessage("Open Android Settings > Passwords & accounts to enable Autofill")
      }
    }
  }

  private fun updateTab(tabId: String, transform: (BrowserTabState) -> BrowserTabState) {
    _tabs.update { list -> list.map { if (it.id == tabId) transform(it) else it } }
  }

  override fun onCleared() {
    super.onCleared()
    mediaManager.release()
    webViewPool.wipeAllCacheAndCookies()
    _tabs.value.forEach { webViewPool.destroyTab(it.id) }
  }
}
