package com.example.media

import android.util.Base64
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

data class DecodedDataUrl(
  val bytes: ByteArray,
  val mimeType: String,
)

/**
 * Decodes RFC 2397 `data:` URLs (base64 and percent-encoded) with an upper memory guard.
 */
object DataUrlDecoder {
  const val MAX_DATA_URL_BYTES = 25 * 1024 * 1024
  private const val MAX_DATA_URL_CHARS = 36_000_000

  fun parse(dataUrl: String): DecodedDataUrl? {
    if (!dataUrl.startsWith("data:", ignoreCase = true)) return null
    if (dataUrl.length > MAX_DATA_URL_CHARS) return null
    val commaIdx = dataUrl.indexOf(',')
    if (commaIdx <= 4 || commaIdx >= dataUrl.length) return null
    val header = dataUrl.substring(5, commaIdx)
    val payload = dataUrl.substring(commaIdx + 1)
    val isBase64 = header.contains(";base64", ignoreCase = true)
    val rawMime = header.substringBefore(';').trim().lowercase(Locale.US)
    val mime = rawMime.ifBlank { "application/octet-stream" }
    val bytes =
      runCatching {
        if (isBase64) {
          Base64.decode(payload, Base64.DEFAULT)
        } else {
          URLDecoder.decode(payload, "UTF-8").toByteArray(StandardCharsets.UTF_8)
        }
      }.getOrNull() ?: return null
    if (bytes.isEmpty() || bytes.size > MAX_DATA_URL_BYTES) return null
    return DecodedDataUrl(bytes = bytes, mimeType = mime)
  }
}
