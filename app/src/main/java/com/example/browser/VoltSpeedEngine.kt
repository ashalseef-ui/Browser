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
import kotlinx.coroutines.launch
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request

class VoltSpeedEngine(private val context: Context) {

  private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val startupExecutor = Executors.newSingleThreadExecutor()
  private val prewarmedHosts = ConcurrentHashMap.newKeySet<String>()

  private val activeProxyRef = AtomicReference<Proxy?>(null)

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
      .connectionPool(ConnectionPool(16, 5, TimeUnit.MINUTES))
      .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
      .connectTimeout(8, TimeUnit.SECONDS)
      .readTimeout(25, TimeUnit.SECONDS)
      .followRedirects(true)
      .build()

  fun setActiveProxy(proxy: Proxy?) {
    activeProxyRef.set(proxy)
    prewarmedHosts.clear()
  }

  @Suppress("OPT_IN_USAGE")
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
   * Resolves DNS and warms up TLS/HTTP2 socket connection in OkHttp pool ahead of WebView navigation.
   */
  fun prewarmOrigin(rawUrl: String) {
    val normalized = normalizeInputToUrl(rawUrl, SearchEngine.DUCKDUCKGO, forceUrlOnly = true) ?: return
    val host = runCatching { java.net.URI(normalized).host }.getOrNull() ?: return
    if (!prewarmedHosts.add(host)) return

    engineScope.launch {
      runCatching {
        InetAddress.getByName(host)
        val request = Request.Builder().url("https://$host/").head().build()
        okHttpClient.newCall(request).execute().use { }
      }
    }
  }

  @SuppressLint("SetJavaScriptEnabled")
  fun configureWebViewSettings(webView: WebView, settings: BrowserSettings) {
    webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
    webView.isScrollbarFadingEnabled = true

    val ws = webView.settings
    ws.javaScriptEnabled = true
    // Keep DOM storage in-memory only during active session for modern HTML5 video players,
    // while strictly disabling disk cache, WebSQL database, and form data persistence.
    ws.domStorageEnabled = true
    ws.databaseEnabled = false
    @Suppress("DEPRECATION")
    ws.saveFormData = false
    ws.cacheMode = WebSettings.LOAD_NO_CACHE
    ws.useWideViewPort = true
    ws.loadWithOverviewMode = true
    ws.setSupportZoom(true)
    ws.builtInZoomControls = true
    ws.displayZoomControls = false
    ws.allowFileAccess = false
    ws.allowContentAccess = true
    ws.mediaPlaybackRequiresUserGesture = false
    ws.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
    ws.loadsImagesAutomatically = !settings.liteModeEnabled
    ws.blockNetworkImage = settings.liteModeEnabled

    val defaultUa = WebSettings.getDefaultUserAgent(context)
    ws.userAgentString =
      if (settings.desktopModeEnabled) {
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
      } else {
        defaultUa
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

  fun wipeEphemeralCacheAndStorage(webView: WebView? = null) {
    runCatching {
      webView?.clearCache(true)
      webView?.clearFormData()
      webView?.clearHistory()
      webView?.clearSslPreferences()
      DeviceTraceCleaner.wipeWebViewTraces(context)
    }
  }

  fun clearNetworkPools() {
    prewarmedHosts.clear()
    runCatching { okHttpClient.connectionPool.evictAll() }
    runCatching { okHttpClient.cache?.delete() }
  }

  companion object {
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
