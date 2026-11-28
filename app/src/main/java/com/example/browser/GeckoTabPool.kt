package com.example.browser

import android.Manifest
import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import com.example.data.AdBlockerType
import com.example.data.BrowserSettings
import com.example.data.MediaKind
import com.example.data.SiteHostOverride
import com.example.data.SitePermissionEntity
import com.example.data.SitePermissionType
import com.example.data.SiteSettingEntity
import com.example.data.WebContextMenuTarget
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.ContentDelegate
import org.mozilla.geckoview.GeckoSession.NavigationDelegate
import org.mozilla.geckoview.GeckoSession.PermissionDelegate
import org.mozilla.geckoview.GeckoSession.ProgressDelegate
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebRequestError

/**
 * Manages active [GeckoSession] instances across Standard and Private tabs, mirroring Volt's
 * security/privacy controls (HTTPS-Only, Kill Switch, Data Saver, ContentBlocking tracking
 * protection, site JS/Desktop overrides, permissions, context menus, and `.xpi` interception).
 */
class GeckoTabPool(
  private val context: Context,
  private val addonManager: FirefoxAddonManager,
  private val speedEngine: VoltSpeedEngine,
  private val mainScope: CoroutineScope,
) {
  private val sessions = LinkedHashMap<String, GeckoSession>(8, 0.75f, true)
  private val incognitoTabIds = HashSet<String>()
  private val currentUrls = HashMap<String, String>()
  private val currentTitles = HashMap<String, String>()
  private val pageStartTimestamps = HashMap<String, Long>()
  private val canGoBackMap = HashMap<String, Boolean>()
  private val canGoForwardMap = HashMap<String, Boolean>()
  private val upgradedFromHttpTabs = ConcurrentHashMap<String, Pair<String, String>>()
  private val httpsOnlyBypassedHosts: MutableSet<String> = ConcurrentHashMap.newKeySet()
  private val publicProxyHttpBypassHosts: MutableSet<String> = ConcurrentHashMap.newKeySet()
  private val persistedSitePermissions = ConcurrentHashMap<String, Boolean>()
  private val sessionSitePermissions = ConcurrentHashMap<String, Boolean>()
  private val siteHostOverrides = ConcurrentHashMap<String, SiteHostOverride>()
  private val pendingXpiCallback = ConcurrentHashMap<String, (String) -> Unit>()

  @Volatile var currentSettings: BrowserSettings = BrowserSettings()
  @Volatile var isKillSwitchBlocking: Boolean = false
  var isPublicRelayActiveProvider: (() -> Boolean)? = null
  var onXpiIntercepted: ((String) -> Unit)? = null
  var onFullscreenChanged: ((Boolean) -> Unit)? = null

  private var reusableGeckoView: GeckoView? = null

  fun attachView(session: GeckoSession): GeckoView {
    val existing = reusableGeckoView
    if (existing != null) {
      if (existing.session !== session) {
        existing.releaseSession()
        existing.setSession(session)
      }
      return existing
    }
    val gv = GeckoView(context)
    gv.layoutParams =
      ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
      )
    gv.setSession(session)
    reusableGeckoView = gv
    return gv
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
    val clean = WebContextTargetResolver.normalizeHostKey(host)
    if (clean.isNotEmpty()) {
      httpsOnlyBypassedHosts.add(clean)
      publicProxyHttpBypassHosts.add(clean)
    }
  }

  fun canGoBack(tabId: String): Boolean = canGoBackMap[tabId] == true

  fun canGoForward(tabId: String): Boolean = canGoForwardMap[tabId] == true

  fun findSession(tabId: String): GeckoSession? = sessions[tabId]

  fun stopAllLoads() {
    mainScope.launch(Dispatchers.Main) {
      sessions.values.forEach { session -> runCatching { session.stop() } }
    }
  }

  fun refreshSettings(settings: BrowserSettings) {
    currentSettings = settings
    if (addonManager.isRuntimeInitialized()) {
      addonManager.syncRuntimeSettings(settings)
    }
    sessions.forEach { (tabId, session) ->
      applySessionSettings(session, settings, siteHostOverrides[WebContextTargetResolver.normalizeHostKey(currentUrls[tabId])])
    }
  }

  private fun applySessionSettings(
    session: GeckoSession,
    settings: BrowserSettings,
    override: SiteHostOverride?,
  ) {
    val (effectiveJs, effectiveDesktop) = VoltSpeedEngine.resolveEffectiveSiteSettings(settings, override)
    val s = session.settings
    s.allowJavascript = effectiveJs
    s.useTrackingProtection = settings.adBlockerType != AdBlockerType.OFF
    s.userAgentMode =
      if (effectiveDesktop) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
      else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
  }

  fun getOrCreateSession(
    tabId: String,
    isIncognito: Boolean,
    callbacks: TabWebCallbacks,
    fallbackUrl: String? = null,
  ): GeckoSession {
    sessions[tabId]?.let { existing ->
      val override = siteHostOverrides[WebContextTargetResolver.normalizeHostKey(currentUrls[tabId] ?: fallbackUrl)]
      applySessionSettings(existing, currentSettings, override)
      return existing
    }

    val runtime = addonManager.ensureRuntime(currentSettings)
    val initialOverride = siteHostOverrides[WebContextTargetResolver.normalizeHostKey(fallbackUrl)]
    val (effectiveJs, effectiveDesktop) = VoltSpeedEngine.resolveEffectiveSiteSettings(currentSettings, initialOverride)

    val sessionSettings =
      GeckoSessionSettings.Builder()
        .usePrivateMode(isIncognito)
        .useTrackingProtection(currentSettings.adBlockerType != AdBlockerType.OFF)
        .allowJavascript(effectiveJs)
        .userAgentMode(
          if (effectiveDesktop) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
          else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        )
        .build()

    val session = GeckoSession(sessionSettings)
    if (isIncognito) {
      incognitoTabIds.add(tabId)
    }

    session.navigationDelegate =
      object : NavigationDelegate {
        override fun onLocationChange(
          session: GeckoSession,
          url: String?,
          perms: MutableList<PermissionDelegate.ContentPermission>,
          hasUserGesture: Boolean,
        ) {
          val resolved = url.orEmpty()
          currentUrls[tabId] = resolved
          val hostOverride = siteHostOverrides[WebContextTargetResolver.normalizeHostKey(resolved)]
          applySessionSettings(session, currentSettings, hostOverride)
        }

        override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
          canGoBackMap[tabId] = canGoBack
        }

        override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
          canGoForwardMap[tabId] = canGoForward
        }

        override fun onLoadRequest(
          session: GeckoSession,
          request: NavigationDelegate.LoadRequest,
        ): GeckoResult<AllowOrDeny> {
          val uri = request.uri
          if (isKillSwitchBlocking) {
            return GeckoResult.fromValue(AllowOrDeny.DENY)
          }
          if (FirefoxAddonManager.isXpiUrl(uri)) {
            mainScope.launch(Dispatchers.Main) {
              onXpiIntercepted?.invoke(uri) ?: addonManager.installFromUrl(uri, currentSettings)
            }
            return GeckoResult.fromValue(AllowOrDeny.DENY)
          }
          if (uri.startsWith("http://", ignoreCase = true)) {
            val host = WebContextTargetResolver.normalizeHostKey(uri)
            if (currentSettings.httpsOnlyEnabled && host.isNotEmpty() && !httpsOnlyBypassedHosts.contains(host)) {
              val upgraded = WebContextTargetResolver.upgradeHttpToHttps(uri)
              upgradedFromHttpTabs[tabId] = uri to upgraded
              mainScope.launch(Dispatchers.Main) { session.loadUri(upgraded) }
              return GeckoResult.fromValue(AllowOrDeny.DENY)
            }
            if (isPublicRelayActiveProvider?.invoke() == true &&
              host.isNotEmpty() &&
              !publicProxyHttpBypassHosts.contains(host)
            ) {
              mainScope.launch(Dispatchers.Main) { callbacks.onPublicProxyHttpWarning(tabId, uri) }
              return GeckoResult.fromValue(AllowOrDeny.DENY)
            }
          }
          return GeckoResult.fromValue(AllowOrDeny.ALLOW)
        }

        override fun onNewSession(
          session: GeckoSession,
          uri: String,
        ): GeckoResult<GeckoSession> {
          if (FirefoxAddonManager.isXpiUrl(uri)) {
            mainScope.launch(Dispatchers.Main) {
              onXpiIntercepted?.invoke(uri) ?: addonManager.installFromUrl(uri, currentSettings)
            }
            return GeckoResult.fromValue(null)
          }
          val newTabId = callbacks.onCreatePopupTabRequested(isIncognito || tabId in incognitoTabIds)
            ?: return GeckoResult.fromValue(null)
          val newSession =
            getOrCreateSession(
              tabId = newTabId,
              isIncognito = isIncognito || tabId in incognitoTabIds,
              callbacks = callbacks,
            )
          return GeckoResult.fromValue(newSession)
        }

        override fun onLoadError(
          session: GeckoSession,
          uri: String?,
          error: WebRequestError,
        ): GeckoResult<String> {
          val failedUrl = uri.orEmpty()
          val pendingUpgrade = upgradedFromHttpTabs[tabId]
          if (pendingUpgrade != null &&
            WebContextTargetResolver.normalizeHostKey(pendingUpgrade.second) ==
              WebContextTargetResolver.normalizeHostKey(failedUrl)
          ) {
            upgradedFromHttpTabs.remove(tabId)
            mainScope.launch(Dispatchers.Main) {
              callbacks.onHttpsUpgradeFailed(
                tabId = tabId,
                failedHttpsUrl = failedUrl,
                fallbackHttpUrl = pendingUpgrade.first,
                reason = "HTTPS connection failed (${error.code})",
              )
            }
          } else {
            callbacks.onMainFrameNetworkError(tabId, error.code, "Network error ${error.code}")
          }
          return GeckoResult.fromValue(null)
        }
      }

    session.progressDelegate =
      object : ProgressDelegate {
        override fun onPageStart(session: GeckoSession, url: String) {
          currentUrls[tabId] = url
          pageStartTimestamps[tabId] = System.currentTimeMillis()
          callbacks.onPageStarted(tabId, url)
        }

        override fun onPageStop(session: GeckoSession, success: Boolean) {
          val resolvedUrl = currentUrls[tabId].orEmpty()
          val title = currentTitles[tabId].orEmpty().ifBlank { resolvedUrl }
          val startedAt = pageStartTimestamps.remove(tabId) ?: System.currentTimeMillis()
          val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
          callbacks.onPageFinished(
            tabId = tabId,
            url = resolvedUrl,
            title = title,
            loadTimeMs = elapsed,
            canGoBack = canGoBackMap[tabId] == true,
            canGoForward = canGoForwardMap[tabId] == true,
          )
        }

        override fun onProgressChange(session: GeckoSession, progress: Int) {
          callbacks.onProgressChanged(tabId, progress)
        }

        override fun onSecurityChange(
          session: GeckoSession,
          securityInfo: ProgressDelegate.SecurityInformation,
        ) {
          // Handled via URL + tab state updates
        }
      }

    session.contentDelegate =
      object : ContentDelegate {
        override fun onTitleChange(session: GeckoSession, title: String?) {
          if (!title.isNullOrBlank()) {
            currentTitles[tabId] = title
            callbacks.onTitleReceived(tabId, title)
          }
        }

        override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
          onFullscreenChanged?.invoke(fullScreen)
        }

        override fun onExternalResponse(
          session: GeckoSession,
          response: org.mozilla.geckoview.WebResponse,
        ) {
          val respUrl = response.uri
          val contentType = response.headers["content-type"].orEmpty()
          val disposition = response.headers["content-disposition"].orEmpty()
          if (FirefoxAddonManager.isXpiUrl(respUrl) ||
            contentType.contains("application/x-xpinstall", ignoreCase = true)
          ) {
            mainScope.launch(Dispatchers.Main) {
              onXpiIntercepted?.invoke(respUrl) ?: addonManager.installFromUrl(respUrl, currentSettings)
            }
            return
          }
          callbacks.onDownloadRequested(
            url = respUrl,
            userAgent = "",
            contentDisposition = disposition,
            mimeType = contentType.ifBlank { "application/octet-stream" },
          )
        }

        override fun onContextMenu(
          session: GeckoSession,
          screenX: Int,
          screenY: Int,
          element: ContentDelegate.ContextElement,
        ) {
          val linkUrl = element.linkUri
          val srcUrl = element.srcUri
          val mediaUrl =
            if (element.type == ContentDelegate.ContextElement.TYPE_VIDEO ||
              element.type == ContentDelegate.ContextElement.TYPE_AUDIO
            ) {
              srcUrl
            } else {
              null
            }
          val imageUrl =
            if (element.type == ContentDelegate.ContextElement.TYPE_IMAGE) srcUrl else null
          val kind =
            when (element.type) {
              ContentDelegate.ContextElement.TYPE_AUDIO -> MediaKind.AUDIO
              ContentDelegate.ContextElement.TYPE_VIDEO -> MediaKind.VIDEO
              else -> MediaSniffer.classifyUrl(linkUrl ?: srcUrl.orEmpty(), currentUrls[tabId].orEmpty(), element.title.orEmpty())?.kind
            }
          val target =
            WebContextMenuTarget(
              pageUrl = currentUrls[tabId].orEmpty(),
              linkUrl = linkUrl,
              linkText = element.title ?: element.altText,
              imageUrl = imageUrl,
              mediaUrl = mediaUrl,
              mediaKind = kind,
              mediaTitle = element.title,
            )
          if (target.hasAnyTarget) {
            callbacks.onContextMenuRequested(tabId, target)
          }
        }
      }

    session.contentBlockingDelegate =
      object : ContentBlocking.Delegate {
        override fun onContentBlocked(
          session: GeckoSession,
          event: ContentBlocking.BlockEvent,
        ) {
          if (event.isBlocking) {
            mainScope.launch(Dispatchers.Main) {
              callbacks.onTrackerBlocked(tabId, TurboShield.ESTIMATED_BYTES_PER_BLOCKED_REQUEST)
            }
          }
        }
      }

    session.permissionDelegate =
      object : PermissionDelegate {
        override fun onAndroidPermissionsRequest(
          session: GeckoSession,
          permissions: Array<out String>?,
          callback: PermissionDelegate.Callback,
        ) {
          val list = permissions.orEmpty().toList()
          val types = linkedSetOf<SitePermissionType>()
          if (list.contains(Manifest.permission.CAMERA)) types.add(SitePermissionType.CAMERA)
          if (list.contains(Manifest.permission.RECORD_AUDIO)) types.add(SitePermissionType.MICROPHONE)
          if (list.contains(Manifest.permission.ACCESS_FINE_LOCATION) ||
            list.contains(Manifest.permission.ACCESS_COARSE_LOCATION)
          ) {
            types.add(SitePermissionType.LOCATION)
          }
          if (types.isEmpty()) {
            callback.grant()
            return
          }
          callbacks.onEnsureRuntimePermissions(types) { granted ->
            if (granted) callback.grant() else callback.reject()
          }
        }

        override fun onContentPermissionRequest(
          session: GeckoSession,
          perm: PermissionDelegate.ContentPermission,
        ): GeckoResult<Int> {
          val siteType =
            when (perm.permission) {
              PermissionDelegate.PERMISSION_GEOLOCATION -> SitePermissionType.LOCATION
              PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS -> null
              else -> null
            }
          if (perm.permission == PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION) {
            return GeckoResult.fromValue(PermissionDelegate.ContentPermission.VALUE_DENY)
          }
          if (perm.permission == PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE ||
            perm.permission == PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE
          ) {
            return GeckoResult.fromValue(PermissionDelegate.ContentPermission.VALUE_ALLOW)
          }
          if (siteType == null) {
            return GeckoResult.fromValue(PermissionDelegate.ContentPermission.VALUE_DENY)
          }
          val origin = perm.uri
          val host = WebContextTargetResolver.normalizeHostKey(origin).ifBlank { "this site" }
          val tabIncognito = isIncognito || tabId in incognitoTabIds
          val sessionKey = "$tabId|$host|${siteType.name}"
          val cached =
            sessionSitePermissions[sessionKey]
              ?: if (!tabIncognito) persistedSitePermissions["$host|${siteType.name}"] else null
          if (cached == true) {
            return GeckoResult.fromValue(PermissionDelegate.ContentPermission.VALUE_ALLOW)
          }
          if (cached == false) {
            return GeckoResult.fromValue(PermissionDelegate.ContentPermission.VALUE_DENY)
          }

          val result = GeckoResult<Int>()
          callbacks.onSitePermissionRequested(
            tabId = tabId,
            origin = origin,
            host = host,
            isIncognito = tabIncognito,
            requestedTypes = setOf(siteType),
          ) { granted, rememberForSite ->
            if (rememberForSite) {
              if (tabIncognito) {
                sessionSitePermissions[sessionKey] = granted
              } else {
                persistedSitePermissions["$host|${siteType.name}"] = granted
                callbacks.onPersistSitePermission(host, siteType, granted)
              }
            }
            result.complete(
              if (granted) PermissionDelegate.ContentPermission.VALUE_ALLOW
              else PermissionDelegate.ContentPermission.VALUE_DENY
            )
          }
          return result
        }
      }

    session.open(runtime)
    sessions[tabId] = session
    if (!fallbackUrl.isNullOrBlank()) {
      currentUrls[tabId] = fallbackUrl
      session.loadUri(fallbackUrl)
    }
    return session
  }

  fun findInPage(tabId: String, query: String, forward: Boolean) {
    val session = sessions[tabId] ?: return
    if (query.isBlank()) {
      session.finder.clear()
      return
    }
    val flags = if (forward) 0 else GeckoSession.FINDER_FIND_BACKWARDS
    session.finder.find(query, flags)
  }

  fun clearFindMatches(tabId: String) {
    sessions[tabId]?.finder?.clear()
  }

  fun destroyTab(tabId: String) {
    incognitoTabIds.remove(tabId)
    currentUrls.remove(tabId)
    currentTitles.remove(tabId)
    pageStartTimestamps.remove(tabId)
    canGoBackMap.remove(tabId)
    canGoForwardMap.remove(tabId)
    upgradedFromHttpTabs.remove(tabId)
    pendingXpiCallback.remove(tabId)
    sessionSitePermissions.keys.removeAll { it.startsWith("$tabId|") }
    sessions.remove(tabId)?.let { session ->
      if (reusableGeckoView?.session === session) {
        reusableGeckoView?.releaseSession()
      }
      runCatching { session.stop() }
      runCatching { session.close() }
    }
  }

  fun destroyAllTabs() {
    val ids = sessions.keys.toList()
    ids.forEach { destroyTab(it) }
    reusableGeckoView?.releaseSession()
    reusableGeckoView = null
  }

  fun wipeAllSessionData() {
    httpsOnlyBypassedHosts.clear()
    publicProxyHttpBypassHosts.clear()
    sessionSitePermissions.clear()
    upgradedFromHttpTabs.clear()
    addonManager.clearAllStorage()
  }
}
