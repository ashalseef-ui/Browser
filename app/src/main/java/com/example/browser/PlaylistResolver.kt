package com.example.browser

import com.example.data.DetectedMediaItem
import com.example.data.MediaKind
import java.net.URI
import java.util.Locale
import kotlin.math.roundToLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class PlaylistResolutionResult(
  val updatedParent: DetectedMediaItem,
  val resolvedItems: List<DetectedMediaItem>,
  val isParentDownloadableVideo: Boolean = false,
)

object PlaylistResolver {

  private data class FetchedPlaylist(
    val finalUrl: String,
    val text: String?,
    val directVideoMime: String? = null,
  )

  suspend fun resolve(
    okHttpClient: OkHttpClient,
    item: DetectedMediaItem,
  ): PlaylistResolutionResult =
    withContext(Dispatchers.IO) {
      val fetched = fetchPlaylistSafe(okHttpClient, item.url, item.sourcePageUrl)
      if (fetched == null) {
        return@withContext PlaylistResolutionResult(
          updatedParent = item.copy(isResolvingMetadata = false),
          resolvedItems = emptyList(),
        )
      }

      // If the URL redirected directly to a binary video file (e.g. .mp4 / video/*), return it directly!
      if (fetched.directVideoMime != null) {
        val resolvedVideo =
          item.copy(
            url = fetched.finalUrl,
            kind = MediaKind.VIDEO,
            mimeType = fetched.directVideoMime,
            isResolvingMetadata = false,
          )
        return@withContext PlaylistResolutionResult(
          updatedParent = resolvedVideo,
          resolvedItems = listOf(resolvedVideo),
          isParentDownloadableVideo = false,
        )
      }

      val text = fetched.text
      if (text.isNullOrBlank()) {
        return@withContext PlaylistResolutionResult(
          updatedParent = item.copy(isResolvingMetadata = false),
          resolvedItems = emptyList(),
        )
      }

      val resolvedItem = if (fetched.finalUrl != item.url) item.copy(url = fetched.finalUrl) else item
      val lowerUrl = resolvedItem.url.lowercase(Locale.US)
      val trimmed = text.trimStart()

      when {
        trimmed.startsWith("#EXTM3U", ignoreCase = true) &&
          (trimmed.contains("#EXT-X-STREAM-INF", ignoreCase = true) ||
            trimmed.contains("#EXT-X-TARGETDURATION", ignoreCase = true) ||
            trimmed.contains("#EXT-X-MEDIA-SEQUENCE", ignoreCase = true) ||
            lowerUrl.contains(".m3u8")) -> {
          resolveM3u8(okHttpClient, resolvedItem, text)
        }

        trimmed.contains("<MPD", ignoreCase = true) || lowerUrl.contains(".mpd") -> {
          val variants = filterDownloadableVideoCandidates(parseDashMpd(resolvedItem, text))
          val best = variants.maxByOrNull { it.bitrateBps ?: 0L }
          PlaylistResolutionResult(
            updatedParent =
              resolvedItem.copy(
                isResolvingMetadata = false,
                durationSeconds = resolvedItem.durationSeconds ?: best?.durationSeconds,
                contentSizeBytes = resolvedItem.contentSizeBytes ?: best?.contentSizeBytes,
                isSizeEstimated = best?.contentSizeBytes != null,
              ),
            resolvedItems = variants,
            isParentDownloadableVideo = false,
          )
        }

        trimmed.startsWith("[playlist]", ignoreCase = true) || lowerUrl.endsWith(".pls") -> {
          val tracks = filterDownloadableVideoCandidates(parsePlsPlaylist(resolvedItem, text))
          PlaylistResolutionResult(
            updatedParent = resolvedItem.copy(isResolvingMetadata = false, segmentCount = tracks.size),
            resolvedItems = tracks,
            isParentDownloadableVideo = false,
          )
        }

        (trimmed.contains("<playlist", ignoreCase = true) && trimmed.contains("<trackList", ignoreCase = true)) ||
          lowerUrl.endsWith(".xspf") -> {
          val tracks = filterDownloadableVideoCandidates(parseXspfPlaylist(resolvedItem, text))
          PlaylistResolutionResult(
            updatedParent = resolvedItem.copy(isResolvingMetadata = false, segmentCount = tracks.size),
            resolvedItems = tracks,
            isParentDownloadableVideo = false,
          )
        }

        trimmed.startsWith("#EXTM3U", ignoreCase = true) ||
          lowerUrl.endsWith(".m3u") -> {
          val tracks = filterDownloadableVideoCandidates(parseStandardM3u(resolvedItem, text))
          PlaylistResolutionResult(
            updatedParent = resolvedItem.copy(isResolvingMetadata = false, segmentCount = tracks.size),
            resolvedItems = tracks,
            isParentDownloadableVideo = false,
          )
        }

        else -> {
          // HTML/JSON/text response: extract any embedded downloadable video or .m3u8 links
          val extracted =
            filterDownloadableVideoCandidates(
              extractMediaLinksFromText(resolvedItem.url, resolvedItem.title, text)
            )
          PlaylistResolutionResult(
            updatedParent = resolvedItem.copy(isResolvingMetadata = false, segmentCount = extracted.size.takeIf { it > 0 }),
            resolvedItems = extracted,
            isParentDownloadableVideo = false,
          )
        }
      }
    }

