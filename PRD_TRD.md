# Volt Browser — Product Requirements Document (PRD) & Technical Requirements Document (TRD)

**Document Version:** 1.0  
**Last Updated:** 2026-11-26  
**Product Name:** Volt Browser  
**Application ID:** `com.aistudio.voltbrowser.xkqvmw` (Namespace: `com.example`)  
**Platform:** Android (Kotlin + Jetpack Compose, `minSdk 24`, `targetSdk 36`)  
**License:** MIT  

---

## Part 1: Product Requirements Document (PRD)

### 1.1 Product Vision & Executive Summary
**Volt Browser** is a privacy-first, high-speed Android web browser engineered for distraction-free browsing, native media extraction/playback, and built-in multi-layer network privacy—all wrapped in an architectural **Graphite Ember** (dark) and **Alabaster Paper** (light) interface.

Unlike bloated browsers or third-party wrapper tools, Volt Browser combines a low-latency Chromium WebView engine with a native **Media3 (ExoPlayer + Transformer)** studio, an integrated **WireGuard / Proxy / Encrypted DNS-over-HTTPS (DoH)** network layer, strict **at-rest cryptographic protections**, and one-tap **Exit & Erase** session sanitization.

> **Scope Note:** Legacy ad-blocker / Turbo Shield filtering engines have been intentionally removed from the product scope. Bandwidth savings, tracker minimization, and network hygiene are instead delivered via **Lite Data Saver**, **HTTPS-Only Mode**, **Encrypted DoH DNS** (including optional DNS-level filtering providers), **Private Tab isolation**, and **Exit & Erase**.

---

### 1.2 Target Personas & Core Use Cases
1. **Privacy-Conscious Mobile Users**
   - Browse in isolated Private Tabs (`LOAD_NO_CACHE`, separate WebView profiles/cookies).
   - Route traffic through Cloudflare WARP, custom WireGuard `.conf` tunnels, encrypted DoH DNS, or HTTP/SOCKS5 relays without leaving the browser.
   - Wipe all traces (tabs, caches, cookies, downloads/temp data, active tunnels) in one action via **Exit & Erase**.
2. **Media Collectors & Streamers**
   - Automatically detect playable or downloadable video, audio, HLS (`.m3u8`), and DASH (`.mpd`) streams while browsing.
   - Watch streams inside an integrated **Cinema HUD** with background audio playback, notification media controls, and **Picture-in-Picture (PiP)**, or export them to device storage.
3. **Bandwidth-Conscious & Power Users**
   - Enable **Lite Data Saver** to skip heavy images and webfonts without breaking page layouts.
   - Switch instantly among 12 privacy-oriented and mainstream search engines directly from the omnibox quick-bar or settings.
   - Use edge swipe gestures, horizontal tab-bar swiping, Desktop Mode, and a quick Social Hub sidebar.

---

### 1.3 Functional Product Requirements

#### FR-1: Multi-Tab Browsing & Workspace Deck (`Tab Deck`)
- Users can create Standard and Private (Incognito) tabs, switch tabs from **Tab Deck** (filtered by `All`, `Standard`, or `Private`), swipe tabs to dismiss, or swipe horizontally on the bottom Tab button with a live tab-peek banner.
- Long-pressing the Home button immediately opens a new Private Tab.
- Window popups (`window.open` / `target="_blank"`) triggered by user gestures open safely as new tabs rather than being silently dropped.

#### FR-2: Omnibox & Start Page (`Speed Dial`)
- **Start Page:** Editorial `VOLT` header with live Session Data counter, quick Private Tab and Exit & Erase actions, a bespoke basalt-and-ember hero card with inline search + Tunnel status chip, a 3-tile real telemetry row (`AVG LOAD`, `DATA SAVED`, `HTTPS GUARD`), and a 3-column squircle Pinned Workspace grid.
- **Floating Command Dock:** Rounded bottom dock housing the unified URL/search capsule (security indicator, search engine badge, URL input, Media Studio stream-count pill) and a 6-slot navigation row (`Back`, `Forward`, `Home`, `Tabs`, `Network & Tunnel`, `Menu`).
- **Quick Search Engine Strip:** Horizontal selector supporting 12 search engines (DuckDuckGo, Google, Brave Search, Mojeek, Marginalia Search, Yandex, Kagi, Qwant, Ecosia, SearXNG, YaCy, Wikipedia).
- **Adaptive Form Factors:** Uses a `NavigationRail` on tablet/expanded screens and supports edge swipe Back/Forward overlays and Find-in-Page.

