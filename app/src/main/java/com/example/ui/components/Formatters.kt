package com.example.ui.components

import android.net.Uri
import java.util.Locale

fun formatBytes(bytes: Long): String {
  if (bytes <= 0L) return "0 KB"
  val kb = bytes / 1024.0
  if (kb < 1024.0) return String.format(Locale.US, "%.0f KB", kb)
  val mb = kb / 1024.0
  if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
  val gb = mb / 1024.0
  return String.format(Locale.US, "%.2f GB", gb)
}

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

fun extractHost(url: String): String {
  if (url.isBlank()) return "Speed Dial"
  return runCatching { Uri.parse(url).host?.removePrefix("www.") ?: url }.getOrDefault(url)
}
