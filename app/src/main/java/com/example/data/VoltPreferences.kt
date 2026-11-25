package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.vpn.DohProvider
import com.example.vpn.WireGuardMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.voltDataStore by preferencesDataStore(name = "volt_browser_prefs")

class VoltPreferences(private val context: Context) {

  private object Keys {
    val TURBO_SHIELD = booleanPreferencesKey("turbo_shield_enabled")
    val AD_BLOCKER_TYPE = stringPreferencesKey("ad_blocker_type")
    val LITE_MODE = booleanPreferencesKey("lite_mode_enabled")
    val HTTPS_ONLY = booleanPreferencesKey("https_only_enabled")
    val DARK_WEB = booleanPreferencesKey("dark_web_enabled")
    val DESKTOP_MODE = booleanPreferencesKey("desktop_mode_enabled")
    val AUTO_WIREGUARD_ON_LAUNCH = booleanPreferencesKey("auto_wireguard_on_launch")
    val WG_KILL_SWITCH = booleanPreferencesKey("wg_kill_switch_enabled")
    val PUBLIC_PROXY_WARNED = booleanPreferencesKey("public_proxy_warned")
    val PREFERRED_WG_MODE = stringPreferencesKey("preferred_wg_mode")
    val SAVED_WARP_CONF = stringPreferencesKey("saved_warp_conf")
    val SAVED_CUSTOM_WG_CONF = stringPreferencesKey("saved_custom_wg_conf")
    val SAVED_CUSTOM_WG_NAME = stringPreferencesKey("saved_custom_wg_name")
    val AUTO_PROXY_ON_LAUNCH = booleanPreferencesKey("auto_proxy_on_launch")
    val AUTO_PROXY_FAILOVER = booleanPreferencesKey("auto_proxy_failover")
    val AUTO_DNS_VPN_ON_LAUNCH = booleanPreferencesKey("auto_dns_vpn_on_launch")
    val PREFERRED_DOH_PROVIDER = stringPreferencesKey("preferred_doh_provider")
    val SEARCH_ENGINE = stringPreferencesKey("search_engine")
    val THEME_MODE = stringPreferencesKey("theme_style_mode")
    val TRACKERS_BLOCKED = longPreferencesKey("cumulative_trackers_blocked")
    val BYTES_SAVED = longPreferencesKey("cumulative_bytes_saved")
    val AVG_LOAD_MS = longPreferencesKey("avg_load_time_ms")
    val MEASURED_PAGES = intPreferencesKey("measured_page_count")
    val STARTUP_ALL_OFF_MIGRATED = booleanPreferencesKey("startup_all_vpn_off_v1")
  }