#### FR-3: Media Studio (Sniffer, Player & Downloader)
- **Passive & DOM Sniffing:** Detects direct video/audio URLs (`mp4`, `webm`, `mkv`, `mp3`, `m4a`, etc.), HLS playlists (`.m3u8`), DASH manifests (`.mpd`), and classic playlists (`.m3u`, `.pls`, `.xspf`).
- **Playback:** Integrated Media3 ExoPlayer overlay with seek controls, fullscreen mode, background audio service (`VoltMediaService`), mini-player bar, and Android Picture-in-Picture.
- **Downloading:** Executes HTTP/S and adaptive downloads through a persistent `WorkManager` queue (`dataSync` foreground service) with up to 8 concurrent HTTP `Range` streams (`SegmentedDownloader` + `.segmeta` chunk checkpoints) for large range-capable files, single-stream `206 Partial Content` resume fallback, `data:` payload decoding, and Media3 `Transformer` `.mp4` remuxing for HLS/DASH streams.
- **Site Media Compatibility Bypass:** Per-site toggle in Privacy & Security controls for sites requiring relaxed compatibility handling.

#### FR-4: Network & Tunnel (`WireGuard`, `Web Proxy`, `Encrypted DNS`)
- **WireGuard VPN:** Supports experimental one-tap Cloudflare WARP registration as well as importing custom `.conf` profiles. Includes a browser-level **Kill Switch** ("Block traffic if VPN drops", off by default) that halts WebView network loads via a dead proxy override until recovery or user toggle, plus explicit UI guidance that system-level Always-on lockdown must be configured in Android Settings.
- **Web Proxy Relays:** Scans public HTTP/HTTPS/SOCKS5 relay lists (e.g., GeoNode, ProxyScrape, TheSpeedX) with DE/IN priority filtering, latency/reachability probing, auto-failover, and custom authenticated proxy entry. Displays a mandatory security notice on first public relay connection and warns before sending plain-HTTP main-frame navigations through public relays.
- **Encrypted DNS (DoH VPN):** Local Android `VpnService` intercepting DNS queries and resolving them over HTTPS across 9 selectable providers (Cloudflare, Cloudflare Malware Shield, Mullvad AdBlock, Mullvad Unfiltered, AdGuard DNS, Control D Anti-Tracker/Unfiltered, Quad9, DNS.SB).

#### FR-5: Security, Site Permissions & Data Controls
- **HTTPS-Only Mode:** Opt-in toggle (off by default) that upgrades `http://` main-frame requests to `https://` and presents an actionable fallback warning page ("Continue anyway") if HTTPS fails.
- **Per-Site Controls & Permissions:** Persists per-host JavaScript execution and Desktop Mode overrides in Room (`site_settings`), plus interactive prompts for Camera, Microphone, and Geolocation with *Allow once*, *Allow for this site*, and *Deny* options. Private Tabs additionally activate `WindowManager.LayoutParams.FLAG_SECURE` to prevent recents thumbnails or screen captures and never persist grants beyond the session.
- **Lite Data Saver:** Skips remote image and font fetches while returning a `200 OK` `1x1` transparent GIF for images so page layouts stay intact.
- **Exit & Erase:** Terminates all tabs, downloads, media playback, and tunnels; clears WebView caches, cookies, WebStorage, temporary files, and database traces.

#### FR-6: Social Sidebar & Library/Settings
- **Social Hub:** Edge-accessible sidebar showing quick web/app shortcuts for popular social platforms with installed-app detection and system autofill indicator.
- **Engine & Appearance:** Grouped settings sheet for Bookmarks, Search Engines, Theme selection (**Graphite Ember** dark / **Alabaster Paper** light), Algorithmic Darkening, Desktop Mode, and Data Saver.

---

