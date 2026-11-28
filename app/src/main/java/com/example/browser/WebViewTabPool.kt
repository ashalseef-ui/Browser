package com.example.browser

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.HttpAuthHandler
import android.net.http.SslError
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.net.Uri
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

import com.example.data.BrowserSettings
import com.example.data.DetectedMediaItem
import com.example.data.MediaKind
import com.example.data.SiteHostOverride
import com.example.data.SitePermissionEntity
import com.example.data.SitePermissionType
import com.example.data.SiteSettingEntity
import com.example.data.WebContextMenuTarget
import java.io.ByteArrayInputStream
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Event callbacks emitted by pooled WebViews back to BrowserViewModel on main/UI state boundaries.
 */
interface TabWebCallbacks {
  fun onPageStarted(tabId: String, url: String)
  fun onPageFinished(tabId: String, url: String, title: String, loadTimeMs: Long, canGoBack: Boolean, canGoForward: Boolean)
  fun onProgressChanged(tabId: String, progress: Int)
  fun onTitleReceived(tabId: String, title: String)
  fun onTrackerBlocked(tabId: String, bytesSaved: Long)
  fun onMediaDetected(tabId: String, items: List<DetectedMediaItem>)
  fun onDownloadRequested(url: String, userAgent: String, contentDisposition: String, mimeType: String)
  fun onOpenNewTabRequested(url: String)
  fun onCreatePopupTabRequested(isIncognito: Boolean): String?
  fun onHttpsUpgradeFailed(tabId: String, failedHttpsUrl: String, fallbackHttpUrl: String, reason: String)
  fun onPublicProxyHttpWarning(tabId: String, httpUrl: String)
  fun onEnsureRuntimePermissions(types: Set<SitePermissionType>, onResult: (Boolean) -> Unit)
  fun onSitePermissionRequested(
    tabId: String,
    origin: String,
    host: String,
    isIncognito: Boolean,
    requestedTypes: Set<SitePermissionType>,
    onDecision: (granted: Boolean, rememberForSite: Boolean) -> Unit,
  )
  fun onSitePermissionCanceled(tabId: String)
  fun onPersistSitePermission(host: String, type: SitePermissionType, granted: Boolean)
  fun onShowFullscreenView(view: View, callback: WebChromeClient.CustomViewCallback)
  fun onHideFullscreenView()
  fun onMainFrameNetworkError(tabId: String, errorCode: Int, description: String)
  fun onContextMenuRequested(tabId: String, target: WebContextMenuTarget)
}

/**
 * Manages a bounded LRU pool of WebViews across Standard and Private tabs, enforcing
 * MultiProfile cookie isolation, HTTPS-Only upgrades, Kill Switch blocking, per-site
 * permissions/settings, and hardened WebMessageListener media sniffing.
 */
