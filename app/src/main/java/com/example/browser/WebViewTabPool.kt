package com.example.browser

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.data.BrowserSettings
import com.example.data.DetectedMediaItem
import com.example.data.MediaKind
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

interface TabWebCallbacks {
  fun onPageStarted(tabId: String, url: String)
  fun onPageFinished(tabId: String, url: String, title: String, loadTimeMs: Long, canGoBack: Boolean, canGoForward: Boolean)
  fun onProgressChanged(tabId: String, progress: Int)
  fun onTitleReceived(tabId: String, title: String)
  fun onTrackerBlocked(tabId: String, bytesSaved: Long)
  fun onMediaDetected(tabId: String, items: List<DetectedMediaItem>)
  fun onDownloadRequested(url: String, userAgent: String, contentDisposition: String, mimeType: String)
  fun onOpenNewTabRequested(url: String)
  fun onShowFullscreenView(view: View, callback: WebChromeClient.CustomViewCallback)
  fun onHideFullscreenView()
  fun onMainFrameNetworkError(tabId: String, errorCode: Int, description: String)
}

class WebViewTabPool(
  private val context: Context,
  private val speedEngine: VoltSpeedEngine,
  private val mainScope: CoroutineScope,
) {
  private val webViews = LinkedHashMap<String, WebView>(8, 0.75f, true)
  private val pageStartTimestamps = HashMap<String, Long>()
  private val currentUrls = HashMap<String, String>()
  private val currentTitles = HashMap<String, String>()

  @Volatile var currentSettings: BrowserSettings = BrowserSettings()

  fun getOrCreateWebView(
    tabId: String,
    isIncognito: Boolean,
    callbacks: TabWebCallbacks,
  ): WebView {
    webViews[tabId]?.let { existing ->
      speedEngine.configureWebViewSettings(existing, currentSettings)
      return existing
    }

    val webView = WebView(context)
    webView.layoutParams =
      ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
      )

    if (isIncognito) {
      CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
    } else {
      CookieManager.getInstance().setAcceptCookie(true)
      CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
    }

    speedEngine.configureWebViewSettings(webView, currentSettings)

    val jsBridge =
      VoltMediaJsBridge(
        onDetected = { items -> mainScope.launch(Dispatchers.Main) { callbacks.onMediaDetected(tabId, items) } },
        currentPageUrlProvider = { currentUrls[tabId].orEmpty() },
      )
    webView.addJavascriptInterface(jsBridge, "VoltMediaBridge")

    webView.webViewClient =
      object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
          val url = request?.url?.toString() ?: return false
          return !(url.startsWith("http://") || url.startsWith("https://") || url.startsWith("about:"))
        }

        override fun shouldInterceptRequest(
          view: WebView?,
          request: WebResourceRequest?,
        ): WebResourceResponse? {
          val uri = request?.url ?: return null
          val isMainFrame = request.isForMainFrame

          if (!isMainFrame && currentSettings.turboShieldEnabled && TurboShield.shouldBlock(uri)) {
            mainScope.launch(Dispatchers.Main) {
              callbacks.onTrackerBlocked(tabId, TurboShield.ESTIMATED_BYTES_PER_BLOCKED_REQUEST)
            }
            return TurboShield.emptyBlockedResponse()
          }

          val urlString = uri.toString()
          val pageUrl = currentUrls[tabId].orEmpty()
          val pageTitle = currentTitles[tabId].orEmpty()
          val headers = request.requestHeaders.orEmpty()
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
            callbacks.onMainFrameNetworkError(
              tabId = tabId,
              errorCode = error.errorCode,
              description = error.description?.toString().orEmpty(),
            )
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

        override fun onCreateWindow(
          view: WebView?,
          isDialog: Boolean,
          isUserGesture: Boolean,
          resultMsg: android.os.Message?,
        ): Boolean {
          val hitResult = view?.hitTestResult
          val url = hitResult?.extra
          if (!url.isNullOrBlank() && (url.startsWith("http://") || url.startsWith("https://"))) {
            callbacks.onOpenNewTabRequested(url)
            return true
          }
          return false
        }
      }

    webView.setDownloadListener(
      DownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
        callbacks.onDownloadRequested(
          url = url,
          userAgent = userAgent.orEmpty(),
          contentDisposition = contentDisposition.orEmpty(),
          mimeType = mimetype.orEmpty(),
        )
      }
    )

    webViews[tabId] = webView
    return webView
  }

  fun findWebView(tabId: String): WebView? = webViews[tabId]

  fun triggerMediaScan(tabId: String) {
    webViews[tabId]?.evaluateJavascript(MediaSniffer.domSnifferScript, null)
  }

  fun refreshSettings(settings: BrowserSettings) {
    currentSettings = settings
    webViews.values.forEach { wv ->
      speedEngine.configureWebViewSettings(wv, settings)
    }
  }

  fun wipeAllCacheAndCookies() {
    webViews.values.forEach { speedEngine.wipeEphemeralCacheAndStorage(it) }
    if (webViews.isEmpty()) {
      speedEngine.wipeEphemeralCacheAndStorage(null)
    }
  }

  fun destroyTab(tabId: String) {
    webViews.remove(tabId)?.let { wv ->
      (wv.parent as? ViewGroup)?.removeView(wv)
      wv.stopLoading()
      speedEngine.wipeEphemeralCacheAndStorage(wv)
      wv.removeAllViews()
      wv.destroy()
    }
    pageStartTimestamps.remove(tabId)
    currentUrls.remove(tabId)
    currentTitles.remove(tabId)
  }

  fun destroyAllTabs() {
    val ids = webViews.keys.toList()
    ids.forEach { destroyTab(it) }
    wipeAllCacheAndCookies()
    pageStartTimestamps.clear()
    currentUrls.clear()
    currentTitles.clear()
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