  val settings: Flow<BrowserSettings> =
    context.voltDataStore.data.map { prefs ->
      val engineName = prefs[Keys.SEARCH_ENGINE] ?: SearchEngine.DUCKDUCKGO.name
      val engine =
        SearchEngine.entries.firstOrNull { it.name == engineName } ?: SearchEngine.DUCKDUCKGO
      val themeName = prefs[Keys.THEME_MODE] ?: AppThemeMode.CYBER_OBSIDIAN.name
      val theme =
        AppThemeMode.entries.firstOrNull { it.name == themeName } ?: AppThemeMode.CYBER_OBSIDIAN
      val dohName = prefs[Keys.PREFERRED_DOH_PROVIDER] ?: DohProvider.CLOUDFLARE.name
      val dohProvider =
        DohProvider.entries.firstOrNull { it.name == dohName } ?: DohProvider.CLOUDFLARE
      val wgModeName = prefs[Keys.PREFERRED_WG_MODE] ?: WireGuardMode.WARP_FREE.name
      val wgMode =
        WireGuardMode.entries.firstOrNull { it.name == wgModeName } ?: WireGuardMode.WARP_FREE
      val legacyShield = prefs[Keys.TURBO_SHIELD] ?: true
      val blockerName = prefs[Keys.AD_BLOCKER_TYPE]
      val blockerType =
        if (blockerName != null) {
          AdBlockerType.entries.firstOrNull { it.name == blockerName } ?: AdBlockerType.ADGUARD
        } else {
          if (legacyShield) AdBlockerType.ADGUARD else AdBlockerType.OFF
        }
      val rawWarp = prefs[Keys.SAVED_WARP_CONF].orEmpty()
      val decryptedWarp = SecureConfigCipher.decrypt(rawWarp)
      val warpDecryptFailed = rawWarp.isNotBlank() && decryptedWarp == null

      val rawCustomWg = prefs[Keys.SAVED_CUSTOM_WG_CONF].orEmpty()
      val decryptedCustomWg = SecureConfigCipher.decrypt(rawCustomWg)
      val customWgDecryptFailed = rawCustomWg.isNotBlank() && decryptedCustomWg == null

      BrowserSettings(
        turboShieldEnabled = blockerType != AdBlockerType.OFF,
        adBlockerType = blockerType,
        liteModeEnabled = prefs[Keys.LITE_MODE] ?: false,
        httpsOnlyEnabled = prefs[Keys.HTTPS_ONLY] ?: false,
        darkWebEnabled = prefs[Keys.DARK_WEB] ?: true,
        desktopModeEnabled = prefs[Keys.DESKTOP_MODE] ?: false,
        autoWireGuardOnLaunchEnabled = prefs[Keys.AUTO_WIREGUARD_ON_LAUNCH] ?: false,
        wireGuardKillSwitchEnabled = prefs[Keys.WG_KILL_SWITCH] ?: false,
        hasSeenPublicProxyWarning = prefs[Keys.PUBLIC_PROXY_WARNED] ?: false,
        preferredWireGuardMode = wgMode,
        savedWarpConf = decryptedWarp.orEmpty(),
        savedCustomWgConf = decryptedCustomWg.orEmpty(),
        savedCustomWgName = prefs[Keys.SAVED_CUSTOM_WG_NAME].orEmpty(),
        warpConfigDecryptFailed = warpDecryptFailed,
        customWgConfigDecryptFailed = customWgDecryptFailed,
        autoProxyOnLaunchEnabled = prefs[Keys.AUTO_PROXY_ON_LAUNCH] ?: false,
        autoProxyFailoverEnabled = prefs[Keys.AUTO_PROXY_FAILOVER] ?: true,
        autoDnsVpnOnLaunchEnabled = prefs[Keys.AUTO_DNS_VPN_ON_LAUNCH] ?: false,
        preferredDohProvider = dohProvider,
        searchEngine = engine,
        themeMode = theme,
        cumulativeTrackersBlocked = prefs[Keys.TRACKERS_BLOCKED] ?: 0L,
        cumulativeBytesSaved = prefs[Keys.BYTES_SAVED] ?: 0L,
        avgLoadTimeMs = prefs[Keys.AVG_LOAD_MS] ?: 0L,
        measuredPageCount = prefs[Keys.MEASURED_PAGES] ?: 0,
      )
    }

  suspend fun setAdBlockerType(type: AdBlockerType) {
    context.voltDataStore.edit {
      it[Keys.AD_BLOCKER_TYPE] = type.name
      it[Keys.TURBO_SHIELD] = type != AdBlockerType.OFF
    }
  }

  suspend fun setTurboShield(enabled: Boolean) {
    setAdBlockerType(if (enabled) AdBlockerType.ADGUARD else AdBlockerType.OFF)
  }

  suspend fun setLiteMode(enabled: Boolean) {
    context.voltDataStore.edit { it[Keys.LITE_MODE] = enabled }
  }

  suspend fun setHttpsOnly(enabled: Boolean) {
    context.voltDataStore.edit { it[Keys.HTTPS_ONLY] = enabled }
  }

  suspend fun setDarkWeb(enabled: Boolean) {
    context.voltDataStore.edit { it[Keys.DARK_WEB] = enabled }
  }

  suspend fun setDesktopMode(enabled: Boolean) {
    context.voltDataStore.edit { it[Keys.DESKTOP_MODE] = enabled }
  }

  suspend fun setAutoWireGuardOnLaunch(enabled: Boolean) {
    context.voltDataStore.edit { it[Keys.AUTO_WIREGUARD_ON_LAUNCH] = enabled }
  }