class WebViewTabPool(
  private val context: Context,
  private val speedEngine: VoltSpeedEngine,
  private val mainScope: CoroutineScope,
) {
  private data class AppliedWebSettings(
    val liteModeEnabled: Boolean,
    val darkWebEnabled: Boolean,
    val effectiveDesktopMode: Boolean,
    val effectiveJsEnabled: Boolean,
  )

  private val webViews = LinkedHashMap<String, WebView>(8, 0.75f, true)
  private val savedStateBundles = HashMap<String, Bundle>()
  private val incognitoTabIds = HashSet<String>()
  private val appliedWebSettings = HashMap<String, AppliedWebSettings>()
  private val pageStartTimestamps = HashMap<String, Long>()
  private val currentUrls = HashMap<String, String>()
  private val currentTitles = HashMap<String, String>()
  private val lastDomTouchTargets = ConcurrentHashMap<String, Pair<Long, WebContextMenuTarget>>()
  private val upgradedFromHttpTabs = ConcurrentHashMap<String, Pair<String, String>>()
  private val httpsOnlyBypassedHosts: MutableSet<String> = ConcurrentHashMap.newKeySet()
  private val publicProxyHttpBypassHosts: MutableSet<String> = ConcurrentHashMap.newKeySet()
  private val persistedSitePermissions = ConcurrentHashMap<String, Boolean>()
  private val sessionSitePermissions = ConcurrentHashMap<String, Boolean>()
  private val siteHostOverrides = ConcurrentHashMap<String, SiteHostOverride>()

  @Volatile var currentSettings: BrowserSettings = BrowserSettings()
  @Volatile var isKillSwitchBlocking: Boolean = false
  var proxyAuthTargetProvider: (() -> VoltSpeedEngine.ProxyAuthTarget?)? = null
  var proxyCredentialsProvider: (() -> Pair<String, String>?)? = null
  var isPublicRelayActiveProvider: (() -> Boolean)? = null

  private fun resolveAppliedWebSettings(
    settings: BrowserSettings,
    siteOverride: SiteHostOverride?,
  ): AppliedWebSettings =
    AppliedWebSettings(
      liteModeEnabled = settings.liteModeEnabled,
      darkWebEnabled = settings.darkWebEnabled,
      effectiveDesktopMode = siteOverride?.desktopMode ?: settings.desktopModeEnabled,
      effectiveJsEnabled = siteOverride?.jsEnabled ?: true,
    )

  fun getSiteOverrideForUrl(urlOrHost: String?): SiteHostOverride? {
    val key = normalizeHostKey(urlOrHost)
    if (key.isEmpty()) return null
    return siteHostOverrides[key]
  }

  fun syncSiteHostOverrides(entities: List<SiteSettingEntity>) {
    siteHostOverrides.clear()
    entities.forEach { entity ->
      if (entity.host.isNotBlank()) {
        siteHostOverrides[entity.host] =
          SiteHostOverride(jsEnabled = entity.jsEnabled, desktopMode = entity.desktopMode)
      }
    }
    refreshSettings(currentSettings)
  }

  fun syncPersistedSitePermissions(entities: List<SitePermissionEntity>) {
    persistedSitePermissions.clear()
    entities.forEach { entity ->
      persistedSitePermissions["${entity.host}|${entity.permissionType}"] = entity.granted
    }
  }

  fun allowInsecureHttpHost(host: String) {
    val clean = normalizeHostKey(host)
    if (clean.isNotEmpty()) {
      httpsOnlyBypassedHosts.add(clean)
      publicProxyHttpBypassHosts.add(clean)
    }
  }

  fun stopAllLoads() {
    mainScope.launch(Dispatchers.Main) {
      webViews.values.forEach { wv -> runCatching { wv.stopLoading() } }
    }
  }

  sealed interface OutgoingNavigationDecision {
    data class LoadUrl(val url: String) : OutgoingNavigationDecision
    data class WarnPublicProxyHttp(val httpUrl: String) : OutgoingNavigationDecision
    data object BlockedByKillSwitch : OutgoingNavigationDecision
  }

  fun resolveOutgoingNavigation(tabId: String, rawUrl: String): OutgoingNavigationDecision {
    if (isKillSwitchBlocking) return OutgoingNavigationDecision.BlockedByKillSwitch
    if (rawUrl.startsWith("http://", ignoreCase = true)) {
      val host = normalizeHostKey(rawUrl)
      if (currentSettings.httpsOnlyEnabled && host.isNotEmpty() && !httpsOnlyBypassedHosts.contains(host)) {
        val upgraded = upgradeHttpToHttps(rawUrl)
        upgradedFromHttpTabs[tabId] = rawUrl to upgraded
        return OutgoingNavigationDecision.LoadUrl(upgraded)
      }
      if (isPublicRelayActiveProvider?.invoke() == true && host.isNotEmpty() && !publicProxyHttpBypassHosts.contains(host)) {
        return OutgoingNavigationDecision.WarnPublicProxyHttp(rawUrl)
      }
    }
    return OutgoingNavigationDecision.LoadUrl(rawUrl)
  }

  fun getOrCreateWebView(
    tabId: String,
    isIncognito: Boolean,
    callbacks: TabWebCallbacks,
    fallbackUrl: String? = null,
  ): WebView {
    webViews[tabId]?.let { existing ->
      val override = getSiteOverrideForUrl(currentUrls[tabId] ?: fallbackUrl)
      val target = resolveAppliedWebSettings(currentSettings, override)
      if (appliedWebSettings[tabId] != target) {
        speedEngine.configureWebViewSettings(
          existing,
          currentSettings,
          isIncognito = isIncognito,
          siteOverride = override,
        )
        appliedWebSettings[tabId] = target
      }
      return existing
    }

    if (webViews.size >= MAX_LIVE_WEBVIEWS) {
      evictEldestWebView(excludingTabId = tabId)
    }

    val webView = WebView(context)
    webView.layoutParams =
      ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
      )

    if (isIncognito) {
      incognitoTabIds.add(tabId)
      if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
        runCatching {
          WebViewCompat.setProfile(webView, PRIVATE_PROFILE_NAME)
          val profile = ProfileStore.getInstance().getOrCreateProfile(PRIVATE_PROFILE_NAME)
          profile.cookieManager.setAcceptCookie(true)
          profile.cookieManager.setAcceptThirdPartyCookies(webView, false)
        }.onFailure {
          CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
        }
      } else {
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
      }
    } else {
      CookieManager.getInstance().setAcceptCookie(true)
      CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
    }

    val initialOverride = getSiteOverrideForUrl(fallbackUrl)
    speedEngine.configureWebViewSettings(
      webView,
      currentSettings,
      isIncognito = isIncognito,
      siteOverride = initialOverride,
    )
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
      webView.importantForAutofill =
        if (isIncognito) View.IMPORTANT_FOR_AUTOFILL_NO
        else View.IMPORTANT_FOR_AUTOFILL_YES
    }

    val jsBridge =
      VoltMediaJsBridge(
        onDetected = { items -> mainScope.launch(Dispatchers.Main) { callbacks.onMediaDetected(tabId, items) } },
        currentPageUrlProvider = { currentUrls[tabId].orEmpty() },
        onBlobDownloaded = { dataUrl, suggestedName, mimeType ->
          mainScope.launch(Dispatchers.Main) {
            val disposition =
              when {
                suggestedName.isBlank() -> ""
                suggestedName.contains("filename", ignoreCase = true) -> suggestedName
                else -> "attachment; filename=\"$suggestedName\""
              }
            callbacks.onDownloadRequested(
              url = dataUrl,
              userAgent = "",
              contentDisposition = disposition,
              mimeType = mimeType,
            )
          }
        },
        onTouchTarget = { linkUrl, linkText, imageUrl, mediaUrl, mediaTitle ->
          lastDomTouchTargets[tabId] =
            System.currentTimeMillis() to
              WebContextMenuTarget(
                pageUrl = currentUrls[tabId].orEmpty(),
                linkUrl = linkUrl,
                linkText = linkText,
                imageUrl = imageUrl,
                mediaUrl = mediaUrl,
                mediaTitle = mediaTitle,
              )
        },
      )

    if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
      // Media sniffer & touch context must operate on arbitrary user-visited websites
      WebViewCompat.addWebMessageListener(
        webView,
        "VoltMediaChannel",
        setOf("*"),
      ) { _, message, _, _, _ ->
        val payload = message.data ?: return@addWebMessageListener
        jsBridge.handleWebMessage(payload)
      }
    } else {
      webView.addJavascriptInterface(jsBridge, "VoltMediaBridge")
    }

    webView.webViewClient =
      object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
          val uri = request?.url ?: return false
          val url = uri.toString()
          if (isKillSwitchBlocking) return true
          if (!(url.startsWith("http://") || url.startsWith("https://") || url.startsWith("about:"))) {
            return true
          }
          val isMainFrame = request.isForMainFrame

          if (url.startsWith("http://", ignoreCase = true)) {
            val host = normalizeHostKey(url)
            if (currentSettings.httpsOnlyEnabled && host.isNotEmpty() && !httpsOnlyBypassedHosts.contains(host)) {
              val upgraded = upgradeHttpToHttps(url)
              upgradedFromHttpTabs[tabId] = url to upgraded
              view?.loadUrl(upgraded)
              return true
            }
            if (isPublicRelayActiveProvider?.invoke() == true &&
              host.isNotEmpty() &&
              !publicProxyHttpBypassHosts.contains(host)
            ) {
              mainScope.launch(Dispatchers.Main) {
                callbacks.onPublicProxyHttpWarning(tabId, url)
              }
              return true
            }
          }
          return false
        }

        override fun shouldInterceptRequest(
          view: WebView?,
          request: WebResourceRequest?,
        ): WebResourceResponse? {
          if (isKillSwitchBlocking) {
            return WebResourceResponse(
              "text/plain",
              "utf-8",
              503,
              "Blocked by VPN Kill Switch",
              mapOf("Cache-Control" to "no-store"),
              ByteArrayInputStream(ByteArray(0)),
            )
          }
          val uri = request?.url ?: return null
          val isMainFrame = request.isForMainFrame
          val pageUrl = currentUrls[tabId].orEmpty()
          val isSiteBypassed = isHostMediaCompatBypassed(pageUrl)
          val headers = request.requestHeaders.orEmpty()

          if (!isMainFrame && !isSiteBypassed && currentSettings.liteModeEnabled) {
            val (shouldBlockData, savedBytes) = VoltDataSaver.shouldBlockForDataSaver(uri, headers)
            if (shouldBlockData) {
              val isImage = savedBytes == VoltDataSaver.ESTIMATED_IMAGE_BYTES
              mainScope.launch(Dispatchers.Main) {
                callbacks.onTrackerBlocked(tabId, savedBytes)
              }
              return VoltDataSaver.emptySaverResponse(isImage = isImage)
            }
          }



          val urlString = uri.toString()
          val pageTitle = currentTitles[tabId].orEmpty()
          val requestReferer =
            headers.entries.firstOrNull { it.key.equals("Referer", ignoreCase = true) }?.value
              ?: pageUrl
          val detected =
            MediaSniffer.classifyUrl(urlString, requestReferer, pageTitle)
              ?: classifyExtensionlessVideoRequest(urlString, headers, requestReferer, pageTitle)
          if (detected != null) {
            mainScope.launch(Dispatchers.Main) {
              callbacks.onMediaDetected(tabId, listOf(detected))
            }
          }

          return super.shouldInterceptRequest(view, request)
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
          super.onPageStarted(view, url, favicon)
          val resolvedUrl = url.orEmpty()
          currentUrls[tabId] = resolvedUrl
          pageStartTimestamps[tabId] = System.currentTimeMillis()
          val override = getSiteOverrideForUrl(resolvedUrl)
          val target = resolveAppliedWebSettings(currentSettings, override)
          if (view != null && appliedWebSettings[tabId] != target) {
            speedEngine.configureWebViewSettings(
              view,
              currentSettings,
              isIncognito = isIncognito || tabId in incognitoTabIds,
              siteOverride = override,
            )
            appliedWebSettings[tabId] = target
          }
          callbacks.onPageStarted(tabId, resolvedUrl)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
          super.onPageFinished(view, url)
          val resolvedUrl = url.orEmpty()
          currentUrls[tabId] = resolvedUrl
          val title = view?.title.orEmpty().ifBlank { resolvedUrl }
          currentTitles[tabId] = title
          val startedAt = pageStartTimestamps.remove(tabId) ?: System.currentTimeMillis()
          val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)


          view?.evaluateJavascript(MediaSniffer.domSnifferScript, null)

          if (resolvedUrl.startsWith("https://", ignoreCase = true)) {
            val pending = upgradedFromHttpTabs[tabId]
            if (pending != null && normalizeHostKey(pending.second) != normalizeHostKey(resolvedUrl)) {
              upgradedFromHttpTabs.remove(tabId)
            }
          }
          callbacks.onPageFinished(
            tabId = tabId,
            url = resolvedUrl,
            title = title,
            loadTimeMs = elapsed,
            canGoBack = view?.canGoBack() == true,
            canGoForward = view?.canGoForward() == true,
          )
        }

        override fun onReceivedError(
          view: WebView?,
          request: WebResourceRequest?,
          error: WebResourceError?,
        ) {
          super.onReceivedError(view, request, error)
          if (request?.isForMainFrame == true && error != null) {
            val failedUrl = request.url?.toString().orEmpty()
            val pendingUpgrade = upgradedFromHttpTabs[tabId]
            if (pendingUpgrade != null &&
              normalizeHostKey(pendingUpgrade.second) == normalizeHostKey(failedUrl)
            ) {
              upgradedFromHttpTabs.remove(tabId)
              val reason = error.description?.toString().orEmpty().ifBlank { "HTTPS connection failed" }
              mainScope.launch(Dispatchers.Main) {
                callbacks.onHttpsUpgradeFailed(
                  tabId = tabId,
                  failedHttpsUrl = failedUrl,
                  fallbackHttpUrl = pendingUpgrade.first,
                  reason = reason,
                )
              }
            }
            callbacks.onMainFrameNetworkError(
              tabId = tabId,
              errorCode = error.errorCode,
              description = error.description?.toString().orEmpty(),
            )
          }
        }

        override fun onReceivedSslError(
          view: WebView?,
          handler: SslErrorHandler?,
          error: SslError?,
        ) {
          handler?.cancel()
          val failedUrl = error?.url.orEmpty()
          val pendingUpgrade = upgradedFromHttpTabs[tabId]
          if (pendingUpgrade != null &&
            (failedUrl.isBlank() || normalizeHostKey(pendingUpgrade.second) == normalizeHostKey(failedUrl))
          ) {
            upgradedFromHttpTabs.remove(tabId)
            mainScope.launch(Dispatchers.Main) {
              callbacks.onHttpsUpgradeFailed(
                tabId = tabId,
                failedHttpsUrl = pendingUpgrade.second,
                fallbackHttpUrl = pendingUpgrade.first,
                reason = "TLS/SSL certificate validation failed",
              )
            }
          }
        }

        override fun onReceivedHttpAuthRequest(
          view: WebView?,
          handler: HttpAuthHandler?,
          host: String?,
          realm: String?,
        ) {
          val authTarget = proxyAuthTargetProvider?.invoke() ?: speedEngine.getActiveProxyAuthTarget()
          if (authTarget != null && handler != null && shouldProvideProxyCredentials(host, authTarget.host)) {
            handler.proceed(authTarget.username, authTarget.password)
          } else {
            super.onReceivedHttpAuthRequest(view, handler, host, realm)
          }
        }
      }

    webView.webChromeClient =
      object : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
          callbacks.onProgressChanged(tabId, newProgress)
        }

        override fun onReceivedTitle(view: WebView?, title: String?) {
          if (!title.isNullOrBlank()) {
            currentTitles[tabId] = title
            callbacks.onTitleReceived(tabId, title)
          }
        }

        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
          if (view != null && callback != null) {
            callbacks.onShowFullscreenView(view, callback)
          }
        }

        override fun onHideCustomView() {
          callbacks.onHideFullscreenView()
        }

        override fun onPermissionRequest(request: PermissionRequest?) {
          request ?: return
          val originUrl = request.origin?.toString().orEmpty().ifBlank { currentUrls[tabId].orEmpty() }
          val host = normalizeHostKey(originUrl).ifBlank { normalizeHostKey(currentUrls[tabId]) }
          val requestedResources = request.resources.orEmpty()

          val grantableProtected =
            if (isHostMediaCompatBypassed(originUrl) &&
              requestedResources.contains(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID)
            ) {
              listOf(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID)
            } else {
              emptyList()
            }

          val siteTypes = linkedSetOf<SitePermissionType>()
          val mappedResources = mutableListOf<String>()
          if (requestedResources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
            siteTypes.add(SitePermissionType.CAMERA)
            mappedResources.add(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
          }
          if (requestedResources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
            siteTypes.add(SitePermissionType.MICROPHONE)
            mappedResources.add(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
          }

          if (siteTypes.isEmpty()) {
            if (grantableProtected.isNotEmpty()) {
              request.grant(grantableProtected.toTypedArray())
            } else {
              request.deny()
            }
            return
          }

          val tabIncognito = isIncognito || tabId in incognitoTabIds
          val decisions = siteTypes.map { type -> type to lookupPermissionDecision(tabId, host, type, tabIncognito) }
          if (decisions.any { it.second == false }) {
            request.deny()
            return
          }
          if (decisions.all { it.second == true }) {
            callbacks.onEnsureRuntimePermissions(siteTypes) { osGranted ->
              if (osGranted) {
                request.grant((mappedResources + grantableProtected).distinct().toTypedArray())
              } else {
                request.deny()
              }
            }
            return
          }

          callbacks.onSitePermissionRequested(
            tabId = tabId,
            origin = originUrl,
            host = host.ifBlank { "this site" },
            isIncognito = tabIncognito,
            requestedTypes = siteTypes,
          ) { granted, rememberForSite ->
            if (!granted) {
              if (rememberForSite && host.isNotBlank()) {
                recordPermissionDecision(tabId, host, siteTypes, granted = false, isIncognito = tabIncognito, callbacks = callbacks)
              }
              request.deny()
            } else {
              callbacks.onEnsureRuntimePermissions(siteTypes) { osGranted ->
                if (osGranted) {
                  if (rememberForSite && host.isNotBlank()) {
                    recordPermissionDecision(tabId, host, siteTypes, granted = true, isIncognito = tabIncognito, callbacks = callbacks)
                  }
                  request.grant((mappedResources + grantableProtected).distinct().toTypedArray())
                } else {
                  request.deny()
                }
              }
            }
          }
        }

        override fun onPermissionRequestCanceled(request: PermissionRequest?) {
          super.onPermissionRequestCanceled(request)
          callbacks.onSitePermissionCanceled(tabId)
        }

        override fun onGeolocationPermissionsShowPrompt(
          origin: String?,
          callback: GeolocationPermissions.Callback?,
        ) {
          callback ?: return
          val rawOrigin = origin.orEmpty().ifBlank { currentUrls[tabId].orEmpty() }
          val host = normalizeHostKey(rawOrigin).ifBlank { normalizeHostKey(currentUrls[tabId]) }
          if (host.isBlank()) {
            callback.invoke(origin, false, false)
            return
          }
          val tabIncognito = isIncognito || tabId in incognitoTabIds
          val existing = lookupPermissionDecision(tabId, host, SitePermissionType.LOCATION, tabIncognito)
          if (existing == false) {
            callback.invoke(origin, false, false)
            return
          }
          if (existing == true) {
            callbacks.onEnsureRuntimePermissions(setOf(SitePermissionType.LOCATION)) { osGranted ->
              callback.invoke(origin, osGranted, false)
            }
            return
          }
          callbacks.onSitePermissionRequested(
            tabId = tabId,
            origin = rawOrigin,
            host = host,
            isIncognito = tabIncognito,
            requestedTypes = setOf(SitePermissionType.LOCATION),
          ) { granted, rememberForSite ->
            if (!granted) {
              if (rememberForSite) {
                recordPermissionDecision(
                  tabId = tabId,
                  host = host,
                  types = setOf(SitePermissionType.LOCATION),
                  granted = false,
                  isIncognito = tabIncognito,
                  callbacks = callbacks,
                )
              }
              callback.invoke(origin, false, false)
            } else {
              callbacks.onEnsureRuntimePermissions(setOf(SitePermissionType.LOCATION)) { osGranted ->
                if (osGranted && rememberForSite) {
                  recordPermissionDecision(
                    tabId = tabId,
                    host = host,
                    types = setOf(SitePermissionType.LOCATION),
                    granted = true,
                    isIncognito = tabIncognito,
                    callbacks = callbacks,
                  )
                }
                callback.invoke(origin, osGranted, false)
              }
            }
          }
        }

        override fun onCreateWindow(
          view: WebView?,
          isDialog: Boolean,
          isUserGesture: Boolean,
          resultMsg: android.os.Message?,
        ): Boolean {
          if (isKillSwitchBlocking || !isUserGesture) {
            return false
          }

          val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
          val popupIncognito = isIncognito || tabId in incognitoTabIds
          val newTabId = callbacks.onCreatePopupTabRequested(isIncognito = popupIncognito) ?: return false
          val popupWebView =
            getOrCreateWebView(
              tabId = newTabId,
              isIncognito = popupIncognito,
              callbacks = callbacks,
            )
          transport.webView = popupWebView
          resultMsg.sendToTarget()
          return true
        }
      }

    webView.setDownloadListener(
      DownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
        val rawUrl = url.orEmpty()
        if (rawUrl.startsWith("blob:", ignoreCase = true)) {
          val js =
            """
            (function(bUrl, dispName, mime, maxBytes) {
              fetch(bUrl).then(function(r) { return r.blob(); }).then(function(blob) {
                if (blob.size > maxBytes) { return; }
                var reader = new FileReader();
                reader.onloadend = function() {
                  if (!reader.result) return;
                  var resolvedMime = blob.type || mime || '';
                  if (window.VoltMediaChannel && typeof window.VoltMediaChannel.postMessage === 'function') {
                    window.VoltMediaChannel.postMessage(JSON.stringify({
                      type: 'blobDownload',
                      payload: { dataUrl: reader.result, suggestedName: dispName, mimeType: resolvedMime }
                    }));
                  } else if (window.VoltMediaBridge && window.VoltMediaBridge.onBlobDownloadData) {
                    window.VoltMediaBridge.onBlobDownloadData(reader.result, dispName, resolvedMime);
                  }
                };
                reader.readAsDataURL(blob);
              }).catch(function() {});
            })(${JSONObject.quote(rawUrl)}, ${JSONObject.quote(contentDisposition.orEmpty())}, ${JSONObject.quote(mimetype.orEmpty())}, $MAX_BLOB_DOWNLOAD_BYTES);
            """.trimIndent()
          webView.evaluateJavascript(js, null)
        } else {
          callbacks.onDownloadRequested(
            url = rawUrl,
            userAgent = userAgent.orEmpty(),
            contentDisposition = contentDisposition.orEmpty(),
            mimeType = mimetype.orEmpty(),
          )
        }
      }
    )

    webView.isLongClickable = true
    webView.setOnLongClickListener { view ->
      val hit = (view as? WebView)?.hitTestResult
      if (hit?.type == WebView.HitTestResult.EDIT_TEXT_TYPE) {
        return@setOnLongClickListener false
      }
      val freshDom =
        lastDomTouchTargets[tabId]
          ?.takeIf { System.currentTimeMillis() - it.first <= 2_500L }
          ?.second
      val pageUrl = currentUrls[tabId].orEmpty()
      val target =
        resolveContextTarget(
          pageUrl = pageUrl,
          hitType = hit?.type ?: WebView.HitTestResult.UNKNOWN_TYPE,
          hitExtra = hit?.extra,
          domTarget = freshDom,
        )
      if (target != null && target.hasAnyTarget) {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        callbacks.onContextMenuRequested(tabId, target)
        true
      } else {
        false
      }
    }

    webViews[tabId] = webView
    appliedWebSettings[tabId] = resolveAppliedWebSettings(currentSettings, initialOverride)

    val savedBundle = savedStateBundles.remove(tabId)
    val restored = if (savedBundle != null) webView.restoreState(savedBundle) != null else false
    if (!restored && !fallbackUrl.isNullOrBlank()) {
      webView.loadUrl(fallbackUrl)
    }
    return webView
  }

  @OptIn(WebViewCompat.ExperimentalSaveState::class)
  @androidx.annotation.OptIn(markerClass = [WebViewCompat.ExperimentalSaveState::class])
  private fun evictEldestWebView(excludingTabId: String) {
    val eldestEntry = webViews.entries.firstOrNull { it.key != excludingTabId } ?: return
    val evictId = eldestEntry.key
    val wv = webViews.remove(evictId) ?: return
    val stateBundle = Bundle()
    val saved =
      if (WebViewFeature.isFeatureSupported(WebViewFeature.SAVE_STATE)) {
        runCatching { WebViewCompat.saveState(wv, stateBundle, MAX_SAVED_STATE_BYTES, false) }.isSuccess
      } else {
        wv.saveState(stateBundle) != null
      }
    if (saved && !stateBundle.isEmpty) {
      savedStateBundles[evictId] = stateBundle
    }
    (wv.parent as? ViewGroup)?.removeView(wv)
    wv.stopLoading()
    speedEngine.wipeEphemeralCacheAndStorage(wv, wipeGlobalProfileStorage = false)
    wv.removeAllViews()
    wv.destroy()
    appliedWebSettings.remove(evictId)
    pageStartTimestamps.remove(evictId)
  }

  fun findWebView(tabId: String): WebView? = webViews[tabId]

  fun requestAutofill(tabId: String): Boolean {
    val webView = webViews[tabId] ?: return false
    webView.requestFocus()
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
      val manager = context.getSystemService(android.view.autofill.AutofillManager::class.java)
      if (manager != null && manager.isEnabled) {
        manager.requestAutofill(webView)
        return true
      }
    }
    return false
  }

  fun triggerMediaScan(tabId: String) {
    webViews[tabId]?.evaluateJavascript(MediaSniffer.domSnifferScript, null)
  }

  fun refreshSettings(settings: BrowserSettings) {
    currentSettings = settings
    webViews.forEach { (tabId, wv) ->
      val override = getSiteOverrideForUrl(currentUrls[tabId])
      val target = resolveAppliedWebSettings(settings, override)
      if (appliedWebSettings[tabId] != target) {
        speedEngine.configureWebViewSettings(
          wv,
          settings,
          isIncognito = tabId in incognitoTabIds,
          siteOverride = override,
        )
        appliedWebSettings[tabId] = target
      }
    }
  }

  private fun lookupPermissionDecision(
    tabId: String,
    host: String,
    type: SitePermissionType,
    isIncognito: Boolean,
  ): Boolean? {
    if (host.isBlank()) return null
    val sessionKey = "$tabId|$host|${type.name}"
    sessionSitePermissions[sessionKey]?.let { return it }
    if (isIncognito) return null
    return persistedSitePermissions["$host|${type.name}"]
  }

  private fun recordPermissionDecision(
    tabId: String,
    host: String,
    types: Set<SitePermissionType>,
    granted: Boolean,
    isIncognito: Boolean,
    callbacks: TabWebCallbacks,
  ) {
    types.forEach { type ->
      if (isIncognito) {
        sessionSitePermissions["$tabId|$host|${type.name}"] = granted
      } else {
        persistedSitePermissions["$host|${type.name}"] = granted
        callbacks.onPersistSitePermission(host, type, granted)
      }
    }
  }

  private fun wipePrivateProfileStorage() {
    if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
      runCatching {
        val profile = ProfileStore.getInstance().getOrCreateProfile(PRIVATE_PROFILE_NAME)
        profile.cookieManager.removeAllCookies(null)
        profile.cookieManager.removeSessionCookies(null)
        profile.cookieManager.flush()
        profile.webStorage.deleteAllData()
      }
    }
  }

  fun wipeAllCacheAndCookies() {
    savedStateBundles.clear()
    mediaCompatBypassHosts.clear()
    httpsOnlyBypassedHosts.clear()
    publicProxyHttpBypassHosts.clear()
    sessionSitePermissions.clear()
    upgradedFromHttpTabs.clear()
    webViews.values.forEach { speedEngine.wipeEphemeralCacheAndStorage(it, wipeGlobalProfileStorage = false) }
    wipePrivateProfileStorage()
    speedEngine.wipeEphemeralCacheAndStorage(null, wipeGlobalProfileStorage = true)
  }

  fun destroyTab(tabId: String) {
    savedStateBundles.remove(tabId)
    val wasIncognito = incognitoTabIds.remove(tabId)
    webViews.remove(tabId)?.let { wv ->
      (wv.parent as? ViewGroup)?.removeView(wv)
      wv.stopLoading()
      speedEngine.wipeEphemeralCacheAndStorage(wv, wipeGlobalProfileStorage = false)
      wv.removeAllViews()
      wv.destroy()
    }
    if (wasIncognito && incognitoTabIds.isEmpty()) {
      wipePrivateProfileStorage()
    }
    appliedWebSettings.remove(tabId)
    pageStartTimestamps.remove(tabId)
    currentUrls.remove(tabId)
    currentTitles.remove(tabId)
    lastDomTouchTargets.remove(tabId)
    upgradedFromHttpTabs.remove(tabId)
    sessionSitePermissions.keys.removeAll { it.startsWith("$tabId|") }
  }

  fun destroyAllTabs() {
    val ids = webViews.keys.toList()
    ids.forEach { destroyTab(it) }
    savedStateBundles.clear()
    incognitoTabIds.clear()
    mediaCompatBypassHosts.clear()
    wipeAllCacheAndCookies()
    appliedWebSettings.clear()
    pageStartTimestamps.clear()
    currentUrls.clear()
    currentTitles.clear()
    lastDomTouchTargets.clear()
  }

  private fun classifyExtensionlessVideoRequest(
    url: String,
    headers: Map<String, String>,
    referer: String,
    pageTitle: String,
  ): DetectedMediaItem? {
    if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) return null
    val cleanPath = url.substringBefore('?').substringBefore('#').lowercase(Locale.US)
    val ext = cleanPath.substringAfterLast('.', "")
    if (ext in NON_VIDEO_EXTENSIONS) return null

    val hasRange = headers.keys.any { it.equals("Range", ignoreCase = true) }
    val hasVideoAccept =
      headers.entries.any { (k, v) ->
        k.equals("Accept", ignoreCase = true) && v.contains("video/", ignoreCase = true)
      }
    if (!hasRange && !hasVideoAccept) return null

    val title = pageTitle.ifBlank { "Web Video" }
    return DetectedMediaItem(
      url = url,
      title = title,
      kind = MediaKind.VIDEO,
      mimeType = "video/mp4",
      sourcePageUrl = referer,
    )
  }

  companion object {
    const val MAX_LIVE_WEBVIEWS = 5
    const val PRIVATE_PROFILE_NAME = "volt_private"
    const val MAX_SAVED_STATE_BYTES = 256 * 1024
    const val MAX_BLOB_DOWNLOAD_BYTES = 25 * 1024 * 1024

    private val mediaCompatBypassHosts: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun normalizeHostKey(urlOrHost: String?): String =
      WebContextTargetResolver.normalizeHostKey(urlOrHost)

    fun setHostMediaCompatBypass(urlOrHost: String?, enabled: Boolean) {
      val key = normalizeHostKey(urlOrHost)
      if (key.isEmpty()) return
      if (enabled) mediaCompatBypassHosts.add(key) else mediaCompatBypassHosts.remove(key)
    }

    fun isHostMediaCompatBypassed(urlOrHost: String?): Boolean {
      val key = normalizeHostKey(urlOrHost)
      if (key.isEmpty()) return false
      return mediaCompatBypassHosts.contains(key)
    }

    fun upgradeHttpToHttps(url: String): String =
      WebContextTargetResolver.upgradeHttpToHttps(url)

    fun clearMediaCompatBypassHosts() {
      mediaCompatBypassHosts.clear()
    }

    fun shouldProvideProxyCredentials(challengeHost: String?, activeProxyHost: String?): Boolean =
      WebContextTargetResolver.shouldProvideProxyCredentials(challengeHost, activeProxyHost)

    fun resolveContextTarget(
      pageUrl: String,
      hitType: Int,
      hitExtra: String?,
      domTarget: WebContextMenuTarget?,
    ): WebContextMenuTarget? =
      WebContextTargetResolver.resolveContextTarget(pageUrl, hitType, hitExtra, domTarget)

    private val NON_VIDEO_EXTENSIONS =
      setOf(
        "ts", "m4s", "mp3", "aac", "m4a", "ogg", "opus", "flac", "wav",
        "vtt", "srt", "ass", "ttml",
        "js", "css", "html", "htm", "json", "xml", "txt", "pdf",
        "png", "jpg", "jpeg", "gif", "webp", "svg", "ico", "avif",
        "woff", "woff2", "ttf", "otf", "eot",
      )
  }
}
