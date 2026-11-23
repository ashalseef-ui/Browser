package com.example.data

import java.util.UUID

enum class SearchEngine(
  val displayName: String,
  val searchUrlTemplate: String,
  val domain: String,
  val shortLabel: String,
  val badgeCode: String,
  val accentColorHex: Long,
  val bangs: List<String>,
) {
  DUCKDUCKGO(
    displayName = "DuckDuckGo",
    searchUrlTemplate = "https://duckduckgo.com/?q=%s",
    domain = "duckduckgo.com",
    shortLabel = "DuckDuckGo",
    badgeCode = "DDG",
    accentColorHex = 0xFFDE5833,
    bangs = listOf("!ddg", "!d"),
  ),
  GOOGLE(
    displayName = "Google",
    searchUrlTemplate = "https://www.google.com/search?q=%s",
    domain = "google.com",
    shortLabel = "Google",
    badgeCode = "G",
    accentColorHex = 0xFF4285F4,
    bangs = listOf("!g"),
  ),
  BRAVE(
    displayName = "Brave Search",
    searchUrlTemplate = "https://search.brave.com/search?q=%s",
    domain = "search.brave.com",
    shortLabel = "Brave",
    badgeCode = "BRV",
    accentColorHex = 0xFFFB542B,
    bangs = listOf("!b", "!br", "!brave"),
  ),
  MOJEEK(
    displayName = "Mojeek",
    searchUrlTemplate = "https://www.mojeek.com/search?q=%s",
    domain = "mojeek.com",
    shortLabel = "Mojeek",
    badgeCode = "MJK",
    accentColorHex = 0xFF4CAF50,
    bangs = listOf("!m", "!mj", "!mojeek"),
  ),
  MARGINALIA(
    displayName = "Marginalia Search",
    searchUrlTemplate = "https://search.marginalia.nu/search?query=%s",
    domain = "search.marginalia.nu",
    shortLabel = "Marginalia",
    badgeCode = "MRG",
    accentColorHex = 0xFFF59E0B,
    bangs = listOf("!mg", "!marg"),
  ),
  YANDEX(
    displayName = "Yandex",
    searchUrlTemplate = "https://yandex.com/search/?text=%s",
    domain = "yandex.com",
    shortLabel = "Yandex",
    badgeCode = "YDX",
    accentColorHex = 0xFFEF4444,
    bangs = listOf("!y", "!ya"),
  ),
  KAGI(
    displayName = "Kagi",
    searchUrlTemplate = "https://kagi.com/search?q=%s",
    domain = "kagi.com",
    shortLabel = "Kagi",
    badgeCode = "KGI",
    accentColorHex = 0xFFFFB300,
    bangs = listOf("!k", "!kagi"),
  ),
  QWANT(
    displayName = "Qwant",
    searchUrlTemplate = "https://www.qwant.com/?q=%s",
    domain = "qwant.com",
    shortLabel = "Qwant",
    badgeCode = "QWT",
    accentColorHex = 0xFF5C6BC0,
    bangs = listOf("!q", "!qw"),
  ),
  ECOSIA(
    displayName = "Ecosia",
    searchUrlTemplate = "https://www.ecosia.org/search?q=%s",
    domain = "ecosia.org",
    shortLabel = "Ecosia",
    badgeCode = "ECO",
    accentColorHex = 0xFF2E7D32,
    bangs = listOf("!e", "!eco"),
  ),
  SEARXNG(
    displayName = "SearXNG",
    searchUrlTemplate = "https://priv.au/search?q=%s",
    domain = "priv.au",
    shortLabel = "SearXNG",
    badgeCode = "SXG",
    accentColorHex = 0xFF06B6D4,
    bangs = listOf("!sx", "!searx"),
  ),
  YACY(
    displayName = "YaCy",
    searchUrlTemplate = "https://yacy.searchlab.eu/yacysearch.html?query=%s",
    domain = "yacy.searchlab.eu",
    shortLabel = "YaCy",
    badgeCode = "YCY",
    accentColorHex = 0xFF0EA5E9,
    bangs = listOf("!yc", "!yacy"),
  ),
  WIKIPEDIA(
    displayName = "Wikipedia",
    searchUrlTemplate = "https://en.wikipedia.org/wiki/Special:Search?search=%s",
    domain = "wikipedia.org",
    shortLabel = "Wikipedia",
    badgeCode = "WK",
    accentColorHex = 0xFFE2E8F0,
    bangs = listOf("!w", "!wiki"),
  );

  fun cycled(step: Int): SearchEngine {
    val all = entries
    val nextIdx = ((ordinal + step) % all.size + all.size) % all.size
    return all[nextIdx]
  }

  companion object {
    fun fromBang(token: String): SearchEngine? {
      val normalized = token.trim().lowercase().let {
        if (it.startsWith("/")) "!${it.drop(1)}" else it
      }
      return entries.firstOrNull { engine -> engine.bangs.any { it == normalized } }
    }
  }
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