  suspend fun setWireGuardKillSwitch(enabled: Boolean) {
    context.voltDataStore.edit { it[Keys.WG_KILL_SWITCH] = enabled }
  }

  suspend fun setPublicProxyWarned(warned: Boolean) {
    context.voltDataStore.edit { it[Keys.PUBLIC_PROXY_WARNED] = warned }
  }

  suspend fun setPreferredWireGuardMode(mode: WireGuardMode) {
    context.voltDataStore.edit { it[Keys.PREFERRED_WG_MODE] = mode.name }
  }

  suspend fun saveWarpConfig(confText: String) {
    val encrypted = SecureConfigCipher.encrypt(confText)
    context.voltDataStore.edit {
      it[Keys.SAVED_WARP_CONF] = encrypted
      it[Keys.PREFERRED_WG_MODE] = WireGuardMode.WARP_FREE.name
    }
  }

  suspend fun saveCustomWireGuardConfig(confText: String, profileName: String) {
    val encrypted = SecureConfigCipher.encrypt(confText)
    context.voltDataStore.edit {
      it[Keys.SAVED_CUSTOM_WG_CONF] = encrypted
      it[Keys.SAVED_CUSTOM_WG_NAME] = profileName
      it[Keys.PREFERRED_WG_MODE] = WireGuardMode.CUSTOM_CONF.name
    }
  }

  suspend fun setAutoProxyOnLaunch(enabled: Boolean) {
    context.voltDataStore.edit { it[Keys.AUTO_PROXY_ON_LAUNCH] = enabled }
  }

  suspend fun setAutoProxyFailover(enabled: Boolean) {
    context.voltDataStore.edit { it[Keys.AUTO_PROXY_FAILOVER] = enabled }
  }

  suspend fun setAutoDnsVpnOnLaunch(enabled: Boolean) {
    context.voltDataStore.edit { it[Keys.AUTO_DNS_VPN_ON_LAUNCH] = enabled }
  }

  suspend fun setPreferredDohProvider(provider: DohProvider) {
    context.voltDataStore.edit { it[Keys.PREFERRED_DOH_PROVIDER] = provider.name }
  }

  suspend fun setSearchEngine(engine: SearchEngine) {
    context.voltDataStore.edit { it[Keys.SEARCH_ENGINE] = engine.name }
  }

  suspend fun setThemeMode(mode: AppThemeMode) {
    context.voltDataStore.edit { it[Keys.THEME_MODE] = mode.name }
  }

  suspend fun recordTelemetry(trackersBlocked: Int, bytesSaved: Long, loadTimeMs: Long) {
    context.voltDataStore.edit { prefs ->
      if (trackersBlocked > 0) {
        prefs[Keys.TRACKERS_BLOCKED] = (prefs[Keys.TRACKERS_BLOCKED] ?: 0L) + trackersBlocked
      }
      if (bytesSaved > 0L) {
        prefs[Keys.BYTES_SAVED] = (prefs[Keys.BYTES_SAVED] ?: 0L) + bytesSaved
      }
      if (loadTimeMs in 15L..30_000L) {
        val count = prefs[Keys.MEASURED_PAGES] ?: 0
        val currentAvg = prefs[Keys.AVG_LOAD_MS] ?: 0L
        val newCount = count + 1
        val newAvg = ((currentAvg * count) + loadTimeMs) / newCount
        prefs[Keys.MEASURED_PAGES] = newCount
        prefs[Keys.AVG_LOAD_MS] = newAvg
      }
    }
  }

  suspend fun ensureStartupVpnOffDefault() {
    context.voltDataStore.edit { prefs ->
      if (prefs[Keys.STARTUP_ALL_OFF_MIGRATED] != true) {
        prefs[Keys.AUTO_WIREGUARD_ON_LAUNCH] = false
        prefs[Keys.AUTO_PROXY_ON_LAUNCH] = false
        prefs[Keys.AUTO_DNS_VPN_ON_LAUNCH] = false
        prefs[Keys.STARTUP_ALL_OFF_MIGRATED] = true
      }
    }
  }

  suspend fun clearAll() {
    context.voltDataStore.edit { it.clear() }
  }
}
