package com.example.ui.components

import androidx.core.net.toUri
import java.util.Locale

/** Formats a byte count into human-readable KB / MB / GB strings. */
fun formatBytes(bytes: Long): String {
  if (bytes <= 0L) return "0 KB"
  val kb = bytes / 1024.0
  if (kb < 1024.0) return String.format(Locale.US, "%.0f KB", kb)
  val mb = kb / 1024.0
  if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
  val gb = mb / 1024.0
  return String.format(Locale.US, "%.2f GB", gb)
}

/** Compact byte formatter used in status pills and navigation badges. */
fun formatCompactDataCounter(bytes: Long): String {
  val safe = bytes.coerceAtLeast(0L)
  if (safe < 1024L) return "${safe}B"
  val kb = safe / 1024.0
  if (kb < 1024.0) return String.format(Locale.US, "%.0fK", kb)
  val mb = kb / 1024.0
  if (mb < 100.0) return String.format(Locale.US, "%.1fM", mb)
  if (mb < 1024.0) return String.format(Locale.US, "%.0fM", mb)
  val gb = mb / 1024.0
  return String.format(Locale.US, "%.1fG", gb)
}

/** Formats milliseconds into `mm:ss` or `h:mm:ss` playback duration labels. */
fun formatDurationMs(ms: Long): String {
  val totalSeconds = (ms.coerceAtLeast(0L) / 1000L)
  val hours = totalSeconds / 3600L
  val minutes = (totalSeconds % 3600L) / 60L
  val seconds = totalSeconds % 60L
  return if (hours > 0) {
    String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
  } else {
    String.format(Locale.US, "%02d:%02d", minutes, seconds)
  }
}

/** Extracts the display host from [url], returning `"Speed Dial"` for blank URLs. */
fun extractHost(url: String): String {
  if (url.isBlank()) return "Speed Dial"
  return runCatching { url.toUri().host?.removePrefix("www.") ?: url }.getOrDefault(url)
}
