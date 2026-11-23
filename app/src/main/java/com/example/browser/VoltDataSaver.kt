package com.example.browser

import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.util.Locale

object VoltDataSaver {

  private val imageExtensions = setOf("png", "jpg", "jpeg", "webp", "gif", "avif")
  private val fontExtensions = setOf("woff", "woff2", "ttf", "otf", "eot")

  const val ESTIMATED_IMAGE_BYTES = 125_000L // ~125 KB per image
  const val ESTIMATED_FONT_BYTES = 55_000L   // ~55 KB per webfont

  fun shouldBlockForDataSaver(uri: Uri?): Pair<Boolean, Long> {
    uri ?: return false to 0L
    val cleanPath = uri.toString().substringBefore('?').substringBefore('#').lowercase(Locale.US)
    val ext = cleanPath.substringAfterLast('.', "")

    if (ext in imageExtensions) {
      return true to ESTIMATED_IMAGE_BYTES
    }
    if (ext in fontExtensions) {
      return true to ESTIMATED_FONT_BYTES
    }
    return false to 0L
  }

  fun emptySaverResponse(): WebResourceResponse =
    WebResourceResponse(
      "text/plain",
      "utf-8",
      404,
      "Skipped by Volt Data Saver",
      mapOf("Cache-Control" to "no-store"),
      ByteArrayInputStream(ByteArray(0)),
    )
}
