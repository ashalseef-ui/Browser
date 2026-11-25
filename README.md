# Volt Browser

Volt Browser is an advanced, high-performance, privacy-first Android web browser built with Kotlin and Jetpack Compose (minSdk 24).

## Features
- **High-Performance WebView Tab Pool**: Fast switching, incognito isolation, and tab management.
- **Turbo Shield Ad & Tracker Blocker**: Multi-tier filtering with Standard, AdGuard, Strict, and Stealth Mode (DNT & GPC signals).
- **Native Media Sniffer & Transformer**: Auto-detection and downloading of MP4, WebM, HLS (`.m3u8`), DASH (`.mpd`), and YouTube stream extraction.
- **Built-in WireGuard & WARP VPN**: Encrypted tunneling with IPv6 leak protection and kill switch.
- **Lite Data Saver Mode**: Text-first boost and compressed image handling.
- **Secure Architecture**: AES-GCM credential encryption at rest via AndroidKeyStore, zero local trace cleanup, and HTTPS-only mode.

## Build Steps
1. Open the project in Android Studio or compile via command line:
   ```bash
   ./gradlew assembleDebug
   ```
2. Run unit tests:
   ```bash
   ./gradlew :app:testDebugUnitTest
   ```

## License
MIT License
