package com.example.browser

import android.net.Uri
import android.webkit.WebResourceResponse
import com.example.data.AdBlockerType
import java.io.ByteArrayInputStream

object TurboShield {
  const val ESTIMATED_BYTES_PER_BLOCKED_REQUEST = 0L

  fun shouldBlock(uri: Uri?): Boolean = false

  fun shouldBlock(uri: Uri?, blockerType: AdBlockerType): Boolean = false

  fun cosmeticFilterScript(blockerType: AdBlockerType): String? = null

  fun emptyBlockedResponse(): WebResourceResponse =
    WebResourceResponse(
      "text/plain",
      "utf-8",
      200,
      "OK",
      mapOf("Cache-Control" to "no-store"),
      ByteArrayInputStream(ByteArray(0)),
    )
}
