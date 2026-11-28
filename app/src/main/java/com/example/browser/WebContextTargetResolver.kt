package com.example.browser

import android.webkit.WebView
import androidx.core.net.toUri
import com.example.data.MediaKind
import com.example.data.WebContextMenuTarget
import java.util.Locale

/**
 * Resolves long-press WebView hit test results merged with DOM touch context metadata
 * into actionable link, image, and media targets.
 */
object WebContextTargetResolver {

  fun normalizeHostKey(urlOrHost: String?): String {
    val raw = urlOrHost?.trim().orEmpty()
    if (raw.isEmpty()) return ""
    val host =
      if (raw.contains("://")) runCatching { raw.toUri().host }.getOrNull().orEmpty()
      else raw.substringBefore('/').substringBefore(':')
    return host.lowercase(Locale.US).removePrefix("www.")
  }

  fun upgradeHttpToHttps(url: String): String =
    if (url.startsWith("http://", ignoreCase = true)) {
      "https://" + url.substring(7)
    } else {
      url
    }

  fun shouldProvideProxyCredentials(challengeHost: String?, activeProxyHost: String?): Boolean {
    val challenge = challengeHost?.trim().orEmpty()
    val proxy = activeProxyHost?.trim().orEmpty()
    return challenge.isNotEmpty() && proxy.isNotEmpty() && challenge.equals(proxy, ignoreCase = true)
  }

  fun resolveContextTarget(
    pageUrl: String,
    hitType: Int,
    hitExtra: String?,
    domTarget: WebContextMenuTarget?,
  ): WebContextMenuTarget? {
    if (hitType == WebView.HitTestResult.EDIT_TEXT_TYPE) return null

    var linkUrl = domTarget?.linkUrl?.takeIf { isSupportedContextUrl(it) }
    val linkText = domTarget?.linkText?.takeIf { it.isNotBlank() }
    var imageUrl = domTarget?.imageUrl?.takeIf { isSupportedContextUrl(it) }
    var mediaUrl = domTarget?.mediaUrl?.takeIf { isSupportedContextUrl(it) }
    var mediaTitle = domTarget?.mediaTitle?.takeIf { it.isNotBlank() }

    val cleanedExtra = hitExtra?.trim()?.takeIf { it.isNotBlank() }
    when (hitType) {
      WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
        if (cleanedExtra != null && isSupportedContextUrl(cleanedExtra)) {
          linkUrl = cleanedExtra
        }
      }
      WebView.HitTestResult.IMAGE_TYPE,
      WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
        if (cleanedExtra != null && isSupportedContextUrl(cleanedExtra)) {
          imageUrl = cleanedExtra
        }
      }
      WebView.HitTestResult.EMAIL_TYPE -> {
        if (cleanedExtra != null) {
          linkUrl = if (cleanedExtra.startsWith("mailto:", true)) cleanedExtra else "mailto:$cleanedExtra"
        }
      }
      WebView.HitTestResult.PHONE_TYPE -> {
        if (cleanedExtra != null) {
          linkUrl = if (cleanedExtra.startsWith("tel:", true)) cleanedExtra else "tel:$cleanedExtra"
        }
      }
      WebView.HitTestResult.GEO_TYPE -> {
        if (cleanedExtra != null) {
          linkUrl = if (cleanedExtra.startsWith("geo:", true)) cleanedExtra else "geo:0,0?q=$cleanedExtra"
        }
      }
    }

    var mediaKind: MediaKind? = null
    if (mediaUrl != null) {
      mediaKind = MediaSniffer.classifyUrl(mediaUrl, pageUrl, mediaTitle.orEmpty())?.kind ?: MediaKind.VIDEO
    } else if (linkUrl != null) {
      val classifiedLink = MediaSniffer.classifyUrl(linkUrl, pageUrl, linkText.orEmpty())
      if (classifiedLink != null) {
        mediaUrl = linkUrl
        mediaKind = classifiedLink.kind
        if (mediaTitle.isNullOrBlank()) {
          mediaTitle = classifiedLink.title
        }
      }
    }

    val resolved =
      WebContextMenuTarget(
        pageUrl = pageUrl,
        linkUrl = linkUrl,
        linkText = linkText,
        imageUrl = imageUrl,
        mediaUrl = mediaUrl,
        mediaKind = mediaKind,
        mediaTitle = mediaTitle,
      )
    return resolved.takeIf { it.hasAnyTarget }
  }

  private fun isSupportedContextUrl(url: String): Boolean {
    val low = url.trim().lowercase(Locale.US)
    return low.startsWith("http://") ||
      low.startsWith("https://") ||
      low.startsWith("mailto:") ||
      low.startsWith("tel:") ||
      low.startsWith("geo:")
  }
}