### 1.4 Non-Functional Requirements
- **Minimum OS Compatibility:** Android 7.0 (API 24) through Android 16 (API 36).
- **Performance:** Chromium initialization starts asynchronously at `Application.onCreate()`; OkHttp connection pooling prewarms DNS/TLS sockets on direct connections; WebView tab pool uses LRU eviction (`MAX_LIVE_WEBVIEWS`) to bound memory.
- **Cryptographic Fail-Closed Guarantees:** Sensitive WireGuard keys/configs must never fall back to silent plaintext writes or silent empty-string decryption errors.
- **Play Policy & Privacy Compliance:** No broad external storage permissions (`READ_EXTERNAL_STORAGE`/`READ_MEDIA_*`), no user-added MitM CA trust in release network config, minimal least-privilege runtime permissions (`CAMERA`, `RECORD_AUDIO`, `ACCESS_FINE/COARSE_LOCATION`, `POST_NOTIFICATIONS`), and `allowBackup="false"`.

---

## Part 2: Technical Requirements Document (TRD)

### 2.1 System Architecture & Package Structure
Volt Browser follows a modular **MVVM** architecture with unidirectional `StateFlow` state management (`BrowserViewModel`) and clean separation across five packages under `com.example`:

| Package | Key Components | Responsibility |
| :--- | :--- | :--- |
| `com.example` | `VoltBrowserApp`, `MainActivity` | Application bootstrap, async Chromium warmup, edge-to-edge Activity host, `FLAG_SECURE` Private-tab protection, PiP & intent handling |
| `com.example.browser` | `VoltSpeedEngine`, `WebViewTabPool`, `WebContextTargetResolver`, `SocialHubHelper`, `VoltDataSaver`, `MediaSniffer`, `MediaSnifferCoordinator`, `PlaylistResolver`, `DeviceTraceCleaner` | WebView lifecycle & LRU pooling, UA/cache/dark-web/per-site config, Data Saver interception, JS bridge & media sniffing, social autofill helpers, trace erasure |
| `com.example.data` | `AppDatabase`, `BrowserDaos`, `RoomEntities`, `BrowserRepository`, `VoltPreferences`, `SecureConfigCipher`, `BrowserModels` | Room SQLite persistence (`v3`), Jetpack DataStore preferences, AES-256-GCM Keystore encryption, domain models |
| `com.example.media` | `MediaPlaybackManager`, `VoltMediaService`, `MediaDownloader`, `MediaDownloadWorker`, `SegmentedDownloader`, `AdaptiveStreamExporter`, `DataUrlDecoder`, `MediaExportHelper` | Media3 ExoPlayer playback, `MediaSessionService` foreground service, WorkManager download queue, 8-stream segmented HTTP Range downloader, Media3 `Transformer` export, MediaStore/SAF export |
| `com.example.vpn` | `VpnProxyCoordinator`, `WireGuardManager`, `PublicRelayManager`, `VoltVpnService`, `VpnModels` | WireGuard `GoBackend` orchestration, Kill Switch enforcement, proxy relay discovery/probing, IPv4 DNS-to-DoH TUN codec |
| `com.example.ui` | `BrowserViewModel`, `BrowserUiState`, `VoltBrowserScreen`, `BrowserNavigationRail`, `BrowserOverlays`, `ui/theme/*`, `ui/components/*`, `ui/screens/*` | Modular Jetpack Compose Material 3 UI, state orchestration, adaptive layouts, gesture & sheet overlays |

---

### 2.2 Technology Stack & Dependencies
Configured via `gradle/libs.versions.toml` and `app/build.gradle.kts`:
- **Build & Language:** AGP `9.1.1`, Kotlin `2.4.21`, KSP `2.3.5`, Java 17 bytecode with core library desugaring (`desugar_jdk_libs 2.1.5`).
- **UI & Graphics:** Jetpack Compose BOM `2024.09.00`, Material 3, Material Icons Extended, Coil Compose `2.7.0`, local bundled fonts (`outfit.ttf`, `manrope.ttf`, `jetbrains_mono.ttf`).
- **Web, Queueing & Concurrency:** `androidx.webkit:webkit 1.15.0`, `androidx.work:work-runtime-ktx 2.10.0`, Kotlin Coroutines `1.10.2`, `OkHttp 4.10.0`.
- **Persistence & Security:** Room `2.7.0` (`runtime`, `ktx`, `compiler` via KSP), DataStore Preferences `1.1.7`, `AndroidKeyStore` AES-GCM (`javax.crypto`).
- **Media:** `androidx.media3` `1.6.1` (`exoplayer`, `exoplayer-hls`, `exoplayer-dash`, `ui`, `session`, `common`, `datasource-okhttp`, `transformer`).
- **VPN Tunneling:** `com.wireguard.android:tunnel 1.0.20260102` (`GoBackend`), Android `VpnService`.
- **Testing:** JUnit `4.13.2`, Robolectric `4.16.1`, Coroutines Test `1.10.2`, AndroidX Test Core/Compose UI Test.

