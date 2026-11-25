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

  private val TRANSPARENT_GIF_BYTES = byteArrayOf(
    0x47.toByte(), 0x49.toByte(), 0x46.toByte(), 0x38.toByte(), 0x39.toByte(), 0x61.toByte(), 0x01.toByte(), 0x00.toByte(), 0x01.toByte(), 0x00.toByte(),
    0x80.toByte(), 0x00.toByte(), 0x00.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x21.toByte(),
    0xf9.toByte(), 0x04.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x2c.toByte(), 0x00.toByte(), 0x00.toByte(),
    0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x00.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x02.toByte(), 0x02.toByte(), 0x44.toByte(),
    0x01.toByte(), 0x00.toByte(), 0x3b.toByte()
  )

  fun shouldBlockForDataSaver(uri: Uri?, headers: Map<String, String> = emptyMap()): Pair<Boolean, Long> {
    uri ?: return false to 0L
    val cleanPath = uri.toString().substringBefore('?').substringBefore('#').lowercase(Locale.US)
    val ext = cleanPath.substringAfterLast('.', "")
    val acceptHeader = headers.entries.firstOrNull { it.key.equals("Accept", true) }?.value.orEmpty().lowercase(Locale.US)

    val isImage = ext in imageExtensions || acceptHeader.contains("image/")
    val isFont = ext in fontExtensions || acceptHeader.contains("font/")

    if (isImage) {
      return true to ESTIMATED_IMAGE_BYTES
    }
    if (isFont) {
      return true to ESTIMATED_FONT_BYTES
    }
    return false to 0L
  }

  fun emptySaverResponse(isImage: Boolean = false): WebResourceResponse =
    if (isImage) {
      WebResourceResponse(
        "image/gif",
        "utf-8",
        200,
        "OK",
        mapOf("Cache-Control" to "no-store"),
        ByteArrayInputStream(TRANSPARENT_GIF_BYTES),
      )
    } else {
      WebResourceResponse(
        "text/plain",
        "utf-8",
        404,
        "Skipped by Volt Data Saver",
        mapOf("Cache-Control" to "no-store"),
        ByteArrayInputStream(ByteArray(0)),
      )
    }
}
