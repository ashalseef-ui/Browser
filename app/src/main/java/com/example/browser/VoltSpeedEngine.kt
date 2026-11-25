package com.example.browser

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.WebViewStartUpConfig
import com.example.data.BrowserSettings
import com.example.data.SearchEngine
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.ConnectionPool
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request

class VoltSpeedEngine(private val context: Context) {

  data class ProxyAuthTarget(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
  )

  private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val startupExecutor = Executors.newSingleThreadExecutor()
  private val prewarmedHosts = ConcurrentHashMap.newKeySet<String>()

  private val activeProxyRef = AtomicReference<Proxy?>(null)
  private val activeProxyAuthRef = AtomicReference<ProxyAuthTarget?>(null)

  @Volatile var isWebViewWarmedUp: Boolean = false
    private set

  private val dynamicProxySelector =
    object : ProxySelector() {
      override fun select(uri: URI?): List<Proxy> {
        val host = uri?.host.orEmpty()
        if (host == "127.0.0.1" || host == "localhost") return listOf(Proxy.NO_PROXY)
        return listOf(activeProxyRef.get() ?: Proxy.NO_PROXY)
      }

      override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
        // Handled by WebView/AutoProxy failover
      }
    }

  val okHttpClient: OkHttpClient =
    OkHttpClient.Builder()
      .proxySelector(dynamicProxySelector)
      .proxyAuthenticator { _, response ->
        val creds = activeProxyAuthRef.get() ?: return@proxyAuthenticator null
        if (response.request.header("Proxy-Authorization") != null) return@proxyAuthenticator null
        response.request
          .newBuilder()
          .header("Proxy-Authorization", Credentials.basic(creds.username, creds.password))
          .build()
      }
      .connectionPool(ConnectionPool(16, 5, TimeUnit.MINUTES))
      .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
      .connectTimeout(8, TimeUnit.SECONDS)
      .readTimeout(25, TimeUnit.SECONDS)
      .followRedirects(true)
      .build()

  fun setActiveProxy(proxy: Proxy?, username: String? = null, password: String? = null) {
    activeProxyRef.set(proxy)
    val sockAddr = proxy?.address() as? InetSocketAddress
    val host = sockAddr?.hostString.orEmpty()
    val port = sockAddr?.port ?: -1
    activeProxyAuthRef.set(
      if (proxy != null && !username.isNullOrBlank() && host.isNotBlank()) {
        ProxyAuthTarget(
          host = host,
          port = port,
          username = username,
          password = password.orEmpty(),
        )
      } else {
        null
      }
    )
    prewarmedHosts.clear()
  }

  fun getActiveProxyAuthTarget(): ProxyAuthTarget? = activeProxyAuthRef.get()

  fun getActiveProxyCredentials(): Pair<String, String>? =
    activeProxyAuthRef.get()?.let { it.username to it.password }

  @OptIn(WebViewCompat.ExperimentalAsyncStartUp::class)
  @androidx.annotation.OptIn(markerClass = [WebViewCompat.ExperimentalAsyncStartUp::class])
  fun warmUpChromiumAsync() {
    runCatching {
      val config = WebViewStartUpConfig.Builder(startupExecutor).build()
      WebViewCompat.startUpWebView(
        context,
        config,
        WebViewCompat.WebViewStartUpCallback {
          isWebViewWarmedUp = true
        },
      )
    }
  }

  /**
   * Resolves DNS and warms up TLS/HTTP2 socket connection in OkHttp pool ahead of direct navigation.
   * Skipped when routing via a public proxy to avoid saturating limited proxy connections.
   */
  fun prewarmOrigin(rawUrl: String) {
    if (activeProxyRef.get() != null) return
    val normalized = normalizeInputToUrl(rawUrl, SearchEngine.DUCKDUCKGO, forceUrlOnly = true) ?: return
    val host = runCatching { URI(normalized).host }.getOrNull()?.trim() ?: return
    if (!isValidPrewarmHost(host)) return
    if (prewarmedHosts.size >= MAX_PREWARMED_HOSTS) {
      prewarmedHosts.clear()
    }
    if (!prewarmedHosts.add(host)) return

    engineScope.launch {
      runCatching {
        InetAddress.getByName(host)
        val request = Request.Builder().url("https://$host/").head().build()
        okHttpClient.newCall(request).execute().use { }
      }
    }
  }

  private val defaultUserAgent: String by lazy { WebSettings.getDefaultUserAgent(context) }

  @SuppressLint("SetJavaScriptEnabled")
  fun configureWebViewSettings(webView: WebView, settings: BrowserSettings, isIncognito: Boolean = false) {
    webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
    webView.isScrollbarFadingEnabled = true

    val ws = webView.settings
    ws.javaScriptEnabled = true
    ws.domStorageEnabled = true
    ws.databaseEnabled = false
    @Suppress("DEPRECATION")
    ws.saveFormData = false
    ws.cacheMode = if (isIncognito) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
    ws.useWideViewPort = true
    ws.loadWithOverviewMode = true
    ws.setSupportZoom(true)
    ws.builtInZoomControls = true
    ws.displayZoomControls = false
    ws.allowFileAccess = false
    ws.allowContentAccess = true
    ws.setSupportMultipleWindows(true)
    ws.javaScriptCanOpenWindowsAutomatically = false
    ws.mediaPlaybackRequiresUserGesture = false
    ws.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
    ws.loadsImagesAutomatically = !settings.liteModeEnabled
    ws.blockNetworkImage = settings.liteModeEnabled

    val chromeVer = Regex("Chrome/([\\d\\.]+)").find(defaultUserAgent)?.groupValues?.getOrNull(1) ?: "126.0.0.0"
    ws.userAgentString =
      if (settings.desktopModeEnabled) {
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chromeVer Safari/537.36"
      } else {
        defaultUserAgent
      }

    if (WebViewFeature.isFeatureSupported(WebViewFeature.OFF_SCREEN_PRERASTER)) {
      WebSettingsCompat.setOffscreenPreRaster(ws, true)
    }
    if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
      WebSettingsCompat.setSafeBrowsingEnabled(ws, true)
    }
    if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
      WebSettingsCompat.setAlgorithmicDarkeningAllowed(ws, settings.darkWebEnabled)
    }
  }

  fun wipeEphemeralCacheAndStorage(
    webView: WebView? = null,
    wipeGlobalProfileStorage: Boolean = false,
  ) {
    runCatching {
      webView?.clearCache(true)
      webView?.clearFormData()
      webView?.clearHistory()
      webView?.clearSslPreferences()
      if (wipeGlobalProfileStorage) {
        DeviceTraceCleaner.wipeWebViewTraces(context)
      }
    }
  }

  fun clearNetworkPools() {
    prewarmedHosts.clear()
    runCatching { okHttpClient.connectionPool.evictAll() }
    runCatching { okHttpClient.cache?.delete() }
  }

  fun shutdown() {
    engineScope.cancel()
    runCatching { startupExecutor.shutdownNow() }
    clearNetworkPools()
  }

  companion object {
    private const val MAX_PREWARMED_HOSTS = 256

    fun isValidPrewarmHost(host: String): Boolean {
      if (host.isBlank() || host.length > 253) return false
      if (host.any { it.isWhitespace() || it == '/' || it == '@' || it == '\\' }) return false
      if (host.startsWith('.') || host.endsWith('.')) return false
      if (!host.equals("localhost", ignoreCase = true) && !host.contains('.')) return false
      return true
    }

    fun normalizeInputToUrl(
      input: String,
      searchEngine: SearchEngine,
      forceUrlOnly: Boolean = false,
    ): String? {
      val trimmed = input.trim()
      if (trimmed.isEmpty()) return null

      if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
        return trimmed
      }

      val looksLikeHost =
        !trimmed.contains(' ') &&
          (trimmed.contains('.') || trimmed.startsWith("localhost")) &&
          !trimmed.startsWith(".")

      if (looksLikeHost) {
        return "https://$trimmed"
      }

      if (forceUrlOnly) return null

      val encoded = URLEncoder.encode(trimmed, "UTF-8")
      return searchEngine.searchUrlTemplate.replace("%s", encoded)
    }
  }
}
