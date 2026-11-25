package com.example.browser

import android.net.Uri
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import com.example.data.DetectedMediaItem
import com.example.data.MediaKind
import java.net.URI
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

object MediaSniffer {

  const val DEFAULT_BROWSER_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 15; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

  const val MIN_DOWNLOADABLE_VIDEO_BYTES = 0L // No strict minimum size restriction

  private val videoExtensions = setOf("mp4", "webm", "mkv", "mov", "m4v", "3gp", "avi", "flv")
  private val audioExtensions = setOf("mp3", "aac", "m4a", "ogg", "opus", "flac", "wav")
  private val playlistExtensions = setOf("m3u", "pls", "xspf")

  fun classifyUrl(url: String, pageUrl: String, pageTitle: String): DetectedMediaItem? {
    if (url.isBlank() || url.startsWith("data:") || url.startsWith("blob:")) return null
    val parsed = runCatching { Uri.parse(url) }.getOrNull() ?: return null
    val scheme = parsed.scheme?.lowercase(Locale.US)
    if (scheme != "http" && scheme != "https") return null

    val pathLower = (parsed.path ?: "").lowercase(Locale.US)
    val ext = pathLower.substringAfterLast('.', "")
    val fullLower = url.lowercase(Locale.US)

    // Skip individual HLS/DASH media chunks when master/media playlists are used
    if (ext == "ts" || ext == "m4s") return null

    val mimeParam = parsed.getQueryParameter("mime")?.lowercase(Locale.US).orEmpty()

    val (kind, mime) =
      when {
        ext == "m3u8" || fullLower.contains(".m3u8") || mimeParam.contains("mpegurl") ->
          MediaKind.STREAM_HLS to "application/x-mpegURL"
        ext == "mpd" || fullLower.contains(".mpd") || mimeParam.contains("dash+xml") ->
          MediaKind.STREAM_DASH to "application/dash+xml"
        ext in playlistExtensions ->
          MediaKind.PLAYLIST to
            when (ext) {
              "pls" -> "audio/x-scpls"
              "xspf" -> "application/xspf+xml"
              else -> "audio/x-mpegurl"
            }
        ext in videoExtensions || mimeParam.startsWith("video/") || fullLower.contains("googlevideo.com") || fullLower.contains("videoplayback") -> {
          val resolvedExt = if (ext in videoExtensions && ext != "m4v") ext else "mp4"
          MediaKind.VIDEO to "video/$resolvedExt"
        }
        ext in audioExtensions || mimeParam.startsWith("audio/") -> {
          val resolvedExt = if (ext in audioExtensions) ext else "mpeg"
          MediaKind.AUDIO to "audio/$resolvedExt"
        }
        else -> return null
      }

    val fileName =
      parsed.lastPathSegment?.takeIf { it.isNotBlank() && it.length <= 72 && it.contains('.') }
        ?: pageTitle.ifBlank { "Captured Video" }

    return DetectedMediaItem(
      url = url,
      title = fileName,
      kind = kind,
      mimeType = mime,
      sourcePageUrl = pageUrl,
    )
  }

  data class VideoProbeResult(
    val isDownloadable: Boolean,
    val contentSizeBytes: Long? = null,
    val mimeType: String? = null,
    val resolvedUrl: String? = null,
  )

  data class PlaybackHeaderContext(
    val referer: String? = null,
    val origin: String? = null,
    val userAgent: String = DEFAULT_BROWSER_USER_AGENT,
  )

  private val playbackContextByUrl = java.util.concurrent.ConcurrentHashMap<String, PlaybackHeaderContext>()
  private val playbackContextByHost = java.util.concurrent.ConcurrentHashMap<String, PlaybackHeaderContext>()

  fun registerPlaybackContext(
    mediaUrl: String,
    sourcePageUrl: String?,
    userAgent: String = DEFAULT_BROWSER_USER_AGENT,
  ) {
    val validReferer =
      sourcePageUrl?.trim()?.takeIf {
        it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
      }
    val origin =
      validReferer?.let { ref ->
        runCatching {
          val u = URI(ref)
          if (!u.scheme.isNullOrBlank() && !u.authority.isNullOrBlank()) {
            "${u.scheme}://${u.authority}"
          } else null
        }.getOrNull()
      }
    val ctx = PlaybackHeaderContext(referer = validReferer, origin = origin, userAgent = userAgent)
    playbackContextByUrl[mediaUrl] = ctx
    runCatching { URI(mediaUrl).host?.lowercase(Locale.US) }.getOrNull()?.takeIf { it.isNotBlank() }?.let { host ->
      playbackContextByHost[host] = ctx
    }
  }

