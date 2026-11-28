package com.example.ui

import com.example.data.SitePermissionType

/** Active modal sheet or dialog destination rendered above the browser canvas. */
enum class BrowserSheet {
  NONE,
  TAB_SWITCHER,
  MEDIA_HUB,
  SECURITY_TELEMETRY,
  LIBRARY_SETTINGS,
  ADD_SPEED_DIAL,
  VPN,
  EXIT_CONFIRM,
}

/** Pending site permission dialog model (Camera, Microphone, or Location). */
data class SitePermissionPromptState(
  val tabId: String,
  val origin: String,
  val host: String,
  val isIncognito: Boolean,
  val requestedTypes: Set<SitePermissionType>,
  val onDecision: (granted: Boolean, rememberForSite: Boolean) -> Unit,
)

/** Pending Android runtime permission batch requested prior to honoring a WebView permission prompt. */
data class RuntimePermissionRequestState(
  val permissions: List<String>,
  val onResult: (Boolean) -> Unit,
)
