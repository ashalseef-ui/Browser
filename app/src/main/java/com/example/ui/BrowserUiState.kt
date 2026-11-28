package com.example.ui

import com.example.data.SitePermissionType
import org.mozilla.geckoview.GeckoSession

/** Active modal sheet or dialog destination rendered above the browser canvas. */
enum class BrowserSheet {
  NONE,
  TAB_SWITCHER,
  MEDIA_HUB,
  SECURITY_TELEMETRY,
  LIBRARY_SETTINGS,
  ADDONS,
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

/** Pending Firefox WebExtension installation/update permission prompt. */
data class AddonInstallPromptState(
  val extensionId: String,
  val name: String,
  val version: String?,
  val permissions: List<String>,
  val origins: List<String>,
  val onDecision: (allow: Boolean, allowPrivate: Boolean) -> Unit,
)

/** Active extension popup modal backed by a GeckoSession from ActionDelegate. */
data class AddonPopupState(
  val addonId: String,
  val addonName: String,
  val session: GeckoSession,
)