  fun getPlaybackContext(url: String): PlaybackHeaderContext? {
    playbackContextByUrl[url]?.let { return it }
    val host = runCatching { URI(url).host?.lowercase(Locale.US) }.getOrNull()
    return if (!host.isNullOrBlank()) playbackContextByHost[host] else null
  }

  fun clearPlaybackContexts() {
    playbackContextByUrl.clear()
    playbackContextByHost.clear()
  }

  fun getCookiesForUrl(url: String): String? =
    runCatching { CookieManager.getInstance().getCookie(url) }
      .getOrNull()
      ?.takeIf { it.isNotBlank() }

  fun buildBrowserRequestBuilder(
    url: String,
    referer: String? = null,
    userAgent: String = DEFAULT_BROWSER_USER_AGENT,
  ): Request.Builder {
    val validReferer = referer?.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
    val builder =
      Request.Builder()
        .url(url)
        .header("User-Agent", userAgent)
        .header("Accept", "*/*")
        .header("Accept-Language", "en-US,en;q=0.9")
    if (validReferer != null) {
      builder.header("Referer", validReferer)
      runCatching {
        val u = URI(validReferer)
        if (!u.scheme.isNullOrBlank() && !u.authority.isNullOrBlank()) {
          builder.header("Origin", "${u.scheme}://${u.authority}")
        }
      }
    }
    getCookiesForUrl(url)?.let { cookieHeader ->
      builder.header("Cookie", cookieHeader)
    }
    return builder
  }

  fun isDownloadableVideoResponse(
    httpCode: Int,
    contentType: String?,
    contentLength: Long?,
    minBytes: Long = MIN_DOWNLOADABLE_VIDEO_BYTES,
  ): Boolean {
    if (httpCode !in 200..299) return false
    val mimeLower = contentType?.substringBefore(';')?.trim()?.lowercase(Locale.US).orEmpty()
    if (
      mimeLower.startsWith("text/") ||
        mimeLower.startsWith("image/") ||
        mimeLower.startsWith("audio/") ||
        mimeLower.contains("html") ||
        mimeLower.contains("json") ||
        mimeLower.contains("xml")
    ) {
      return false
    }
    return mimeLower.isEmpty() ||
      mimeLower.startsWith("video/") ||
      mimeLower == "application/octet-stream" ||
      mimeLower == "binary/octet-stream" ||
      mimeLower == "application/mp4" ||
      mimeLower.contains("stream") ||
      mimeLower.contains("mpegurl")
  }

  /**
   * Verifies that a remote URL resolves cleanly (following redirects, carrying cookies & Referer/Origin),
   * points to a downloadable binary video resource (HTTP 2xx, non-HTML/audio/error), and exceeds [minBytes]
   * (strictly > 1 MB by default for listed videos).
   */
  suspend fun probeDownloadableVideo(
    okHttpClient: OkHttpClient,
    url: String,
    referer: String? = null,
    minBytes: Long = MIN_DOWNLOADABLE_VIDEO_BYTES,
  ): VideoProbeResult =
    withContext(Dispatchers.IO) {
      val headProbe =
        runCatching {
          val req = buildBrowserRequestBuilder(url, referer).head().build()
          okHttpClient.newCall(req).execute().use { response ->
            val len = response.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0L }
            val mime = response.header("Content-Type")?.substringBefore(';')?.trim()
            val finalUrl = response.request.url.toString()
            if (response.isSuccessful) {
              val ok = isDownloadableVideoResponse(response.code, mime, len, minBytes)
              VideoProbeResult(
                isDownloadable = ok,
                contentSizeBytes = len,
                mimeType = mime,
                resolvedUrl = finalUrl,
              )
            } else {
              null
            }
          }
        }.getOrNull()