  private fun filterDownloadableVideoCandidates(items: List<DetectedMediaItem>): List<DetectedMediaItem> =
    items.filter { candidate ->
      (candidate.kind == MediaKind.VIDEO || candidate.kind == MediaKind.STREAM_HLS) &&
        !candidate.url.contains("#dash_")
    }

  private suspend fun resolveM3u8(
    okHttpClient: OkHttpClient,
    parent: DetectedMediaItem,
    playlistText: String,
  ): PlaylistResolutionResult = coroutineScope {
    val parsedVariants =
      parseHlsMasterVariants(parent, playlistText).filter { it.kind == MediaKind.STREAM_HLS }
    if (parsedVariants.isNotEmpty()) {
      // Master playlist: inspect top 6 quality variants in parallel for duration, segments, .mp4 links & reachability
      val inspectedTriples =
        parsedVariants.take(6).map { variant ->
          async {
            val mediaText = fetchPlaylistText(okHttpClient, variant.url, parent.sourcePageUrl)
            if (mediaText != null) {
              inspectHlsMediaPlaylist(okHttpClient, variant, mediaText)
            } else {
              Triple(variant.copy(isResolvingMetadata = false), emptyList(), false)
            }
          }
        }.awaitAll()

      val downloadableVariants =
        inspectedTriples.filter { it.third }.map { it.first }
      val extractedDirectMp4s =
        inspectedTriples.flatMap { it.second }.distinctBy { it.url }

      // If direct .mp4 files were extracted (e.g. byte-range single MP4), surface those first; also keep verified downloadable HLS variants
      val combined = (extractedDirectMp4s + downloadableVariants).distinctBy { it.url }
      val best = downloadableVariants.maxByOrNull { it.bitrateBps ?: 0L }
      return@coroutineScope PlaylistResolutionResult(
        updatedParent =
          parent.copy(
            isResolvingMetadata = false,
            durationSeconds = parent.durationSeconds ?: best?.durationSeconds,
            contentSizeBytes = parent.contentSizeBytes ?: best?.contentSizeBytes,
            isSizeEstimated = best?.contentSizeBytes != null,
            segmentCount = best?.segmentCount,
          ),
        resolvedItems = combined,
        // Master playlist container itself is replaced by concrete downloadable variants / MP4s
        isParentDownloadableVideo = false,
      )
    }

    // Direct media playlist (no #EXT-X-STREAM-INF)
    val (inspectedSelf, directMp4s, isDownloadable) =
      inspectHlsMediaPlaylist(okHttpClient, parent, playlistText)
    PlaylistResolutionResult(
      updatedParent = inspectedSelf.copy(isResolvingMetadata = false),
      resolvedItems = directMp4s,
      // If directMp4s exist for a single byte-range MP4, prefer the clean .mp4 entries instead of duplicate .m3u8
      isParentDownloadableVideo = isDownloadable && directMp4s.isEmpty(),
    )
  }

