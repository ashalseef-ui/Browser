package com.example.data

import java.util.UUID

enum class SearchEngine(val displayName: String, val searchUrlTemplate: String, val domain: String) {
  DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=%s", "duckduckgo.com"),
  GOOGLE("Google", "https://www.google.com/search?q=%s", "google.com"),
  BRAVE("Brave Search", "https://search.brave.com/search?q=%s", "search.brave.com"),
  WIKIPEDIA("Wikipedia", "https://en.wikipedia.org/wiki/Special:Search?search=%s", "wikipedia.org"),
}

enum class MediaKind {
  VIDEO,
  AUDIO,
  STREAM_HLS,
  STREAM_DASH,
  PLAYLIST,
}

data class DetectedMediaItem(
  val url: String,
  val title: String,
  val kind: MediaKind,
  val mimeType: String,
  val sourcePageUrl: String,
  val posterUrl: String? = null,
  val durationSeconds: Long? = null,
  val contentSizeBytes: Long? = null,
  val isSizeEstimated: Boolean = false,
  val isResolvingMetadata: Boolean = false,
  val resolutionLabel: String? = null,
  val bitrateBps: Long? = null,
  val segmentCount: Int? = null,
  val parentPlaylistUrl: String? = null,
  val detectedAt: Long = System.currentTimeMillis(),
)

enum class DownloadStatus {
  QUEUED,
  DOWNLOADING,
  COMPLETED,
  FAILED,
  CANCELLED,
}

data class BrowserTabState(
  val id: String = UUID.randomUUID().toString(),
  val url: String = "",
  val title: String = "Speed Dial",
  val isIncognito: Boolean = false,
  val isLoading: Boolean = false,
  val progress: Int = 0,
  val canGoBack: Boolean = false,
  val canGoForward: Boolean = false,
  val trackersBlockedOnPage: Int = 0,
  val bytesSavedOnPage: Long = 0L,
  val lastLoadTimeMs: Long? = null,
  val isSecure: Boolean = true,
  val detectedMedia: List<DetectedMediaItem> = emptyList(),
  val isScanningMedia: Boolean = false,
  val isSpeedDial: Boolean = true,
)

data class BrowserSettings(
  val turboShieldEnabled: Boolean = true,
  val liteModeEnabled: Boolean = false,
  val darkWebEnabled: Boolean = true,
  val desktopModeEnabled: Boolean = false,
  val autoProxyOnLaunchEnabled: Boolean = false,
  val autoProxyFailoverEnabled: Boolean = true,
  val searchEngine: SearchEngine = SearchEngine.DUCKDUCKGO,
  val cumulativeTrackersBlocked: Long = 0L,
  val cumulativeBytesSaved: Long = 0L,
  val avgLoadTimeMs: Long = 0L,
  val measuredPageCount: Int = 0,
)
