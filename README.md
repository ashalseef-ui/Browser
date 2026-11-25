# Volt Browser

Volt Browser is a privacy-focused Android web browser built with Kotlin and Jetpack Compose (minSdk 24).

## Implemented Features
- **Multi-Tab WebView Pool**: Fast tab switching, isolated private/incognito sessions (`LOAD_NO_CACHE` and separate WebView profile cookies when supported), and edge swipe navigation.
- **Turbo Shield Ad & Tracker Blocker**: Multi-tier filtering (`Off`, `Standard`, `AdGuard`, `Strict Anti-Popup`, and `Stealth Mode` with DNT/GPC signals).
- **Native Media Sniffer & Player**: Detects direct MP4/WebM/HLS/DASH streams in WebView pages, supports Media3 playback and PiP, and downloads detected media or data/blob URLs.
- **Built-in WireGuard, Proxy & DoH VPN**:
  - WireGuard VPN via Cloudflare WARP (experimental registration endpoint) or imported `.conf` profiles, with IPv6 `::/0` routing and browser-level drop detection Kill Switch.
  - Public HTTP/SOCKS5 relay scanner and Private Auth proxy support, with plain-HTTP safety warnings over public relays.
  - Local Encrypted DNS-over-HTTPS (DoH) VPN service.
- **Lite Data Saver Mode**: Text-first browsing that skips remote images (returning a 1x1 transparent GIF placeholder) and webfonts.
- **Security & Site Controls**: AES-GCM credential encryption at rest via `AndroidKeyStore`, optional HTTPS-Only upgrade mode with fallback warnings, per-site Camera/Microphone/Location permission prompts, hardened `WebMessageListener` JS bridge, and Exit & Erase trace cleanup.

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
- Browsing history is not persisted across sessions; private tabs use `LOAD_NO_CACHE` and isolate session permissions.
- Imported WireGuard configs and WARP keys are encrypted with AES-256-GCM backed by AndroidKeyStore; if the keystore cannot encrypt or decrypt, plaintext is not saved silently.
- True system-wide Always-on VPN lockdown must be enabled in Android system VPN settings.

## License
[MIT License](LICENSE)
