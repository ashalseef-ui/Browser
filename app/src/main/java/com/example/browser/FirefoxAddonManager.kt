package com.example.browser

import android.content.Context
import android.net.Uri
import com.example.data.AdBlockerType
import com.example.data.AddonCatalogItem
import com.example.data.AppThemeMode
import com.example.data.BrowserSettings
import com.example.data.DetectedMediaItem
import com.example.data.FirefoxAddonInfo
import com.example.data.MediaKind
import com.example.ui.AddonInstallPromptState
import com.example.ui.AddonPopupState
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

/**
 * Manages the shared [GeckoRuntime], built-in Volt Bridge WebExtension native messaging,
 * Firefox `.xpi` add-on installation (AMO URLs, direct `.xpi` links, and local SAF files),
 * extension permission prompts, and ActionDelegate popups.
 *
 * [GeckoRuntime] is initialized lazily on demand on Android devices so Speed Dial startup stays
 * instant and local JVM Robolectric unit tests can exercise pure XPI/catalog helpers safely.
 */
class FirefoxAddonManager(
  private val context: Context,
  private val scope: CoroutineScope,
  private val onShowMessage: (String) -> Unit,
  private val onBridgeMediaDetected: (pageUrl: String, items: List<DetectedMediaItem>) -> Unit = { _, _ -> },
) {
  @Volatile private var runtime: GeckoRuntime? = null
  private val loadedExtensions = ConcurrentHashMap<String, WebExtension>()
  private val browserActions = ConcurrentHashMap<String, WebExtension.Action>()
  @Volatile private var bridgePort: WebExtension.Port? = null
  @Volatile private var latestBridgeStateJson: JSONObject = JSONObject()

  private val _addons = MutableStateFlow<List<FirefoxAddonInfo>>(emptyList())
  val addons: StateFlow<List<FirefoxAddonInfo>> = _addons.asStateFlow()

  private val _isInstalling = MutableStateFlow(false)
  val isInstalling: StateFlow<Boolean> = _isInstalling.asStateFlow()

  private val _pendingInstallPrompt = MutableStateFlow<AddonInstallPromptState?>(null)
  val pendingInstallPrompt: StateFlow<AddonInstallPromptState?> = _pendingInstallPrompt.asStateFlow()

  private val _activeAddonPopup = MutableStateFlow<AddonPopupState?>(null)
  val activeAddonPopup: StateFlow<AddonPopupState?> = _activeAddonPopup.asStateFlow()

  fun isRuntimeInitialized(): Boolean = runtime != null

  /**
   * Lazily initializes [GeckoRuntime] on the main thread, configures ContentBlocking and theme
   * preferences, attaches the WebExtension prompt delegate, and loads the built-in Volt bridge.
   */
  fun ensureRuntime(settings: BrowserSettings): GeckoRuntime {
    runtime?.let { return it }
    synchronized(this) {
      runtime?.let { return it }
      val runtimeSettings =
        GeckoRuntimeSettings.Builder()
          .preferredColorScheme(
            if (settings.themeMode == AppThemeMode.ZEN_LIGHT) {
              GeckoRuntimeSettings.COLOR_SCHEME_LIGHT
            } else {
              GeckoRuntimeSettings.COLOR_SCHEME_DARK
            }
          )
          .contentBlocking(buildContentBlockingSettings(settings))
          .javaScriptEnabled(true)
          .consoleOutput(false)
          .aboutConfigEnabled(false)
          .build()

      val created = GeckoRuntime.create(context.applicationContext, runtimeSettings)
      runtime = created
      configureWebExtensionController(created)
      return created
    }
  }

  private fun configureWebExtensionController(rt: GeckoRuntime) {
    val controller = rt.webExtensionController
    controller.setPromptDelegate(
      object : WebExtensionController.PromptDelegate {
        override fun onInstallPrompt(
          extension: WebExtension,
          permissions: Array<out String>,
          origins: Array<out String>,
        ): GeckoResult<AllowOrDeny> {
          val result = GeckoResult<AllowOrDeny>()
          val allPerms = permissions.toList().distinct()
          scope.launch(Dispatchers.Main) {
            _pendingInstallPrompt.value?.onDecision?.invoke(false, false)
            _pendingInstallPrompt.value =
              AddonInstallPromptState(
                extensionId = extension.id,
                name = extension.metaData.name?.takeIf { it.isNotBlank() } ?: extension.id,
                version = extension.metaData.version,
                permissions = allPerms,
                origins = origins.toList(),
              ) { allow, allowPrivate ->
                if (allow && allowPrivate) {
                  rt.webExtensionController.setAllowedInPrivateBrowsing(extension, true)
                }
                result.complete(if (allow) AllowOrDeny.ALLOW else AllowOrDeny.DENY)
              }
          }
          return result
        }

        override fun onUpdatePrompt(
          currentlyInstalled: WebExtension,
          updatedExtension: WebExtension,
          newPermissions: Array<out String>,
          newOrigins: Array<out String>,
        ): GeckoResult<AllowOrDeny> {
          return GeckoResult.fromValue(AllowOrDeny.ALLOW)
        }
      }
    )

    // Register built-in Volt Bridge WebExtension for proxy routing, Kill Switch & Media Sniffer
    controller
      .ensureBuiltIn(BUILT_IN_BRIDGE_LOCATION, BUILT_IN_BRIDGE_ID)
      .accept(
        { ext ->
          if (ext != null) {
            attachBridgeMessageDelegate(ext)
          }
          refreshAddons()
        },
        {
          refreshAddons()
        },
      )
  }

  private fun attachBridgeMessageDelegate(ext: WebExtension) {
    ext.setMessageDelegate(
      object : WebExtension.MessageDelegate {
        override fun onConnect(port: WebExtension.Port) {
          bridgePort = port
          runCatching {
            port.postMessage(
              JSONObject()
                .put("type", "syncState")
                .put("state", latestBridgeStateJson)
            )
          }
          port.setDelegate(
            object : WebExtension.PortDelegate {
              override fun onPortMessage(
                message: Any,
                port: WebExtension.Port,
              ) {
                handleBridgePortMessage(message)
              }

              override fun onDisconnect(port: WebExtension.Port) {
                if (bridgePort === port) {
                  bridgePort = null
                }
              }
            }
          )
        }
      },
      NATIVE_BRIDGE_APP_NAME,
    )
  }

  private fun handleBridgePortMessage(message: Any) {
    val json = message as? JSONObject ?: return
    val msgType = json.optString("type")
    if (msgType == "domMediaDetected") {
      val pageUrl = json.optString("pageUrl")
      val pageTitle = json.optString("pageTitle")
      val rawItems = json.optJSONArray("items") ?: return
      val parsed = mutableListOf<DetectedMediaItem>()
      for (i in 0 until rawItems.length()) {
        val obj = rawItems.optJSONObject(i) ?: continue
        val url = obj.optString("url")
        if (url.isBlank()) continue
        val title = obj.optString("title").ifBlank { pageTitle }
        val kindHint = obj.optString("kind")
        val poster = obj.optString("poster").ifBlank { null }
        val classified = MediaSniffer.classifyUrl(url, pageUrl, title)
        if (classified != null) {
          parsed.add(classified.copy(posterUrl = poster ?: classified.posterUrl))
        } else {
          val kind =
            when (kindHint) {
              "audio" -> MediaKind.AUDIO
              else -> MediaKind.VIDEO
            }
          parsed.add(
            DetectedMediaItem(
              url = url,
              title = title.ifBlank { "Web Media" },
              kind = kind,
              mimeType = if (kind == MediaKind.AUDIO) "audio/*" else "video/mp4",
              sourcePageUrl = pageUrl,
              posterUrl = poster,
            )
          )
        }
      }
      if (parsed.isNotEmpty()) {
        onBridgeMediaDetected(pageUrl, parsed)
      }
    } else if (msgType == "networkMediaDetected") {
      val mediaUrl = json.optString("url")
      val pageUrl = json.optString("pageUrl")
      val classified = MediaSniffer.classifyUrl(mediaUrl, pageUrl, "Stream Media")
      if (classified != null) {
        onBridgeMediaDetected(pageUrl, listOf(classified))
      }
    }
  }

  fun syncBridgeState(
    killSwitchBlocking: Boolean,
    liteModeEnabled: Boolean,
    proxyType: String,
    proxyHost: String,
    proxyPort: Int,
    proxyUsername: String,
    proxyPassword: String,
    bypassedHosts: Set<String>,
  ) {
    val stateObj =
      JSONObject()
        .put("killSwitch", killSwitchBlocking)
        .put("liteMode", liteModeEnabled)
        .put("proxyType", proxyType)
        .put("proxyHost", proxyHost)
        .put("proxyPort", proxyPort)
        .put("proxyUsername", proxyUsername)
        .put("proxyPassword", proxyPassword)
        .put("bypassedHosts", JSONArray(bypassedHosts.toList()))
    latestBridgeStateJson = stateObj
    val port = bridgePort ?: return
    scope.launch(Dispatchers.Main) {
      runCatching {
        port.postMessage(
          JSONObject()
            .put("type", "syncState")
            .put("state", stateObj)
        )
      }
    }
  }

  fun syncRuntimeSettings(settings: BrowserSettings) {
    val rt = runtime ?: return
    scope.launch(Dispatchers.Main) {
      runCatching {
        rt.settings.preferredColorScheme =
          if (settings.themeMode == AppThemeMode.ZEN_LIGHT) {
            GeckoRuntimeSettings.COLOR_SCHEME_LIGHT
          } else {
            GeckoRuntimeSettings.COLOR_SCHEME_DARK
          }
        val cb = buildContentBlockingSettings(settings)
        rt.settings.contentBlocking.setAntiTracking(cb.antiTrackingCategories)
        rt.settings.contentBlocking.setCookieBehavior(cb.cookieBehavior)
        rt.settings.contentBlocking.setStrictSocialTrackingProtection(cb.strictSocialTrackingProtection)
        rt.settings.contentBlocking.setEnhancedTrackingProtectionLevel(cb.enhancedTrackingProtectionLevel)
      }
    }
  }

  private fun buildContentBlockingSettings(settings: BrowserSettings): ContentBlocking.Settings {
    val level = settings.adBlockerType
    val antiTracking =
      when (level) {
        AdBlockerType.OFF -> ContentBlocking.AntiTracking.NONE
        AdBlockerType.STANDARD ->
          ContentBlocking.AntiTracking.AD or ContentBlocking.AntiTracking.ANALYTIC
        AdBlockerType.ADGUARD ->
          ContentBlocking.AntiTracking.AD or
            ContentBlocking.AntiTracking.ANALYTIC or
            ContentBlocking.AntiTracking.SOCIAL or
            ContentBlocking.AntiTracking.CRYPTOMINING
        AdBlockerType.STRICT, AdBlockerType.STEALTH ->
          ContentBlocking.AntiTracking.AD or
            ContentBlocking.AntiTracking.ANALYTIC or
            ContentBlocking.AntiTracking.SOCIAL or
            ContentBlocking.AntiTracking.CRYPTOMINING or
            ContentBlocking.AntiTracking.FINGERPRINTING or
            ContentBlocking.AntiTracking.STP
      }
    val cookieBehavior =
      when (level) {
        AdBlockerType.OFF -> ContentBlocking.CookieBehavior.ACCEPT_ALL
        AdBlockerType.STANDARD, AdBlockerType.ADGUARD ->
          ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS
        AdBlockerType.STRICT, AdBlockerType.STEALTH ->
          ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY
      }
    val etpLevel =
      when (level) {
        AdBlockerType.OFF -> ContentBlocking.EtpLevel.NONE
        AdBlockerType.STANDARD -> ContentBlocking.EtpLevel.DEFAULT
        AdBlockerType.ADGUARD, AdBlockerType.STRICT, AdBlockerType.STEALTH ->
          ContentBlocking.EtpLevel.STRICT
      }

    return ContentBlocking.Settings.Builder()
      .antiTracking(antiTracking)
      .cookieBehavior(cookieBehavior)
      .enhancedTrackingProtectionLevel(etpLevel)
      .strictSocialTrackingProtection(level == AdBlockerType.STRICT || level == AdBlockerType.STEALTH)
      .build()
  }

  fun respondToInstallPrompt(allow: Boolean, allowPrivate: Boolean) {
    val prompt = _pendingInstallPrompt.value ?: return
    _pendingInstallPrompt.value = null
    prompt.onDecision(allow, allowPrivate)
  }

  fun closeAddonPopup() {
    val active = _activeAddonPopup.value
    _activeAddonPopup.value = null
    runCatching { active?.session?.close() }
  }

  fun refreshAddons(settings: BrowserSettings = BrowserSettings()) {
    val rt = runtime ?: run {
      // Ensure runtime when user opens Add-ons manager
      ensureRuntime(settings)
    }
    scope.launch(Dispatchers.Main) {
      rt.webExtensionController.list().accept(
        { list ->
          val items =
            list.orEmpty()
              .filter { ext -> ext.id != BUILT_IN_BRIDGE_ID }
              .map { ext ->
                loadedExtensions[ext.id] = ext
                attachActionDelegate(ext)
                FirefoxAddonInfo(
                  id = ext.id,
                  name = ext.metaData.name?.takeIf { it.isNotBlank() } ?: ext.id,
                  version = ext.metaData.version.orEmpty(),
                  description = ext.metaData.description.orEmpty(),
                  creatorName = ext.metaData.creatorName,
                  enabled = ext.metaData.enabled,
                  allowedInPrivateBrowsing = ext.metaData.allowedInPrivateBrowsing,
                  isBuiltIn = ext.id == BUILT_IN_BRIDGE_ID,
                  hasBrowserAction = browserActions.containsKey(ext.id),
                  browserActionTitle = browserActions[ext.id]?.title,
                  homepageUrl = ext.metaData.homepageUrl,
                )
              }
              .sortedBy { it.name.lowercase(Locale.US) }
          _addons.value = items
        },
        { err ->
          onShowMessage("Failed to load add-ons: ${err?.message ?: "unknown error"}")
        },
      )
    }
  }

  private fun attachActionDelegate(ext: WebExtension) {
    val extName = ext.metaData.name?.takeIf { it.isNotBlank() } ?: ext.id
    ext.setActionDelegate(
      object : WebExtension.ActionDelegate {
        override fun onBrowserAction(
          extension: WebExtension,
          session: GeckoSession?,
          action: WebExtension.Action,
        ) {
          browserActions[extension.id] = action
          updateAddonHasAction(extension.id, action.title)
        }

        override fun onPageAction(
          extension: WebExtension,
          session: GeckoSession?,
          action: WebExtension.Action,
        ) {
          browserActions[extension.id] = action
          updateAddonHasAction(extension.id, action.title)
        }

        override fun onTogglePopup(
          extension: WebExtension,
          action: WebExtension.Action,
        ): GeckoResult<GeckoSession> {
          if (_activeAddonPopup.value?.addonId == extension.id) {
            closeAddonPopup()
            return GeckoResult.fromValue(null)
          }
          val popupSession = createPopupSession(extension.id, extName)
          return GeckoResult.fromValue(popupSession)
        }

        override fun onOpenPopup(
          extension: WebExtension,
          action: WebExtension.Action,
        ): GeckoResult<GeckoSession> {
          val popupSession = createPopupSession(extension.id, extName)
          return GeckoResult.fromValue(popupSession)
        }
      }
    )
  }

  private fun createPopupSession(addonId: String, addonName: String): GeckoSession {
    closeAddonPopup()
    val rt = ensureRuntime(BrowserSettings())
    val session = GeckoSession()
    session.open(rt)
    _activeAddonPopup.value = AddonPopupState(addonId = addonId, addonName = addonName, session = session)
    return session
  }

  private fun updateAddonHasAction(addonId: String, actionTitle: String?) {
    _addons.value =
      _addons.value.map { info ->
        if (info.id == addonId) info.copy(hasBrowserAction = true, browserActionTitle = actionTitle)
        else info
      }
  }

  fun openAddonPopup(addonId: String) {
    val action = browserActions[addonId]
    if (action == null) {
      onShowMessage("This add-on runs automatically in the background and has no popup UI")
      return
    }
    scope.launch(Dispatchers.Main) {
      runCatching { action.click() }
        .onFailure { onShowMessage("Unable to open add-on popup") }
    }
  }

  fun installFromUrl(rawInput: String, settings: BrowserSettings = BrowserSettings()) {
    val resolvedUrl = resolveAmoXpiUrl(rawInput)
    if (resolvedUrl == null) {
      onShowMessage("Enter a valid .xpi download link or addons.mozilla.org add-on URL")
      return
    }
    val rt = ensureRuntime(settings)
    _isInstalling.value = true
    scope.launch(Dispatchers.Main) {
      rt.webExtensionController
        .install(resolvedUrl)
        .accept(
          { ext ->
            _isInstalling.value = false
            val title = ext?.metaData?.name ?: "Firefox Add-on"
            onShowMessage("Installed $title")
            refreshAddons(settings)
          },
          { err ->
            _isInstalling.value = false
            val msg = err?.message.orEmpty()
            if (msg.contains("cancel", ignoreCase = true) || msg.contains("reject", ignoreCase = true)) {
              onShowMessage("Add-on installation cancelled")
            } else {
              onShowMessage("Add-on install failed: ${msg.ifBlank { "check network or .xpi format" }}")
            }
          },
        )
    }
  }

  fun installFromContentUri(uri: Uri, settings: BrowserSettings = BrowserSettings()) {
    _isInstalling.value = true
    scope.launch {
      val preparedFile =
        withContext(Dispatchers.IO) {
          val importDir = File(context.cacheDir, "xpi_imports").apply { mkdirs() }
          val outFile = File(importDir, "addon_${System.currentTimeMillis()}.xpi")
          runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
              FileOutputStream(outFile).use { output -> input.copyTo(output) }
            }
            if (!validateXpiArchive(outFile)) {
              outFile.delete()
              null
            } else {
              outFile
            }
          }.getOrNull()
        }

      if (preparedFile == null) {
        _isInstalling.value = false
        onShowMessage("Invalid .xpi package (missing WebExtension manifest.json)")
        return@launch
      }

      withContext(Dispatchers.Main) {
        val rt = ensureRuntime(settings)
        val fileUri = Uri.fromFile(preparedFile).toString()
        rt.webExtensionController
          .install(fileUri)
          .accept(
            { ext ->
              _isInstalling.value = false
              preparedFile.delete()
              val title = ext?.metaData?.name ?: "Local .xpi Add-on"
              onShowMessage("Installed $title")
              refreshAddons(settings)
            },
            { err ->
              _isInstalling.value = false
              preparedFile.delete()
              onShowMessage("Add-on install failed: ${err?.message ?: "incompatible .xpi"}")
            },
          )
      }
    }
  }

  fun setAddonEnabled(addonId: String, enabled: Boolean) {
    val rt = runtime ?: return
    val ext = loadedExtensions[addonId] ?: return
    scope.launch(Dispatchers.Main) {
      val call =
        if (enabled) {
          rt.webExtensionController.enable(ext, WebExtensionController.EnableSource.USER)
        } else {
          rt.webExtensionController.disable(ext, WebExtensionController.EnableSource.USER)
        }
      call.accept(
        {
          refreshAddons()
          onShowMessage(if (enabled) "${ext.metaData.name} enabled" else "${ext.metaData.name} disabled")
        },
        { err ->
          onShowMessage("Failed to update add-on: ${err?.message ?: "error"}")
        },
      )
    }
  }

  fun setAllowedInPrivateBrowsing(addonId: String, allowed: Boolean) {
    val rt = runtime ?: return
    val ext = loadedExtensions[addonId] ?: return
    scope.launch(Dispatchers.Main) {
      rt.webExtensionController
        .setAllowedInPrivateBrowsing(ext, allowed)
        .accept(
          {
            refreshAddons()
            onShowMessage(
              if (allowed) "${ext.metaData.name} allowed in Private tabs"
              else "${ext.metaData.name} disabled in Private tabs"
            )
          },
          { err ->
            onShowMessage("Failed to update Private Browsing permission: ${err?.message ?: "error"}")
          },
        )
    }
  }

  fun uninstallAddon(addonId: String) {
    val rt = runtime ?: return
    val ext = loadedExtensions[addonId] ?: return
    val name = ext.metaData.name ?: addonId
    scope.launch(Dispatchers.Main) {
      rt.webExtensionController
        .uninstall(ext)
        .accept(
          {
            loadedExtensions.remove(addonId)
            browserActions.remove(addonId)
            if (_activeAddonPopup.value?.addonId == addonId) {
              closeAddonPopup()
            }
            refreshAddons()
            onShowMessage("Uninstalled $name")
          },
          { err ->
            onShowMessage("Uninstall failed: ${err?.message ?: "error"}")
          },
        )
    }
  }

  fun clearAllStorage() {
    val rt = runtime ?: return
    scope.launch(Dispatchers.Main) {
      runCatching {
        rt.storageController.clearData(org.mozilla.geckoview.StorageController.ClearFlags.ALL)
      }
    }
  }

  companion object {
    const val BUILT_IN_BRIDGE_ID = "volt-bridge@voltbrowser.app"
    const val BUILT_IN_BRIDGE_LOCATION = "resource://android/assets/volt_bridge/"
    const val NATIVE_BRIDGE_APP_NAME = "voltBridge"

    val CURATED_ADDONS: List<AddonCatalogItem> =
      listOf(
        AddonCatalogItem(
          id = "uBlock0@raymondhill.net",
          slug = "ublock-origin",
          name = "uBlock Origin",
          summary = "Wide-spectrum content & cosmetic blocker for ads, trackers, and miners.",
          category = "Privacy & Adblock",
          xpiUrl = "https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/addon-ublock-origin-latest.xpi",
          amoPageUrl = "https://addons.mozilla.org/en-US/firefox/addon/ublock-origin/",
        ),
        AddonCatalogItem(
          id = "addon@darkreader.org",
          slug = "darkreader",
          name = "Dark Reader",
          summary = "True real-time dark mode theme inversion for every website.",
          category = "Appearance",
          xpiUrl = "https://addons.mozilla.org/firefox/downloads/latest/darkreader/addon-darkreader-latest.xpi",
          amoPageUrl = "https://addons.mozilla.org/en-US/firefox/addon/darkreader/",
        ),
        AddonCatalogItem(
          id = "jid1-MnnxcxisBPnSXQ@jetpack",
          slug = "privacy-badger17",
          name = "Privacy Badger",
          summary = "EFF heuristic tracker & invisible beacon blocker.",
          category = "Privacy & Adblock",
          xpiUrl = "https://addons.mozilla.org/firefox/downloads/latest/privacy-badger17/addon-privacy-badger17-latest.xpi",
          amoPageUrl = "https://addons.mozilla.org/en-US/firefox/addon/privacy-badger17/",
        ),
        AddonCatalogItem(
          id = "sponsorBlocker@ajay.app",
          slug = "sponsorblock",
          name = "SponsorBlock",
          summary = "Automatically skip sponsorships, intros, outros, and reminders in videos.",
          category = "Media & Video",
          xpiUrl = "https://addons.mozilla.org/firefox/downloads/latest/sponsorblock/addon-sponsorblock-latest.xpi",
          amoPageUrl = "https://addons.mozilla.org/en-US/firefox/addon/sponsorblock/",
        ),
        AddonCatalogItem(
          id = "jid1-BoFifL9Vbdl2zQ@jetpack",
          slug = "decentraleyes",
          name = "Decentraleyes",
          summary = "Serves common CDN libraries locally to stop Google/Cloudflare CDN tracking.",
          category = "Privacy & Adblock",
          xpiUrl = "https://addons.mozilla.org/firefox/downloads/latest/decentraleyes/addon-decentraleyes-latest.xpi",
          amoPageUrl = "https://addons.mozilla.org/en-US/firefox/addon/decentraleyes/",
        ),
        AddonCatalogItem(
          id = "CookieAutoDelete@kennydo.com",
          slug = "cookie-autodelete",
          name = "Cookie AutoDelete",
          summary = "Automatically sweeps leftover site cookies when tabs close.",
          category = "Privacy & Adblock",
          xpiUrl = "https://addons.mozilla.org/firefox/downloads/latest/cookie-autodelete/addon-cookie-autodelete-latest.xpi",
          amoPageUrl = "https://addons.mozilla.org/en-US/firefox/addon/cookie-autodelete/",
        ),
      )

    /** Returns true when a URL points directly to a Firefox `.xpi` package. */
    fun isXpiUrl(url: String?): Boolean {
      if (url.isNullOrBlank()) return false
      val clean = url.trim().substringBefore('?').substringBefore('#').lowercase(Locale.US)
      return (clean.startsWith("http://") || clean.startsWith("https://") || clean.startsWith("file://")) &&
        clean.endsWith(".xpi")
    }

    /**
     * Converts a direct `.xpi` URL or Mozilla AMO extension listing URL
     * (e.g. `https://addons.mozilla.org/en-US/firefox/addon/ublock-origin/`) into a direct `.xpi` URL.
     */
    fun resolveAmoXpiUrl(rawInput: String?): String? {
      val trimmed = rawInput?.trim().orEmpty()
      if (trimmed.isEmpty()) return null
      if (isXpiUrl(trimmed)) return trimmed

      val amoPageRegex =
        Regex("^https?://addons\\.mozilla\\.org/(?:[^/]+/)?(?:firefox|android)/addon/([^/?#]+)/?.*", RegexOption.IGNORE_CASE)
      val match = amoPageRegex.find(trimmed)
      if (match != null) {
        val slug = match.groupValues[1].trim()
        if (slug.isNotBlank()) {
          return "https://addons.mozilla.org/firefox/downloads/latest/$slug/addon-$slug-latest.xpi"
        }
      }

      if ((trimmed.startsWith("https://", true) || trimmed.startsWith("http://", true)) &&
        trimmed.contains("addons.mozilla.org/firefox/downloads/", ignoreCase = true)
      ) {
        return trimmed
      }
      return null
    }

    /** Validates that a local `.xpi` file is a valid ZIP archive containing `manifest.json`. */
    fun validateXpiArchive(file: File): Boolean {
      if (!file.exists() || !file.isFile || file.length() < 32L) return false
      return runCatching {
        ZipInputStream(file.inputStream().buffered()).use { zip ->
          var entry = zip.nextEntry
          var foundManifest = false
          while (entry != null) {
            if (entry.name.equals("manifest.json", ignoreCase = true)) {
              foundManifest = true
              break
            }
            entry = zip.nextEntry
          }
          foundManifest
        }
      }.getOrDefault(false)
    }
  }
}
