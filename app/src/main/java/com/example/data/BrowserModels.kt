package com.example.data

import java.util.UUID

enum class SearchEngine(val displayName: String, val searchUrlTemplate: String, val domain: String) {
  DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=%s", "duckduckgo.com"),
  GOOGLE("Google", "https://www.google.com/search?q=%s", "google.com"),
  BRAVE("Brave Search", "https://search.brave.com/search?q=%s", "search.brave.com"),
  MOJEEK("Mojeek", "https://www.mojeek.com/search?q=%s", "mojeek.com"),
  MARGINALIA("Marginalia Search", "https://search.marginalia.nu/search?query=%s", "search.marginalia.nu"),
  YANDEX("Yandex", "https://yandex.com/search/?text=%s", "yandex.com"),
  KAGI("Kagi", "https://kagi.com/search?q=%s", "kagi.com"),
  QWANT("Qwant", "https://www.qwant.com/?q=%s", "qwant.com"),
  ECOSIA("Ecosia", "https://www.ecosia.org/search?q=%s", "ecosia.org"),
  SEARXNG("SearXNG", "https://priv.au/search?q=%s", "priv.au"),
  YACY("YaCy", "https://yacy.searchlab.eu/yacysearch.html?query=%s", "yacy.searchlab.eu"),
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

enum class AppThemeMode(val displayName: String) {
  CYBER_OBSIDIAN("Cyber Obsidian"),
  ZEN_LIGHT("Zen Light"),
}

enum class AdBlockerType(
  val displayName: String,
  val badgeLabel: String,
  val subtitle: String,
) {
  OFF("Disabled", "OFF", "No ad, tracker, or cosmetic filtering"),
  STANDARD("Standard Shield", "STD", "Fast core ad & tracker domain blocklist"),
  ADGUARD("AdGuard Filter", "ADG", "AdGuard network rules, telemetry & cosmetic ad hiding"),
  STRICT("Strict Anti-Popup", "MAX", "AdGuard + popup traps, cookie nags & anti-adblock overlays"),
}

data class BrowserSettings(
  val turboShieldEnabled: Boolean = true,
  val adBlockerType: AdBlockerType = AdBlockerType.ADGUARD,
  val liteModeEnabled: Boolean = false,
  val darkWebEnabled: Boolean = true,
  val desktopModeEnabled: Boolean = false,
  val autoWireGuardOnLaunchEnabled: Boolean = false,
  val preferredWireGuardMode: com.example.vpn.WireGuardMode = com.example.vpn.WireGuardMode.WARP_FREE,
  val savedWarpConf: String = "",
  val savedCustomWgConf: String = "",
  val savedCustomWgName: String = "",
  val autoProxyOnLaunchEnabled: Boolean = false,
  val autoProxyFailoverEnabled: Boolean = true,
  val autoDnsVpnOnLaunchEnabled: Boolean = false,
  val preferredDohProvider: com.example.vpn.DohProvider = com.example.vpn.DohProvider.CLOUDFLARE,
  val searchEngine: SearchEngine = SearchEngine.DUCKDUCKGO,
  val themeMode: AppThemeMode = AppThemeMode.CYBER_OBSIDIAN,
  val cumulativeTrackersBlocked: Long = 0L,
  val cumulativeBytesSaved: Long = 0L,
  val avgLoadTimeMs: Long = 0L,
  val measuredPageCount: Int = 0,
)

data class SocialAppShortcut(
  val id: String,
  val name: String,
  val packageName: String,
  val webUrl: String,
  val loginUrl: String,
  val isInstalled: Boolean = false,
)

data class WebContextMenuTarget(
  val pageUrl: String = "",
  val linkUrl: String? = null,
  val linkText: String? = null,
  val imageUrl: String? = null,
  val mediaUrl: String? = null,
  val mediaKind: MediaKind? = null,
  val mediaTitle: String? = null,
  val bookmarkId: Long? = null,
) {
  val hasAnyTarget: Boolean
    get() = !linkUrl.isNullOrBlank() || !imageUrl.isNullOrBlank() || !mediaUrl.isNullOrBlank()
}