  private suspend fun inspectHlsMediaPlaylist(
    okHttpClient: OkHttpClient,
    item: DetectedMediaItem,
    mediaPlaylistText: String,
  ): Triple<DetectedMediaItem, List<DetectedMediaItem>, Boolean> {
    val stats = parseHlsMediaPlaylistStats(item.url, mediaPlaylistText)
    if (stats.isDrmProtected || stats.segmentUrls.isEmpty()) {
      return Triple(
        item.copy(isResolvingMetadata = false, segmentCount = 0),
        emptyList(),
        false,
      )
    }

    val durationSec = item.durationSeconds ?: stats.totalDurationSeconds.takeIf { it > 0L }
    var streamMeetsThreshold = false
    var estimatedBytes: Long? = null

    if (stats.directMp4Urls.size == 1 && stats.hasByteRange) {
      val probe =
        MediaSniffer.probeDownloadableVideo(
          okHttpClient = okHttpClient,
          url = stats.directMp4Urls.first(),
          referer = item.sourcePageUrl,
          minBytes = MediaSniffer.MIN_DOWNLOADABLE_VIDEO_BYTES,
        )
      streamMeetsThreshold = probe.isDownloadable
      estimatedBytes = probe.contentSizeBytes
    } else {
      val segProbe =
        MediaSniffer.probeDownloadableVideo(
          okHttpClient = okHttpClient,
          url = stats.segmentUrls.first(),
          referer = item.sourcePageUrl,
          minBytes = 0L,
        )
      val sampleSize = segProbe.contentSizeBytes
      estimatedBytes =
        when {
          item.bitrateBps != null && item.bitrateBps > 0L && durationSec != null && durationSec > 0L ->
            (item.bitrateBps / 8L) * durationSec
          sampleSize != null && sampleSize > 0L -> sampleSize * stats.segmentUrls.size
          else -> null
        }
      streamMeetsThreshold = segProbe.isDownloadable
    }

    val updatedItem =
      item.copy(
        isResolvingMetadata = false,
        durationSeconds = durationSec,
        segmentCount = stats.segmentUrls.size,
        contentSizeBytes = item.contentSizeBytes ?: estimatedBytes,
        isSizeEstimated = item.contentSizeBytes == null && estimatedBytes != null && !(stats.directMp4Urls.size == 1 && stats.hasByteRange),
      )

    if (!streamMeetsThreshold) {
      return Triple(updatedItem, emptyList(), false)
    }

    // Surface extracted .mp4 links (e.g. byte-range single MP4 or direct .mp4 clips)
    val directMp4Items =
      stats.directMp4Urls.mapIndexed { idx, mp4Url ->
        val label = item.resolutionLabel?.let { "$it MP4" } ?: "MP4"
        val cleanTitle =
          if (stats.directMp4Urls.size > 1) "${item.title.substringBefore(" [")} [$label #${idx + 1}]"
          else "${item.title.substringBefore(" [")} [$label]"
        DetectedMediaItem(
          url = mp4Url,
          title = cleanTitle,
          kind = MediaKind.VIDEO,
          mimeType = "video/mp4",
          sourcePageUrl = item.sourcePageUrl,
          posterUrl = item.posterUrl,
          durationSeconds = durationSec,
          resolutionLabel = label,
          bitrateBps = item.bitrateBps,
          parentPlaylistUrl = item.url,
        )
      }

    return Triple(updatedItem, directMp4Items, true)
  }

  private fun fetchPlaylistText(
    okHttpClient: OkHttpClient,
    url: String,
    referer: String?,
  ): String? = fetchPlaylistSafe(okHttpClient, url, referer)?.text

  private fun fetchPlaylistSafe(
    okHttpClient: OkHttpClient,
    url: String,
    referer: String?,
  ): FetchedPlaylist? =
    runCatching {
      val req = MediaSniffer.buildBrowserRequestBuilder(url, referer).build()
      okHttpClient.newCall(req).execute().use { response ->
        if (!response.isSuccessful) return@use null
        val finalUrl = response.request.url.toString()
        val mime = response.header("Content-Type")?.substringBefore(';')?.trim()?.lowercase(Locale.US).orEmpty()
        if (mime.startsWith("video/") || mime == "application/octet-stream" || mime == "binary/octet-stream") {
          return@use FetchedPlaylist(finalUrl = finalUrl, text = null, directVideoMime = mime.ifBlank { "video/mp4" })
        }
        val body = response.body ?: return@use null
        val contentLength = response.header("Content-Length")?.toLongOrNull() ?: -1L
        if (contentLength > 5_000_000L) {
          // Guard against accidentally reading a giant binary media stream as text
          return@use FetchedPlaylist(finalUrl = finalUrl, text = null, directVideoMime = "video/mp4")
        }
        FetchedPlaylist(finalUrl = finalUrl, text = body.string())
      }
    }.getOrNull()

  fun parseHlsMasterVariants(
    parent: DetectedMediaItem,
    playlistText: String,
  ): List<DetectedMediaItem> {
    val lines = playlistText.lines()
    val base = runCatching { URI(parent.url) }.getOrNull() ?: return emptyList()
    val variants = mutableListOf<DetectedMediaItem>()
    val seenUrls = HashSet<String>()

    for (i in lines.indices) {
      val line = lines[i].trim()
      if (line.startsWith("#EXT-X-STREAM-INF:", ignoreCase = true)) {
        val attrs = line.substringAfter(':')
        val bandwidth =
          Regex("BANDWIDTH=(\\d+)", RegexOption.IGNORE_CASE)
            .find(attrs)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
        val resolution =
          Regex("RESOLUTION=(\\d+x\\d+)", RegexOption.IGNORE_CASE)
            .find(attrs)
            ?.groupValues
            ?.getOrNull(1)
        val nameAttr =
          Regex("NAME=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
            .find(attrs)
            ?.groupValues
            ?.getOrNull(1)

        val nextUriLine =
          lines
            .drop(i + 1)
            .firstOrNull { candidate ->
              val t = candidate.trim()
              t.isNotEmpty() && !t.startsWith("#")
            }
            ?.trim() ?: continue

        val resolvedUrl = runCatching { base.resolve(nextUriLine).toString() }.getOrNull() ?: continue
        if (!seenUrls.add(resolvedUrl)) continue

        val heightLabel =
          resolution
            ?.substringAfter('x')
            ?.toIntOrNull()
            ?.let { "${it}p" }
            ?: nameAttr
            ?: bandwidth?.let { "${it / 1000}kbps" }
            ?: "Variant"

        val cleanTitle = "${parent.title.substringBefore(" [")} [$heightLabel]"
        variants.add(
          DetectedMediaItem(
            url = resolvedUrl,
            title = cleanTitle,
            kind = MediaKind.STREAM_HLS,
            mimeType = "application/x-mpegURL",
            sourcePageUrl = parent.sourcePageUrl,
            posterUrl = parent.posterUrl,
            durationSeconds = parent.durationSeconds,
            resolutionLabel = heightLabel,
            bitrateBps = bandwidth,
            parentPlaylistUrl = parent.url,
          )
        )
      } else if (line.startsWith("#EXT-X-MEDIA:", ignoreCase = true)) {
        val uriAttr =
          Regex("""URI="([^"]+)"""", RegexOption.IGNORE_CASE)
            .find(line)
            ?.groupValues
            ?.getOrNull(1) ?: continue
        val resolvedUrl = runCatching { base.resolve(uriAttr).toString() }.getOrNull() ?: continue
        if (!seenUrls.add(resolvedUrl)) continue
        val isAudio = line.contains("TYPE=AUDIO", ignoreCase = true)
        val nameAttr =
          Regex("""NAME="([^"]+)"""", RegexOption.IGNORE_CASE)
            .find(line)
            ?.groupValues
            ?.getOrNull(1) ?: if (isAudio) "Audio" else "Track"
        variants.add(
          DetectedMediaItem(
            url = resolvedUrl,
            title = "${parent.title.substringBefore(" [")} [$nameAttr]",
            kind = if (isAudio) MediaKind.AUDIO else MediaKind.STREAM_HLS,
            mimeType = if (isAudio) "audio/mp4" else "application/x-mpegURL",
            sourcePageUrl = parent.sourcePageUrl,
            durationSeconds = parent.durationSeconds,
            resolutionLabel = nameAttr,
            parentPlaylistUrl = parent.url,
          )
        )
      }
    }

    return variants.sortedByDescending { it.bitrateBps ?: 0L }
  }

  data class HlsMediaStats(
    val initMapUrl: String? = null,
    val segmentUrls: List<String>,
    val directMp4Urls: List<String> = emptyList(),
    val hasByteRange: Boolean = false,
    val isDrmProtected: Boolean = false,
    val totalDurationSeconds: Long,
  )

  fun parseHlsMediaPlaylistStats(playlistUrl: String, playlistText: String): HlsMediaStats {
    val base = runCatching { URI(playlistUrl) }.getOrNull()
    val segments = mutableListOf<String>()
    val directMp4Set = LinkedHashSet<String>()
    var initMapUrl: String? = null
    var hasByteRange = false
    var isDrmProtected = false
    var totalDuration = 0.0

    playlistText.lineSequence().forEach { raw ->
      val line = raw.trim()
      when {
        line.isEmpty() -> Unit
        line.startsWith("#EXT-X-KEY:", ignoreCase = true) ||
          line.startsWith("#EXT-X-SESSION-KEY:", ignoreCase = true) -> {
          val upper = line.uppercase(Locale.US)
          if (
            upper.contains("SAMPLE-AES") ||
              upper.contains("WIDEVINE") ||
              upper.contains("PLAYREADY") ||
              upper.contains("STREAMINGKEYDELIVERY") ||
              upper.contains("EDEF8BA9")
          ) {
            isDrmProtected = true
          }
        }
        line.startsWith("#EXT-X-MAP:", ignoreCase = true) -> {
          val uriAttr =
            Regex("""URI="([^"]+)"""", RegexOption.IGNORE_CASE)
              .find(line)
              ?.groupValues
              ?.getOrNull(1)
          if (!uriAttr.isNullOrBlank()) {
            val resolved =
              if (base != null) runCatching { base.resolve(uriAttr).toString() }.getOrNull()
              else uriAttr
            if (!resolved.isNullOrBlank()) {
              initMapUrl = resolved
            }
          }
        }
        line.startsWith("#EXT-X-BYTERANGE:", ignoreCase = true) -> {
          hasByteRange = true
        }
        line.startsWith("#EXTINF:", ignoreCase = true) -> {
          val durStr = line.removePrefix("#EXTINF:").substringBefore(',').trim()
          totalDuration += durStr.toDoubleOrNull() ?: 0.0
        }
        !line.startsWith("#") -> {
          val resolved =
            if (base != null) runCatching { base.resolve(line).toString() }.getOrNull()
            else line
          if (!resolved.isNullOrBlank()) {
            segments.add(resolved)
            val pathLower = resolved.substringBefore('?').substringBefore('#').lowercase(Locale.US)
            if (pathLower.endsWith(".mp4") || pathLower.endsWith(".m4v") || pathLower.endsWith(".webm")) {
              directMp4Set.add(resolved)
            }
          }
        }
      }
    }

    // Only treat segment .mp4 URLs as standalone downloadable MP4 files when:
    // (a) byte-range playlist references a full MP4 file (1 or few unique base URLs), OR
    // (b) a small playlist lists full MP4 clips directly (<= 10 unique .mp4 URLs)
    val surfacedMp4s =
      if (hasByteRange || directMp4Set.size <= 10) directMp4Set.toList()
      else emptyList()

    return HlsMediaStats(
      initMapUrl = initMapUrl,
      segmentUrls = segments,
      directMp4Urls = surfacedMp4s,
      hasByteRange = hasByteRange,
      isDrmProtected = isDrmProtected,
      totalDurationSeconds = totalDuration.roundToLong(),
    )
  }

  fun parseDashMpd(parent: DetectedMediaItem, mpdXml: String): List<DetectedMediaItem> {
    val rootBase = runCatching { URI(parent.url) }.getOrNull()
    val mpdDurationMatch =
      Regex("""mediaPresentationDuration\s*=\s*"([^"]+)"""", RegexOption.IGNORE_CASE).find(mpdXml)
    val durationSec =
      mpdDurationMatch?.groupValues?.getOrNull(1)?.let { parseIso8601DurationSeconds(it) }
        ?: parent.durationSeconds

    val results = mutableListOf<DetectedMediaItem>()
    val seenUrls = HashSet<String>()

    val repRegex =
      Regex("""<Representation\b([^>]*)(?:>(.*?)</Representation>|/>)""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

    repRegex.findAll(mpdXml).forEach { match ->
      val attrs = match.groupValues[1]
      val body = match.groupValues.getOrNull(2).orEmpty()

      val bandwidth =
        Regex("""bandwidth\s*=\s*"(\d+)"""", RegexOption.IGNORE_CASE)
          .find(attrs)
          ?.groupValues
          ?.getOrNull(1)
          ?.toLongOrNull()
      val height =
        Regex("""height\s*=\s*"(\d+)"""", RegexOption.IGNORE_CASE)
          .find(attrs)
          ?.groupValues
          ?.getOrNull(1)
          ?.toIntOrNull()
      val mime =
        Regex("""mimeType\s*=\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
          .find(attrs)
          ?.groupValues
          ?.getOrNull(1)

      val baseUrlMatch =
        Regex("""<BaseURL[^>]*>([^<]+)</BaseURL>""", RegexOption.IGNORE_CASE).find(body)
      val rawBaseUrl = baseUrlMatch?.groupValues?.getOrNull(1)?.trim()

      val resolvedDirectUrl =
        if (!rawBaseUrl.isNullOrBlank() && rootBase != null) {
          runCatching { rootBase.resolve(rawBaseUrl).toString() }.getOrNull()
        } else {
          null
        }

      val isAudioOnly = mime?.startsWith("audio/", ignoreCase = true) == true
      val label =
        when {
          height != null -> "${height}p"
          isAudioOnly -> "Audio"
          bandwidth != null -> "${bandwidth / 1000}kbps"
          else -> "DASH"
        }

      if (!resolvedDirectUrl.isNullOrBlank() && seenUrls.add(resolvedDirectUrl)) {
        val kind = if (isAudioOnly) MediaKind.AUDIO else MediaKind.VIDEO
        results.add(
          DetectedMediaItem(
            url = resolvedDirectUrl,
            title = "${parent.title.substringBefore(" [")} [$label]",
            kind = kind,
            mimeType = mime ?: if (isAudioOnly) "audio/mp4" else "video/mp4",
            sourcePageUrl = parent.sourcePageUrl,
            durationSeconds = durationSec,
            contentSizeBytes =
              if (bandwidth != null && durationSec != null && durationSec > 0L) {
                (bandwidth / 8L) * durationSec
              } else null,
            isSizeEstimated = bandwidth != null && durationSec != null && durationSec > 0L,
            resolutionLabel = label,
            bitrateBps = bandwidth,
            parentPlaylistUrl = parent.url,
          )
        )
      } else if (bandwidth != null && durationSec != null && durationSec > 0L) {
        val estSize = (bandwidth / 8L) * durationSec
        results.add(
          DetectedMediaItem(
            url = "${parent.url}#dash_$label",
            title = "${parent.title.substringBefore(" [")} [$label]",
            kind = MediaKind.STREAM_DASH,
            mimeType = mime ?: "application/dash+xml",
            sourcePageUrl = parent.sourcePageUrl,
            durationSeconds = durationSec,
            contentSizeBytes = estSize,
            isSizeEstimated = true,
            resolutionLabel = label,
            bitrateBps = bandwidth,
            parentPlaylistUrl = parent.url,
          )
        )
      }
    }

    return results.distinctBy { it.url }.sortedByDescending { it.bitrateBps ?: 0L }
  }

  fun parseStandardM3u(parent: DetectedMediaItem, m3uText: String): List<DetectedMediaItem> {
    val base = runCatching { URI(parent.url) }.getOrNull()
    val lines = m3uText.lines()
    val items = mutableListOf<DetectedMediaItem>()
    val seen = HashSet<String>()

    var pendingTitle: String? = null
    var pendingDuration: Long? = null

    for (raw in lines) {
      val line = raw.trim()
      if (line.isEmpty()) continue
      if (line.startsWith("#EXTINF:", ignoreCase = true)) {
        val meta = line.removePrefix("#EXTINF:")
        pendingDuration = meta.substringBefore(',').trim().toDoubleOrNull()?.roundToLong()?.takeIf { it > 0L }
        pendingTitle = meta.substringAfter(',', "").trim().takeIf { it.isNotBlank() }
        continue
      }
      if (line.startsWith("#")) continue

      val resolved =
        if (base != null) runCatching { base.resolve(line).toString() }.getOrNull() else line
      if (resolved.isNullOrBlank() || !resolved.startsWith("http", ignoreCase = true)) {
        pendingTitle = null
        pendingDuration = null
        continue
      }
      if (!seen.add(resolved)) {
        pendingTitle = null
        pendingDuration = null
        continue
      }

      val classified =
        MediaSniffer.classifyUrl(resolved, parent.sourcePageUrl, pendingTitle ?: parent.title)
          ?: DetectedMediaItem(
            url = resolved,
            title = pendingTitle ?: "Playlist Media",
            kind = MediaKind.VIDEO,
            mimeType = "video/mp4",
            sourcePageUrl = parent.sourcePageUrl,
          )

      items.add(
        classified.copy(
          title = pendingTitle ?: classified.title,
          durationSeconds = pendingDuration ?: classified.durationSeconds,
          parentPlaylistUrl = parent.url,
        )
      )
      pendingTitle = null
      pendingDuration = null
    }

    return items
  }

  fun parsePlsPlaylist(parent: DetectedMediaItem, plsText: String): List<DetectedMediaItem> {
    val base = runCatching { URI(parent.url) }.getOrNull()
    val files = LinkedHashMap<String, String>()
    val titles = HashMap<String, String>()
    val lengths = HashMap<String, Long>()

    plsText.lineSequence().forEach { raw ->
      val line = raw.trim()
      val eq = line.indexOf('=')
      if (eq <= 0) return@forEach
      val key = line.substring(0, eq).trim()
      val value = line.substring(eq + 1).trim()
      when {
        key.startsWith("File", ignoreCase = true) -> files[key.drop(4)] = value
        key.startsWith("Title", ignoreCase = true) -> titles[key.drop(5)] = value
        key.startsWith("Length", ignoreCase = true) -> {
          value.toLongOrNull()?.takeIf { it > 0L }?.let { lengths[key.drop(6)] = it }
        }
      }
    }

    return files.entries.mapNotNull { (indexKey, rawUrl) ->
      val resolved =
        if (base != null) runCatching { base.resolve(rawUrl).toString() }.getOrNull() else rawUrl
      if (resolved.isNullOrBlank() || !resolved.startsWith("http", ignoreCase = true)) return@mapNotNull null
      val entryTitle = titles[indexKey]?.takeIf { it.isNotBlank() } ?: parent.title
      val classified =
        MediaSniffer.classifyUrl(resolved, parent.sourcePageUrl, entryTitle)
          ?: DetectedMediaItem(
            url = resolved,
            title = entryTitle,
            kind = MediaKind.VIDEO,
            mimeType = "video/mp4",
            sourcePageUrl = parent.sourcePageUrl,
          )
      classified.copy(
        title = entryTitle,
        durationSeconds = lengths[indexKey] ?: classified.durationSeconds,
        parentPlaylistUrl = parent.url,
      )
    }
  }

  fun parseXspfPlaylist(parent: DetectedMediaItem, xspfText: String): List<DetectedMediaItem> {
    val base = runCatching { URI(parent.url) }.getOrNull()
    val trackRegex = Regex("""<track\b[^>]*>(.*?)</track>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    val locRegex = Regex("""<location>([^<]+)</location>""", RegexOption.IGNORE_CASE)
    val titleRegex = Regex("""<title>([^<]+)</title>""", RegexOption.IGNORE_CASE)
    val durRegex = Regex("""<duration>(\d+)</duration>""", RegexOption.IGNORE_CASE)

    return trackRegex.findAll(xspfText).mapNotNull { match ->
      val body = match.groupValues[1]
      val rawLoc = locRegex.find(body)?.groupValues?.getOrNull(1)?.trim() ?: return@mapNotNull null
      val resolved =
        if (base != null) runCatching { base.resolve(rawLoc).toString() }.getOrNull() else rawLoc
      if (resolved.isNullOrBlank() || !resolved.startsWith("http", ignoreCase = true)) return@mapNotNull null
      val title = titleRegex.find(body)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() } ?: parent.title
      val durSec = durRegex.find(body)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { it / 1000L }?.takeIf { it > 0L }
      val classified =
        MediaSniffer.classifyUrl(resolved, parent.sourcePageUrl, title)
          ?: DetectedMediaItem(
            url = resolved,
            title = title,
            kind = MediaKind.VIDEO,
            mimeType = "video/mp4",
            sourcePageUrl = parent.sourcePageUrl,
          )
      classified.copy(
        title = title,
        durationSeconds = durSec ?: classified.durationSeconds,
        parentPlaylistUrl = parent.url,
      )
    }.toList()
  }

  fun extractMediaLinksFromText(
    baseUrl: String,
    pageTitle: String,
    rawText: String,
  ): List<DetectedMediaItem> {
    val unescaped =
      rawText
        .replace("\\/", "/")
        .replace("\\u002F", "/")
        .replace("\\u002f", "/")
        .replace("\\u0026", "&")
        .replace("&amp;", "&")

    val base = runCatching { URI(baseUrl) }.getOrNull()
    val urlRegex =
      Regex(
        """((?:https?:)?//[^\s"'<>\\]+(?:\.(?:mp4|webm|mkv|mov|m4v|m3u8|mpd|m3u|pls|xspf|mp3|m4a)|videoplayback\?[^"'\s<>\\]+|googlevideo\.com\/videoplayback[^"'\s<>\\]*)(?:\?[^\s"'<>\\]*)?)""",
        RegexOption.IGNORE_CASE,
      )
    val attrOrJsonRegex =
      Regex(
        """(?:<(?:video|source)[^>]+?\bsrc\s*=\s*["']([^"']+)["']|"(?:contentUrl|video_url|mp4_url|streamUrl|file|src)"\s*:\s*"([^"]+)")""",
        RegexOption.IGNORE_CASE,
      )

    val found = LinkedHashMap<String, DetectedMediaItem>()

    fun recordCandidate(raw: String) {
      val normalized =
        when {
          raw.startsWith("http://", true) || raw.startsWith("https://", true) -> raw
          raw.startsWith("//") && base != null -> "${base.scheme ?: "https"}:$raw"
          base != null -> runCatching { base.resolve(raw).toString() }.getOrNull() ?: raw
          else -> raw
        }
      val item = MediaSniffer.classifyUrl(normalized, baseUrl, pageTitle)
      if (item != null && !found.containsKey(item.url)) {
        found[item.url] = item.copy(parentPlaylistUrl = baseUrl)
      }
    }

    urlRegex.findAll(unescaped).forEach { match ->
      recordCandidate(match.groupValues[1])
    }
    attrOrJsonRegex.findAll(unescaped).forEach { match ->
      val raw = match.groupValues[1].ifBlank { match.groupValues[2] }
      if (raw.isNotBlank()) recordCandidate(raw)
    }

    // Put direct .mp4 / VIDEO files first
    return found.values.sortedWith(
      compareBy<DetectedMediaItem> {
        when (it.kind) {
          MediaKind.VIDEO -> 0
          MediaKind.AUDIO -> 1
          MediaKind.STREAM_HLS -> 2
          MediaKind.STREAM_DASH -> 3
          MediaKind.PLAYLIST -> 4
        }
      }
    )
  }

  fun parseIso8601DurationSeconds(iso: String): Long? {
    val match =
      Regex("""PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+(?:\.\d+)?)S)?""", RegexOption.IGNORE_CASE).find(iso)
        ?: return null
    val hours = match.groupValues[1].toLongOrNull() ?: 0L
    val mins = match.groupValues[2].toLongOrNull() ?: 0L
    val secs = match.groupValues[3].toDoubleOrNull()?.roundToLong() ?: 0L
    val total = hours * 3600L + mins * 60L + secs
    return total.takeIf { it > 0L }
  }
}