      if (headProbe != null) {
        if (headProbe.isDownloadable) return@withContext headProbe
        // If HEAD returned a small known size (<= minBytes) or HTML error page on 200 OK, reject immediately
        val headMime = headProbe.mimeType?.lowercase(Locale.US).orEmpty()
        if (headMime.contains("html") || (headProbe.contentSizeBytes != null && headProbe.contentSizeBytes <= minBytes)) {
          return@withContext headProbe
        }
      }

      // Lightweight 1-byte range GET request to follow redirects, read exact Content-Range total size & verify video MIME
      runCatching {
        val req =
          buildBrowserRequestBuilder(url, referer)
            .header("Range", "bytes=0-0")
            .get()
            .build()
        okHttpClient.newCall(req).execute().use { response ->
          val mime = response.header("Content-Type")?.substringBefore(';')?.trim()
          val finalUrl = response.request.url.toString()
          val contentRange = response.header("Content-Range")
          val totalFromRange = parseTotalSizeFromContentRange(contentRange)
          val len =
            totalFromRange
              ?: if (response.code == 200) {
                response.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0L }
              } else {
                null
              }
          val ok = isDownloadableVideoResponse(response.code, mime, len, minBytes)
          VideoProbeResult(
            isDownloadable = ok,
            contentSizeBytes = len,
            mimeType = mime,
            resolvedUrl = finalUrl,
          )
        }
      }.getOrElse {
        headProbe ?: VideoProbeResult(isDownloadable = false)
      }
    }

  /**
   * Queries exact remote file size via HTTP HEAD, with a lightweight Range: bytes=0-0 GET fallback
   * for CDNs/servers that block HEAD or omit Content-Length on HEAD.
   */
  suspend fun probeContentSize(
    okHttpClient: OkHttpClient,
    url: String,
    referer: String? = null,
  ): Long? = probeDownloadableVideo(okHttpClient, url, referer, minBytes = 0L).contentSizeBytes

  fun parseTotalSizeFromContentRange(contentRange: String?): Long? {
    if (contentRange.isNullOrBlank()) return null
    val totalPart = contentRange.substringAfter('/', "").trim()
    if (totalPart.isEmpty() || totalPart == "*") return null
    return totalPart.toLongOrNull()?.takeIf { it > 0L }
  }

  val domSnifferScript: String =
    """
    (function() {
      function classifySrc(src) {
        var low = (src || '').toLowerCase();
        if (low.indexOf('.m3u8') !== -1) return 'STREAM_HLS';
        if (low.indexOf('.mpd') !== -1) return 'STREAM_DASH';
        if (low.indexOf('.m3u') !== -1 || low.indexOf('.pls') !== -1 || low.indexOf('.xspf') !== -1) return 'PLAYLIST';
        if (low.indexOf('.mp3') !== -1 || low.indexOf('.m4a') !== -1 || low.indexOf('.aac') !== -1 || low.indexOf('.ogg') !== -1 || low.indexOf('.opus') !== -1 || low.indexOf('.flac') !== -1) return 'AUDIO';
        return 'VIDEO';
      }

      function isMediaLink(href) {
        var low = (href || '').toLowerCase().split('?')[0].split('#')[0];
        return /\.(mp4|webm|mkv|mov|m4v|3gp|avi|flv|m3u8|mpd|m3u|pls|xspf)$/.test(low);
      }

      function resolveAbsolute(raw, baseUri) {
        if (!raw) return '';
        try { return new URL(raw, baseUri || location.href).href; } catch (e) { return raw; }
      }

      function addCandidate(out, seen, rawUrl, baseUri, title, poster, duration) {
        var url = resolveAbsolute(rawUrl, baseUri);
        if (!url || url.startsWith('blob:') || url.startsWith('data:')) return;
        if (!url.startsWith('http://') && !url.startsWith('https://')) return;
        var kind = classifySrc(url);
        if (kind === 'AUDIO') return;
        if (seen[url]) return;
        seen[url] = true;
        out.push({
          url: url,
          kind: kind,
          title: title || 'Web Video',
          poster: poster || '',
          duration: duration || 0
        });
      }

      function scanDoc(doc, out, seen, baseTitle) {
        try {
          var baseUri = doc.baseURI || location.href;
          var videos = doc.querySelectorAll('video');
          videos.forEach(function(v, idx) {
            var candidates = [
              v.currentSrc, v.src,
              v.getAttribute('data-src'), v.getAttribute('data-mp4'),
              v.getAttribute('data-video'), v.getAttribute('data-hls'),
              v.getAttribute('data-stream'), v.getAttribute('data-url')
            ];
            v.querySelectorAll('source').forEach(function(s) {
              candidates.push(s.src, s.getAttribute('src'), s.getAttribute('data-src'));
            });
            var setup = v.getAttribute('data-setup');
            if (setup) {
              try {
                var parsed = JSON.parse(setup);
                if (parsed && Array.isArray(parsed.sources)) {
                  parsed.sources.forEach(function(srcObj) { if (srcObj && srcObj.src) candidates.push(srcObj.src); });
                }
              } catch (e) {}
            }
            var dur = isFinite(v.duration) ? Math.floor(v.duration) : 0;
            candidates.forEach(function(src) {
              addCandidate(out, seen, src, baseUri, baseTitle + (videos.length > 1 ? ' #' + (idx + 1) : ''), v.poster || '', dur);
            });
          });

          doc.querySelectorAll('meta[property="og:video"], meta[property="og:video:url"], meta[property="og:video:secure_url"], meta[name="twitter:player:stream"]').forEach(function(m) {
            if (m && m.content) {
              addCandidate(out, seen, m.content, baseUri, baseTitle, '', 0);
            }
          });

          doc.querySelectorAll('[data-mp4], [data-video], [data-stream], [data-file], [data-hls], [data-video-url]').forEach(function(el) {
            ['data-mp4', 'data-video', 'data-stream', 'data-file', 'data-hls', 'data-video-url'].forEach(function(attr) {
              var val = el.getAttribute(attr);
              if (val) {
                addCandidate(out, seen, val, baseUri, baseTitle, '', 0);
              }
            });
          });

          doc.querySelectorAll('a[href]').forEach(function(a) {
            var href = a.href;
            if (href && isMediaLink(href)) {
              var linkTitle = (a.innerText || a.title || '').trim();
              addCandidate(out, seen, href, baseUri, linkTitle || baseTitle, '', 0);
            }
          });

          // Scan JSON-LD VideoObject, YouTube ytInitialPlayerResponse & inline script/JSON blocks for video URLs
          var scannedScripts = window.__voltScannedScripts || (window.WeakSet ? new WeakSet() : null);
          window.__voltScannedScripts = scannedScripts;
          var urlPattern = /https?:\/\/[^"'\s<>\\]+(?:\.(?:mp4|webm|mkv|mov|m4v|m3u8|mpd)|videoplayback\?[^"'\s<>\\]+|googlevideo\.com\/videoplayback[^"'\s<>\\]*)(?:\?[^"'\s<>\\]*)?/gi;
          doc.querySelectorAll('script:not([src])').forEach(function(sc) {
            if (scannedScripts && scannedScripts.has(sc)) return;
            var txt = sc.textContent || '';
            if (scannedScripts && txt.length > 0) scannedScripts.add(sc);
            if (txt.length > 0 && txt.length < 500000) {
              if (txt.indexOf('ytInitialPlayerResponse') !== -1 || txt.indexOf('streamingData') !== -1) {
                try {
                  var jsonIdx = txt.indexOf('ytInitialPlayerResponse');
                  if (jsonIdx !== -1) {
                    var eqIdx = txt.indexOf('=', jsonIdx);
                    if (eqIdx !== -1) {
                      var sub = txt.substring(eqIdx + 1).trim();
                      if (sub.charAt(0) === '{') {
                        var braceCount = 0;
                        var endIdx = 0;
                        for (var i = 0; i < sub.length; i++) {
                          if (sub.charAt(i) === '{') braceCount++;
                          else if (sub.charAt(i) === '}') braceCount--;
                          if (braceCount === 0) {
                            endIdx = i + 1;
                            break;
                          }
                        }
                        if (endIdx > 0) {
                          var playerResp = JSON.parse(sub.substring(0, endIdx));
                          var sData = playerResp && playerResp.streamingData;
                          var vTitle = (playerResp.videoDetails && playerResp.videoDetails.title) || baseTitle;
                          var vDuration = (playerResp.videoDetails && parseInt(playerResp.videoDetails.lengthSeconds)) || 0;
                          if (sData) {
                            var formats = (sData.formats || []).concat(sData.adaptiveFormats || []);
                            formats.forEach(function(f) {
                              if (f && f.url) {
                                addCandidate(out, seen, f.url, baseUri, vTitle, '', vDuration);
                              }
                            });
                            if (sData.hlsManifestUrl) addCandidate(out, seen, sData.hlsManifestUrl, baseUri, vTitle, '', vDuration);
                            if (sData.dashManifestUrl) addCandidate(out, seen, sData.dashManifestUrl, baseUri, vTitle, '', vDuration);
                          }
                        }
                      }
                    }
                  }
                } catch (e) {}
              }
              if ((sc.type || '').indexOf('ld+json') !== -1) {
                try {
                  var ld = JSON.parse(txt);
                  var arr = Array.isArray(ld) ? ld : [ld];
                  arr.forEach(function(entry) {
                    if (entry && entry.contentUrl) addCandidate(out, seen, entry.contentUrl, baseUri, entry.name || baseTitle, entry.thumbnailUrl || '', 0);
                  });
                } catch (e) {}
              }
              var clean = txt.replace(/\\\//g, '/').replace(/\\u002F/gi, '/').replace(/\\u0026/gi, '&');
              var m;
              while ((m = urlPattern.exec(clean)) !== null) {
                addCandidate(out, seen, m[0], baseUri, baseTitle, '', 0);
              }
            }
          });
        } catch (e) {}
      }

      function postVoltMessage(type, payload) {
        try {
          if (window.VoltMediaChannel && typeof window.VoltMediaChannel.postMessage === 'function') {
            window.VoltMediaChannel.postMessage(JSON.stringify({ type: type, payload: payload }));
            return true;
          }
          if (window.VoltMediaBridge) {
            if (type === 'mediaFound' && window.VoltMediaBridge.onMediaFound) {
              window.VoltMediaBridge.onMediaFound(JSON.stringify(payload));
              return true;
            }
            if (type === 'touchTarget' && window.VoltMediaBridge.onTouchTargetDetected) {
              window.VoltMediaBridge.onTouchTargetDetected(JSON.stringify(payload));
              return true;
            }
          }
        } catch (e) {}
        return false;
      }

      function emitDynamicUrl(url) {
        try {
          if (!url || typeof url !== 'string') return;
          var abs = resolveAbsolute(url, location.href);
          if (isMediaLink(abs)) {
            var kind = classifySrc(abs);
            if (kind !== 'AUDIO') {
              postVoltMessage('mediaFound', [{
                url: abs,
                kind: kind,
                title: document.title || location.hostname || 'Web Video',
                poster: '',
                duration: 0
              }]);
            }
          }
        } catch (e) {}
      }

      if (!window.__voltNetworkHooked) {
        window.__voltNetworkHooked = true;
        try {
          var origFetch = window.fetch;
          if (origFetch) {
            window.fetch = function(input) {
              try {
                var u = typeof input === 'string' ? input : (input && input.url);
                emitDynamicUrl(u);
              } catch (e) {}
              return origFetch.apply(this, arguments);
            };
          }
          var origOpen = XMLHttpRequest.prototype.open;
          if (origOpen) {
            XMLHttpRequest.prototype.open = function(method, url) {
              try { emitDynamicUrl(url); } catch (e) {}
              return origOpen.apply(this, arguments);
            };
          }
        } catch (e) {}
      }

      window.__voltScanMedia = function() {
        try {
          var found = [];
          var seen = {};
          var pageTitle = document.title || location.hostname || 'Web Video';
          scanDoc(document, found, seen, pageTitle);
          var frames = document.querySelectorAll('iframe');
          frames.forEach(function(f) {
            try {
              if (f.contentDocument) scanDoc(f.contentDocument, found, seen, pageTitle);
            } catch (e) {}
          });
          if (found.length > 0) {
            postVoltMessage('mediaFound', found.slice(0, 40));
          }
        } catch (e) {}
      };

      if (!window.__voltSnifferActive) {
        window.__voltSnifferActive = true;
        document.addEventListener('play', window.__voltScanMedia, true);
        document.addEventListener('loadedmetadata', window.__voltScanMedia, true);
        if (window.MutationObserver && document.body) {
          var obs = new MutationObserver(function() {
            clearTimeout(window.__voltDebounce);
            window.__voltDebounce = setTimeout(window.__voltScanMedia, 600);
          });
          obs.observe(document.body, { childList: true, subtree: true });
        }
      }

      if (!window.__voltContextTouchHooked) {
        window.__voltContextTouchHooked = true;
        window.__voltHadTouchTarget = false;
        function inspectTouchContext(target, clientX, clientY) {
          try {
            var linkUrl = '', linkText = '', imageUrl = '', mediaUrl = '', mediaTitle = '';
            var els = [];
            if (typeof clientX === 'number' && typeof clientY === 'number' && document.elementsFromPoint) {
              els = Array.prototype.slice.call(document.elementsFromPoint(clientX, clientY) || []);
            }
            if (target && els.indexOf(target) === -1) els.unshift(target);
            for (var i = 0; i < els.length; i++) {
              var el = els[i];
              if (!el || !el.tagName) continue;
              var tag = el.tagName.toUpperCase();
              if (!linkUrl) {
                var a = tag === 'A' ? el : (el.closest ? el.closest('a[href]') : null);
                if (a && a.href) {
                  linkUrl = a.href;
                  linkText = (a.innerText || a.getAttribute('aria-label') || a.title || '').trim();
                }
              }
              if (!imageUrl && tag === 'IMG' && (el.currentSrc || el.src)) {
                imageUrl = el.currentSrc || el.src;
              }
              if (!mediaUrl && (tag === 'VIDEO' || tag === 'AUDIO')) {
                var src = el.currentSrc || el.src;
                if (!src && el.querySelector) {
                  var s = el.querySelector('source[src]');
                  if (s) src = s.src || s.getAttribute('src') || '';
                }
                if (src) {
                  mediaUrl = src;
                  mediaTitle = el.getAttribute('title') || el.getAttribute('aria-label') || document.title || '';
                }
                if (!imageUrl && el.poster) imageUrl = el.poster;
              }
            }
            var hasTarget = !!(linkUrl || imageUrl || mediaUrl);
            if (!hasTarget && !window.__voltHadTouchTarget) return;
            window.__voltHadTouchTarget = hasTarget;
            if (hasTarget) {
              postVoltMessage('touchTarget', {
                linkUrl: linkUrl || '',
                linkText: linkText || '',
                imageUrl: imageUrl || '',
                mediaUrl: mediaUrl || '',
                mediaTitle: mediaTitle || ''
              });
            }
          } catch (e) {}
        }
        document.addEventListener('touchstart', function(e) {
          var t = e.touches && e.touches[0];
          inspectTouchContext(e.target, t ? t.clientX : null, t ? t.clientY : null);
        }, { capture: true, passive: true });
        document.addEventListener('contextmenu', function(e) {
          inspectTouchContext(e.target, e.clientX, e.clientY);
        }, { capture: true, passive: true });
      }
      window.__voltScanMedia();
      setTimeout(window.__voltScanMedia, 1000);
    })();
    """
      .trimIndent()
}