---

### 2.3 Detailed Subsystem Specifications

#### 2.3.1 Browser Engine & WebView Pool (`browser/`)
- **Startup & Network Warmup (`VoltSpeedEngine`):**
  - Calls `WebViewCompat.startUpWebView(...)` off the main thread in `VoltBrowserApp.onCreate()`.
  - Maintains a shared `OkHttpClient` backed by a `ProxySelector` bound to `activeProxyRef` and `activeProxyAuthRef` (excluding `127.0.0.1`/`localhost`) and a 16-connection HTTP/2 + HTTP/1.1 pool.
  - Prewarms non-proxied target origins via `InetAddress.getByName` and `HEAD https://<host>/` (bounded to `256` hosts).
- **WebView Settings (`configureWebViewSettings`):**
  - Accepts `isIncognito: Boolean`: sets `WebSettings.LOAD_NO_CACHE` for private tabs and `WebSettings.LOAD_DEFAULT` for standard tabs.
  - Dynamically extracts the system Chromium version from `WebSettings.getDefaultUserAgent(context)` via `Regex("Chrome/([\\d\\.]+)")` and injects it into the Linux x86_64 Desktop User-Agent string when Desktop Mode is enabled.
  - Configures `safeBrowsingEnabled = true`, `allowFileAccess = false`, `javaScriptCanOpenWindowsAutomatically = false`, `setSupportMultipleWindows(true)`, and `WebSettingsCompat.setAlgorithmicDarkeningAllowed`.
- **Tab Pooling & Isolation (`WebViewTabPool`):**
  - Maintains an access-ordered `LinkedHashMap<String, WebView>` capped at `MAX_LIVE_WEBVIEWS`.
  - For Private tabs, isolates cookies via `WebViewCompat.setProfile(webView, PRIVATE_PROFILE_NAME)` and `ProfileStore` when `WebViewFeature.MULTI_PROFILE` is supported, disables third-party cookies, and sets `IMPORTANT_FOR_AUTOFILL_NO` (API 26+).
  - Handles `onCreateWindow` popups by resolving `resultMsg.obj as WebView.WebViewTransport`, honoring `request?.hasGesture()`, instantiating the new tab's `WebView`, and calling `resultMsg.sendToTarget()`.
- **JS Bridge Hardening (`WebViewTabPool` & `MediaSniffer`):**
  - Prefers `WebViewCompat.addWebMessageListener(webView, "VoltMediaChannel", setOf("*"), ...)` gated by `WebViewFeature.WEB_MESSAGE_LISTENER`, falling back to `addJavascriptInterface(jsBridge, "VoltMediaBridge")` only on older WebView implementations.
  - Caps incoming JSON message payload length and item count inside `VoltMediaJsBridge` to mitigate DOM/bridge flooding.
- **Lite Data Saver (`VoltDataSaver`):**
  - Evaluates both URL extension (`png`, `jpg`, `jpeg`, `webp`, `gif`, `avif`; `woff`, `woff2`, `ttf`, `otf`, `eot`) and HTTP `Accept` headers (`image/`, `font/`).
  - Returns an HTTP `200 OK` `image/gif` `1x1` transparent `ByteArrayInputStream` (`TRANSPARENT_GIF_BYTES`) for images and an empty `404` `text/plain` response for fonts.

#### 2.3.2 Security, Encryption & Site Controls
- **At-Rest Config Encryption (`SecureConfigCipher`):**
  - Uses `AndroidKeyStore` (`volt_wg_config_aes_v1`) with 256-bit `AES/GCM/NoPadding`, 12-byte IV, 128-bit authentication tag, `android.util.Base64.NO_WRAP`, and version prefix `enc:v1:`.
  - `encrypt()` throws `IllegalStateException` if the Keystore is unavailable—callers (`saveWarpConfig`, `saveCustomWireGuardConfig`) catch the error, abort saving plaintext, and surface an error message to the UI.
  - `decrypt()` supports legacy plaintext passthrough (strings without `enc:v1:`) and returns `null` if decryption fails, triggering `warpConfigDecryptFailed` / `customWgConfigDecryptFailed` warnings prompting user re-import.
