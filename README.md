# Volt Browser

Volt Browser is a privacy-focused Android web browser built with Kotlin and Jetpack Compose (`minSdk 24`).

## Implemented Features
- **Multi-Tab WebView Pool & Private Tab Protection**: Fast tab switching, isolated private/incognito sessions (`LOAD_NO_CACHE`, separate WebView profile cookies when supported, and dynamic `FLAG_SECURE` screenshot/recents blocking), plus edge swipe navigation.
- **Media Studio (Sniffer, Media3 Player & Multi-Stream WorkManager Downloader)**: Detects direct MP4/WebM/HLS/DASH streams in WebView pages, supports Media3 playback with Picture-in-Picture, accelerates large direct downloads across up to 8 parallel HTTP `Range` segments via a persistent `WorkManager` queue (with per-chunk `.segmeta` resume), decodes `data:` URLs, and remuxes adaptive streams locally.
- **Built-in WireGuard, Proxy & DoH VPN**:
  - WireGuard VPN via Cloudflare WARP (experimental registration endpoint) or imported `.conf` profiles, with IPv6 `::/0` routing and browser-level drop detection Kill Switch.
  - Public HTTP/SOCKS5 relay scanner (DE & IN prioritized) and Private Auth proxy support, with plain-HTTP safety warnings over public relays.
  - Local Encrypted DNS-over-HTTPS (DoH) VPN service with 9 selectable providers.
- **Lite Data Saver Mode**: Text-first browsing that skips remote images (returning a `200 OK` 1x1 transparent GIF placeholder) and webfonts.
- **Security & Per-Site Controls**: AES-256-GCM credential encryption at rest via `AndroidKeyStore`, optional HTTPS-Only upgrade mode with fallback warnings, per-site JavaScript / Desktop Mode / Media Bypass overrides, per-site Camera/Microphone/Location permission prompts, hardened `WebMessageListener` JS bridge, and one-tap **Exit & Erase** trace cleanup.
- **Graphite Ember & Alabaster Paper UI**: Dual dark/light Material 3 themes with Start Page Speed Dial, floating bottom Omnibox Dock, adaptive tablet NavigationRail, and Social Hub sidebar.

## Architecture & Documentation
See [PRD_TRD.md](PRD_TRD.md) for the complete Product & Technical Requirements Specification.

## Build Steps
1. Open the project in Android Studio or build via command line:
   ```bash
   ./gradlew assembleDebug
   ```
2. Run unit tests & lint:
   ```bash
   ./gradlew :app:testDebugUnitTest :app:lintDebug
   ```

## Privacy Notes
- Browsing history is not persisted across sessions; private tabs use `LOAD_NO_CACHE`, protect recents thumbnails via `FLAG_SECURE`, and isolate session permissions.
- Imported WireGuard configs and WARP keys are encrypted with AES-256-GCM backed by AndroidKeyStore; if the keystore cannot encrypt or decrypt, plaintext is not saved silently.
- True system-wide Always-on VPN lockdown must be enabled in Android system VPN settings.

## License
[MIT License](LICENSE)
