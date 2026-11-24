package com.example.browser

import android.net.Uri
import com.example.data.BrowserTabState
import com.example.data.DetectedMediaItem
import com.example.data.MediaKind
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class MediaSnifferCoordinator(
  private val speedEngine: VoltSpeedEngine,
  private val scope: CoroutineScope,
  private val tabProvider: (String) -> BrowserTabState?,
  private val updateTab: (String, (BrowserTabState) -> BrowserTabState) -> Unit,
  private val onShowMessage: (String) -> Unit,
) {
  private val activeMediaScans = ConcurrentHashMap<String, AtomicInteger>()
  private val rejectedMediaUrlsByTab = ConcurrentHashMap<String, MutableSet<String>>()

  fun resetTab(tabId: String) {
    rejectedMediaUrlsByTab.remove(tabId)
  }

  fun clearAll() {
    rejectedMediaUrlsByTab.clear()
    activeMediaScans.clear()
  }

  fun mergeAndProbeMediaItems(
    tabId: String,
    items: List<DetectedMediaItem>,
    forceReprobe: Boolean = false,
    isManualResolve: Boolean = false,
  ) {
    val currentTab = tabProvider(tabId)
    val existingMap = currentTab?.detectedMedia?.associateBy { it.url }.orEmpty()
    val rejectedSet =
      rejectedMediaUrlsByTab.computeIfAbsent(tabId) { Collections.synchronizedSet(HashSet()) }

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
        rejectedSet.add(candidate.url)
        beginMediaScan(tabId)
        scope.launch {
          try {
            val result = PlaylistResolver.resolve(speedEngine.okHttpClient, candidate)

            val directVideos = result.resolvedItems.filter { it.kind == MediaKind.VIDEO }.toMutableList()
            val hlsVideos =
              result.resolvedItems.filter {
                it.kind == MediaKind.STREAM_HLS &&
                  (it.segmentCount ?: 0) > 0
              }.toMutableList()

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
                    (it.segmentCount ?: 0) > 0
                }
              )
              if (nestedRes.isParentDownloadableVideo) {
                hlsVideos.add(nestedRes.updatedParent)
              }
            }

            val verifiedToAdd = mutableListOf<DetectedMediaItem>()
            if (result.isParentDownloadableVideo) {
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
                onShowMessage("Found ${verifiedToAdd.distinctBy { it.url }.size} downloadable video(s)")
              }
            } else {
              if (forceReprobe) {
                removeMediaItem(tabId, candidate.url)
              }
              if (isManualResolve) {
                onShowMessage("No downloadable video > 1 MB found at that URL")
              }
            }
          } finally {
            endMediaScan(tabId)
          }
        }
      } else {
        beginMediaScan(tabId)
        scope.launch {
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
                onShowMessage("Verified downloadable video (> 1 MB)")
              }
            } else {
              rejectedSet.add(candidate.url)
              probe.resolvedUrl?.let { rejectedSet.add(it) }
              if (forceReprobe) {
                removeMediaItem(tabId, candidate.url)
              }
              if (isManualResolve) {
                onShowMessage("Video unavailable or smaller than 1 MB")
              }
            }
          } finally {
            endMediaScan(tabId)
          }
        }
      }
    }
  }

  private fun buildVerifiedVideoItem(
    baseItem: DetectedMediaItem,
    probe: MediaSniffer.VideoProbeResult,
  ): DetectedMediaItem? {
    if (!probe.isDownloadable) return null
    val verifiedSize = probe.contentSizeBytes ?: baseItem.contentSizeBytes ?: 0L

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
              !item.url.contains("#dash_")
          }
          .sortedWith(
            compareBy<DetectedMediaItem> { if (it.kind == MediaKind.VIDEO) 0 else 1 }
              .thenByDescending { it.contentSizeBytes ?: it.bitrateBps ?: 0L }
          )
      tab.copy(detectedMedia = sorted)
    }
  }

  private fun removeMediaItem(tabId: String, url: String) {
    updateTab(tabId) { tab ->
      tab.copy(detectedMedia = tab.detectedMedia.filterNot { it.url == url })
    }
  }
}