- **Navigation Guards (`resolveOutgoingNavigation`):**
  - Evaluates in priority order:
    1. **Kill Switch:** Returns `BlockedByKillSwitch` if `isKillSwitchBlocking` is active.
    2. **HTTPS-Only Mode:** If enabled and host is not in `httpsOnlyBypassedHosts`, upgrades `http://` main-frame requests to `https://`; on SSL/network failure, surfaces `HttpsWarningState` with a "Continue anyway" bypass.
    3. **Public Relay Plain-HTTP Guard:** If routing over an unauthenticated public relay and host is not bypassed, emits `WarnPublicProxyHttp` before transmitting cleartext HTTP.
- **Network Security Config (`res/xml/network_security_config.xml`):**
  - Allows general browser HTTP/HTTPS using **system CAs only** (`<certificates src="system" />`).
  - Explicitly forbids cleartext (`cleartextTrafficPermitted="false"`) for all internal DoH, Cloudflare WARP, IP-check (`api.ipify.org`), and proxy directory endpoints.
- **Per-Site Permissions:**
  - Handles `WebChromeClient.onPermissionRequest` (Camera, Microphone, Protected Media) and `onGeolocationPermissionsShowPrompt`.
  - Standard tabs check Room (`SitePermissionEntity`); Private tabs consult only ephemeral `sessionSitePermissions` and never persist grants.

#### 2.3.3 Persistence Schema (`data/`)
- **Room Database (`volt_browser.db`, Version 3, `MIGRATION_1_2` & `MIGRATION_2_3`):**
  - `bookmarks`: `id` (PK), `url`, `title`, `category`, `isSpeedDial`, `accentHex`, `createdAt`.
  - `history_entries`: `id` (PK), `url`, `title`, `visitedAt`, `loadTimeMs`, `trackersBlocked`.
  - `downloads`: `id` (PK), `url`, `fileName`, `localPath`, `mimeType`, `downloadedBytes`, `totalBytes`, `status`, `createdAt`.
  - `site_permissions`: composite PK `(host, permissionType)`, `granted`, `updatedAt`.
  - `site_settings`: `host` (PK), `jsEnabled`, `desktopMode`, `updatedAt`.
- **DataStore (`VoltPreferences`):**
  - Persists boolean feature flags, theme mode, search engine, DoH provider, proxy failover/launch flags, encrypted WireGuard configs, decrypt-failure flags, and cumulative session telemetry.

#### 2.3.4 Media Playback & Download Pipeline (`media/`)
- **Playback:** `MediaPlaybackManager` binds `ExoPlayer` configured with `OkHttpDataSource.Factory` (injecting per-stream `Referer`, `Origin`, and `User-Agent` from `MediaSniffer.registerPlaybackContext`), attaches to `VoltMediaService` (`foregroundServiceType="mediaPlayback"`), and updates Android 12+ `PictureInPictureParams.setAutoEnterEnabled` in `MainActivity`.
- **Downloading (`MediaDownloader`, `MediaDownloadWorker`, `SegmentedDownloader`, `AdaptiveStreamExporter`):**
  - HTTP/S downloads enqueue into `WorkManager` (`MediaDownloadWorker`, `NetworkType.CONNECTED`, `FOREGROUND_SERVICE_TYPE_DATA_SYNC`) for background survival and automatic backoff retries across interruptions.
  - Range-capable files (`Accept-Ranges: bytes` / `206` probe, `>= 1 MB`) split across up to **8 parallel HTTP `Range` connections** writing disjoint offsets into `RandomAccessFile` with sibling `.segmeta` JSON checkpoints so interrupted chunks resume precisely without re-fetching completed segments; non-range servers fall back cleanly to single-stream `Range: bytes=<offset>-` transfers and export via `MediaExportHelper` (`MediaStore` / SAF / `FileProvider`).
  - `data:` URLs are base64/URL-decoded locally via `DataUrlDecoder`.
  - Adaptive HLS/DASH streams are transformed/remuxed to `.mp4` locally using `AdaptiveStreamExporter` (Media3 `Transformer` + `ExoPlayerAssetLoader`) with live progress tracking.