class VoltMediaJsBridge(
  private val onDetected: (List<DetectedMediaItem>) -> Unit,
  private val currentPageUrlProvider: () -> String,
  private val onBlobDownloaded: (dataUrl: String, suggestedName: String, mimeType: String) -> Unit = { _, _, _ -> },
  private val onTouchTarget: (linkUrl: String?, linkText: String?, imageUrl: String?, mediaUrl: String?, mediaTitle: String?) -> Unit = { _, _, _, _, _ -> },
) {
  fun handleWebMessage(rawMessage: String) {
    if (rawMessage.isBlank() || rawMessage.length > MAX_WEB_MESSAGE_CHARS) return
    runCatching {
      val envelope = JSONObject(rawMessage)
      when (envelope.optString("type")) {
        "mediaFound" -> {
          val array = envelope.optJSONArray("payload") ?: return
          val serialized = array.toString()
          if (serialized.length <= MAX_MEDIA_JSON_CHARS) {
            parseAndEmitMediaArray(array)
          }
        }
        "touchTarget" -> {
          val payload = envelope.optJSONObject("payload") ?: return
          val serialized = payload.toString()
          if (serialized.length <= MAX_TOUCH_JSON_CHARS) {
            handleTouchTargetObject(payload)
          }
        }
        "blobDownload" -> {
          val payload = envelope.optJSONObject("payload") ?: return
          onBlobDownloadData(
            dataUrl = payload.optString("dataUrl"),
            suggestedName = payload.optString("suggestedName"),
            mimeType = payload.optString("mimeType"),
          )
        }
      }
    }
  }

  @JavascriptInterface
  fun onTouchTargetDetected(jsonPayload: String) {
    if (jsonPayload.isBlank() || jsonPayload.length > MAX_TOUCH_JSON_CHARS) return
    runCatching {
      handleTouchTargetObject(JSONObject(jsonPayload))
    }
  }

  private fun handleTouchTargetObject(obj: JSONObject) {
    val linkUrl = obj.optString("linkUrl").take(MAX_URL_LENGTH).takeIf { it.isNotBlank() }
    val linkText = obj.optString("linkText").take(MAX_TITLE_LENGTH).takeIf { it.isNotBlank() }
    val imageUrl = obj.optString("imageUrl").take(MAX_URL_LENGTH).takeIf { it.isNotBlank() }
    val mediaUrl = obj.optString("mediaUrl").take(MAX_URL_LENGTH).takeIf { it.isNotBlank() }
    val mediaTitle = obj.optString("mediaTitle").take(MAX_TITLE_LENGTH).takeIf { it.isNotBlank() }
    onTouchTarget(linkUrl, linkText, imageUrl, mediaUrl, mediaTitle)
  }

  @JavascriptInterface
  fun onBlobDownloadData(dataUrl: String, suggestedName: String, mimeType: String) {
    if (dataUrl.startsWith("data:", ignoreCase = true) && dataUrl.length <= MAX_BLOB_DATA_URL_CHARS) {
      onBlobDownloaded(dataUrl, suggestedName.take(MAX_TITLE_LENGTH), mimeType.take(120))
    }
  }

  @JavascriptInterface
  fun onMediaFound(jsonPayload: String) {
    if (jsonPayload.isBlank() || jsonPayload.length > MAX_MEDIA_JSON_CHARS) return
    runCatching {
      parseAndEmitMediaArray(JSONArray(jsonPayload))
    }
  }

  private fun parseAndEmitMediaArray(array: JSONArray) {
    val pageUrl = currentPageUrlProvider()
    val count = minOf(array.length(), MAX_MEDIA_ITEMS)
    val items = buildList {
      for (i in 0 until count) {
        val obj = array.optJSONObject(i) ?: continue
        val url = obj.optString("url").take(MAX_URL_LENGTH)
        if (url.isBlank() || (!url.startsWith("http://") && !url.startsWith("https://"))) {
          continue
        }
        val kindStr = obj.optString("kind", "VIDEO")
        val kind =
          when (kindStr) {
            "AUDIO" -> MediaKind.AUDIO
            "STREAM_HLS" -> MediaKind.STREAM_HLS
            "STREAM_DASH" -> MediaKind.STREAM_DASH
            "PLAYLIST" -> MediaKind.PLAYLIST
            else -> MediaKind.VIDEO
          }
        val mime =
          when (kind) {
            MediaKind.AUDIO -> "audio/mpeg"
            MediaKind.STREAM_HLS -> "application/x-mpegURL"
            MediaKind.STREAM_DASH -> "application/dash+xml"
            MediaKind.PLAYLIST -> "audio/x-mpegurl"
            MediaKind.VIDEO -> "video/mp4"
          }
        val duration = obj.optLong("duration", 0L).takeIf { it > 0L }
        val poster = obj.optString("poster").take(MAX_URL_LENGTH).takeIf { it.isNotBlank() }
        add(
          DetectedMediaItem(
            url = url,
            title = obj.optString("title").take(MAX_TITLE_LENGTH).ifBlank { "Web Video" },
            kind = kind,
            mimeType = mime,
            sourcePageUrl = pageUrl,
            posterUrl = poster,
            durationSeconds = duration,
          )
        )
      }
    }
    if (items.isNotEmpty()) {
      onDetected(items)
    }
  }

  companion object {
    const val MAX_MEDIA_JSON_CHARS = 65_536
    const val MAX_MEDIA_ITEMS = 40
    const val MAX_TOUCH_JSON_CHARS = 16_384
    const val MAX_BLOB_DATA_URL_CHARS = 36_000_000
    const val MAX_WEB_MESSAGE_CHARS = 37_000_000
    const val MAX_URL_LENGTH = 4096
    const val MAX_TITLE_LENGTH = 240
  }
}