#### 2.3.5 VPN, WireGuard, Proxy & DoH (`vpn/`)
- **WireGuard (`WireGuardManager`):**
  - Wraps `com.wireguard.android.backend.GoBackend`.
  - `buildWarpConfigString()` enforces `AllowedIPs = 0.0.0.0/0, ::/0` to prevent IPv6 tunnel bypass leaks.
  - Exposes `tunnelUpFlow` observed by `VpnProxyCoordinator`; unexpected drops while `wireGuardKillSwitchEnabled == true` engage WebView load stopping and a dead local proxy override via `androidx.webkit.ProxyController`, updating `isKillSwitchTripped`.
- **Encrypted DNS VPN (`VoltVpnService`):**
  - Implements `VpnService` (`foregroundServiceType="specialUse"`), reads raw IPv4 packets from `tunInterface`, parses UDP port 53 queries (`DnsPacketCodec.parseIpv4UdpDnsQuery`), executes `application/dns-message` POST requests to the selected `DohProvider` over a `protect()`-ed `OkHttpClient`, caches responses in a volatile 256-entry in-memory LRU cache (TTL clamped 5–3600s, wiped on stop/destroy), and writes swapped-header IPv4/UDP responses back to the TUN descriptor.
- **Public & Private Proxy Relays (`PublicRelayManager`):**
  - Aggregates and probes HTTP/SOCKS5 candidates concurrently, supports authenticated private proxies (`username`/`password`), and coordinates `ProxyController` + `VoltSpeedEngine.setActiveProxy(...)`.

#### 2.3.6 Design System & UI/UX Architecture (`ui/`)
- **Color Palettes (`ui/theme/Color.kt`, `Theme.kt`):**
  - **Graphite Ember (Dark):** `#0D0D10` obsidian canvas, `#1D1D23` slate surfaces, `#FF6347` Ember Coral primary/brand accent, `#34D399` Ember Mint active/tunnel accent, `#FBBF24` Ember Amber, `#A78BFA` Ember Iris.
  - **Alabaster Paper (Light):** `#F6F4EF` ceramic paper canvas, `#FFFFFF` elevated cards, `#DC3D1E` Alabaster Coral accent.
- **Typography (`ui/theme/Type.kt`):**
  - `OutfitFontFamily` for display & headline hierarchies.
  - `ManropeFontFamily` for titles & readable body copy.
  - `JetBrainsMonoFontFamily` for technical telemetry, URLs, badges, and micro-labels.
- **Visual Assets (`res/drawable/`):**
  - Custom generated assets: `img_speed_dial_hero.jpg` (Start Page basalt-and-coral prism hero) and `img_media_vault_empty.jpg` (Media Studio empty-state illustration).

---

### 2.4 Build, Release, CI & Test Verification
- **Release Optimization & ProGuard (`app/build.gradle.kts`, `app/proguard-rules.pro`):**
  - `release` build enables `isMinifyEnabled = true` with keep rules preserving `@JavascriptInterface` (`VoltMediaJsBridge`), Room (`* extends androidx.room.RoomDatabase`), WireGuard Go/JNI bindings (`com.wireguard.**`), and Media3 (`androidx.media3.**`).
  - Signing configs (`release` and `debugConfig`) are preserved for AI Studio APK/AAB pipelines.
- **Continuous Integration (`.github/workflows/android.yml`):**
  - Runs on `push` and `pull_request` to `main`/`master` using Temurin JDK 17 (`gradle/actions/setup-gradle@v4`), executing `./gradlew :app:testDebugUnitTest :app:lintDebug`.
- **Automated Test Suite (`app/src/test/java/com/example/`):**
  - Covers `MediaSniffer` stream classification, `VoltSpeedEngine` URL/search normalization and UA substitution, `DnsPacketCodec` IPv4/UDP DNS parsing and response construction, `PublicRelayManager` feed parsing, `VoltDataSaver` image/font detection and 1x1 GIF responses, `SecureConfigCipher` AES-GCM encryption/decryption roundtrips and failure semantics, `WireGuardManager` IPv4+IPv6 `AllowedIPs` validation, and `Formatters`.
